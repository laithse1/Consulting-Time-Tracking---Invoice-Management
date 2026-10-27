package io.consulting.service.assistant;

import static io.consulting.domain.DomainValues.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "app.token=test-token-at-least-24-characters",
        "app.assistant.max-actions=3",
    }
)
@Transactional
class NarrativeWorkAssistantTest {

    @Autowired
    WorkAssistantService assistant;

    @Autowired
    JdbcTemplate db;

    private Map<String, Object> propose(String source, String mode) {
        return assistant.propose(
            Map.of(
                "contract_id",
                1,
                "source",
                source,
                "reference_date",
                "2026-09-17",
                "input_mode",
                mode,
                "prepare_invoice",
                true,
                "period_start",
                "2026-09-01",
                "period_end",
                "2026-09-30"
            )
        );
    }

    @Test
    void automaticModeAccountsForTheExtraInvoiceSlotWithoutDroppingWork() {
        String source =
            "2026-09-15 1h testing Audiom\n2026-09-16 1h documenting Audiom\n2026-09-17 1h research Audiom";
        var plan = propose(source, "auto");
        var actions = (List<Map<String, Object>>) plan.get("actions");
        assertEquals(1, actions.size());
        assertEquals("save_note", text(actions.getFirst(), "type"));
        assertEquals(source, text(actions.getFirst(), "source_text"));
        var saved = assistant.apply(text(plan, "id"), Map.of("version", 1, "confirmed", true));
        assertTrue(
            ((List<Map<String, Object>>) saved.get("results")).getFirst().containsKey("note")
        );
        assertEquals(7, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM timer", Integer.class));
    }

    @Test
    void strictModeCountsInvoiceAndRetainsSavedNotesOnFailure() {
        String source =
            "Today 1h testing Audiom\nToday 1h documenting Audiom\nToday 1h research Audiom";
        assistant.saveDraft(1, source);
        assertThrows(RuntimeException.class, () -> propose(source, "lines"));
        assertEquals(
            source,
            db.queryForObject(
                "SELECT source FROM assistant_drafts WHERE contract_id=1",
                String.class
            )
        );
        assertEquals(0, db.queryForObject("SELECT count(*) FROM assistant_plans", Integer.class));
    }

    @Test
    void supportedTaskLinesStillGenerateTheirReviewedInvoiceAtTheLimit() {
        var plan = propose(
            "2026-09-15 1h testing Audiom\n2026-09-16 1h documenting Audiom",
            "auto"
        );
        assertEquals(3, ((List<?>) plan.get("actions")).size());
        assistant.apply(text(plan, "id"), Map.of("version", 1, "confirmed", true));
        assertEquals(9, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
    }

    @Test
    void noteActionCannotBeTurnedIntoBillableWorkByChangingItsType() {
        var plan = propose("Today 2h testing Audiom\nDo not bill future deployment", "narrative");
        var actions = (List<Map<String, Object>>) plan.get("actions");
        actions.getFirst().put("type", "log_work");
        assertThrows(RuntimeException.class, () ->
            assistant.update(text(plan, "id"), Map.of("version", 1, "actions", actions))
        );
        assertEquals(7, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
    }
}
