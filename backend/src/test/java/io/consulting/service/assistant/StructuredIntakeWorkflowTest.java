package io.consulting.service.assistant;

import static io.consulting.domain.DomainValues.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "app.token=test-token-at-least-24-characters",
    }
)
@Transactional
class StructuredIntakeWorkflowTest {

    @Autowired
    WorkAssistantService assistant;

    @Autowired
    JdbcTemplate db;

    private Map<String, Object> propose() throws Exception {
        String source;
        try (var input = getClass().getResourceAsStream("/assistant/october-proposed-work.txt")) {
            source = new String(
                Objects.requireNonNull(input).readAllBytes(),
                StandardCharsets.UTF_8
            );
        }
        // A selected provider must not be called for an explicit table: no paid request is needed.
        return assistant.propose(
            Map.of(
                "contract_id",
                1,
                "source",
                source,
                "reference_date",
                "2026-10-25",
                "provider_id",
                "unavailable-fixture",
                "provider_consent",
                true
            )
        );
    }

    @Test
    void exactPromptProducesAnEditablePlanWithAnInitiallyExcludedExactPeriodInvoice()
        throws Exception {
        var plan = propose();
        var actions = (List<Map<String, Object>>) plan.get("actions");
        assertEquals("local-rules", text(plan, "engine"));
        assertEquals(19, actions.size());
        var invoice = actions.getLast();
        assertEquals("draft_invoice", text(invoice, "type"));
        assertEquals("2026-10-30", text(invoice, "period_end"));
        assertEquals(true, invoice.get("skip"));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
        assertEquals(7, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
        var result = assistant.apply(text(plan, "id"), Map.of("version", 1, "confirmed", true));
        assertEquals(18, ((List<?>) result.get("results")).size());
        assertEquals(0, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
        assertEquals(
            0,
            db.queryForObject(
                "SELECT count(*) FROM entries WHERE draft=0 AND work_date LIKE '2026-10%'",
                Integer.class
            )
        );
        assertEquals(
            0,
            db.queryForObject(
                "SELECT count(*) FROM entries WHERE work_date >= '2026-10-26'",
                Integer.class
            )
        );
    }

    @Test
    void unresolvedQuestionsBlockInvoiceAtomicallyEvenIfAllTasksAreExcluded() throws Exception {
        var plan = propose();
        var actions = (List<Map<String, Object>>) plan.get("actions");
        for (var a : actions) if (text(a, "type").equals("log_work")) a.put("skip", true);
        actions.getLast().put("skip", false);
        var saved = assistant.update(text(plan, "id"), Map.of("version", 1, "actions", actions));
        var error = assertThrows(RuntimeException.class, () ->
            assistant.apply(
                text(plan, "id"),
                Map.of("version", integer(saved, "version"), "confirmed", true)
            )
        );
        assertTrue(error.getMessage().contains("billing questions"));
        assertEquals(7, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
    }

    @Test
    void resolutionAndActualConfirmationProduceOnlyReviewedHoursAtConfiguredRate()
        throws Exception {
        var plan = propose();
        plan = assistant.split(text(plan, "id"), integer(plan, "version"), 0);
        var actions = (List<Map<String, Object>>) plan.get("actions");
        for (var a : actions) {
            if (text(a, "type").equals("log_work")) a.put("skip", true);
            if (Boolean.TRUE.equals(a.get("requires_resolution"))) a.put("resolved", true);
        }
        var actual = actions.getFirst();
        actual.putAll(
            Map.of(
                "skip",
                false,
                "work_date",
                "2026-10-01",
                "seconds",
                3600,
                "project_id",
                1,
                "draft",
                0
            )
        );
        actions
            .getLast()
            .putAll(
                Map.of(
                    "skip",
                    false,
                    "work_date",
                    "2026-10-02",
                    "seconds",
                    3600,
                    "project_id",
                    1,
                    "draft",
                    0
                )
            );
        actions
            .stream()
            .filter(a -> text(a, "type").equals("draft_invoice"))
            .findFirst()
            .orElseThrow()
            .put("skip", false);
        var saved = assistant.update(
            text(plan, "id"),
            Map.of("version", integer(plan, "version"), "actions", actions)
        );
        var applied = assistant.apply(
            text(plan, "id"),
            Map.of("version", integer(saved, "version"), "confirmed", true)
        );
        var results = (List<Map<String, Object>>) applied.get("results");
        var invoice = results
            .stream()
            .filter(r -> text(r, "type").equals("draft_invoice"))
            .findFirst()
            .orElseThrow();
        var preview = (Map<String, Object>) invoice.get("preview");
        assertEquals(
            0,
            new java.math.BigDecimal("2").compareTo(
                new java.math.BigDecimal(text(preview, "hours"))
            )
        );
        assertEquals(
            "2026-10-30",
            text((Map<String, Object>) invoice.get("invoice"), "period_end")
        );
        assertEquals(9, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
    }

    @Test
    void reviewMetadataCannotBeRemovedToBypassTheBillingGate() throws Exception {
        var plan = propose();
        var actions = (List<Map<String, Object>>) plan.get("actions");
        actions
            .stream()
            .filter(a -> Boolean.TRUE.equals(a.get("requires_resolution")))
            .findFirst()
            .orElseThrow()
            .remove("requires_resolution");
        assertThrows(RuntimeException.class, () ->
            assistant.update(text(plan, "id"), Map.of("version", 1, "actions", actions))
        );
    }

    @Test
    void dailySplitsKeepBudgetAndReceiptIdentityWithoutInventingHours() throws Exception {
        var plan = propose();
        var split = assistant.split(text(plan, "id"), 1, 0);
        var actions = (List<Map<String, Object>>) split.get("actions");
        assertEquals(20, actions.size());
        assertEquals("5", text(actions.getFirst(), "proposed_hours"));
        assertFalse(actions.getLast().containsKey("proposed_hours"));
        assertEquals(0, integer(actions.getLast(), "seconds"));
        assertEquals("", text(actions.getLast(), "work_date"));
        assertEquals(1, integer(actions.getLast(), "draft"));
        assertEquals(
            text(actions.getFirst(), "source_text"),
            text(actions.getLast(), "source_text")
        );
        assertThrows(RuntimeException.class, () -> assistant.split(text(plan, "id"), 1, 0));
        assertEquals(7, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
    }

    @Test
    void manuallyConfirmedFutureWorkIsRejectedBeforeAnyWrites() throws Exception {
        var plan = propose();
        var actions = (List<Map<String, Object>>) plan.get("actions");
        actions
            .getFirst()
            .putAll(
                Map.of("work_date", "2026-10-26", "seconds", 3600, "project_id", 1, "draft", 0)
            );
        var saved = assistant.update(text(plan, "id"), Map.of("version", 1, "actions", actions));
        var error = assertThrows(RuntimeException.class, () ->
            assistant.apply(
                text(plan, "id"),
                Map.of("version", integer(saved, "version"), "confirmed", true)
            )
        );
        assertTrue(error.getMessage().contains("Future work"));
        assertEquals(7, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
    }
}
