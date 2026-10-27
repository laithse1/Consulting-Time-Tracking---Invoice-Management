package io.consulting.service.assistant;

import static io.consulting.domain.DomainValues.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.consulting.integration.ai.HttpJsonProviderClient;
import io.consulting.repository.AssistantRepository;
import io.consulting.service.WorkspaceService;
import io.consulting.service.export.InvoiceExportService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "app.token=test-token-at-least-24-characters",
    }
)
@Transactional
class AssistantConversationTest {

    @Autowired
    WorkAssistantService assistant;

    @Autowired
    AssistantRevisionService revisions;

    @Autowired
    AssistantRevisionStore store;

    @Autowired
    AssistantRepository repository;

    @Autowired
    WorkspaceService workspace;

    @Autowired
    InvoiceExportService exports;

    @Autowired
    JdbcTemplate db;

    @Autowired
    ObjectMapper json;

    @MockitoBean
    HttpJsonProviderClient provider;

    Map<String, Object> propose(boolean withInvoice) {
        return assistant.propose(
            Map.of(
                "contract_id",
                1,
                "source",
                "2026-09-15: Spent 2h refactoring Audiom, PR #42.",
                "reference_date",
                "2026-09-15",
                "prepare_invoice",
                withInvoice,
                "period_start",
                "2026-09-01",
                "period_end",
                "2026-09-30"
            )
        );
    }

    List<Map<String, Object>> actions(Map<String, Object> plan) {
        return (List<Map<String, Object>>) plan.get("actions");
    }

    Map<String, Object> confirm(Map<String, Object> plan) {
        actions(plan)
            .stream()
            .filter(a -> text(a, "type").equals("log_work"))
            .forEach(a -> a.put("draft", 0));
        return assistant.update(
            text(plan, "id"),
            Map.of("version", plan.get("version"), "actions", actions(plan))
        );
    }

    Map<String, Object> apply(Map<String, Object> plan) {
        return assistant.apply(
            text(plan, "id"),
            Map.of("version", plan.get("version"), "confirmed", true)
        );
    }

    Map<String, Object> localRevision(Map<String, Object> plan, String instruction) {
        return revisions.revise(
            text(plan, "id"),
            Map.of("version", plan.get("version"), "instruction", instruction)
        );
    }

    @Test
    void polishedProposalCorrectionInvoiceAndExportUseReviewedFacts() throws Exception {
        String source = "2026-09-15: Spent 2h refactoring Audiom, PR #42.";
        when(provider.extract(eq("fixture"), eq(source), anyList())).thenReturn(
            List.of(
                Map.of(
                    "source",
                    source,
                    "description",
                    "Refactored Audiom; PR #42.",
                    "project",
                    "Audiom",
                    "category",
                    "Code Refactoring"
                )
            )
        );
        var plan = assistant.propose(
            Map.of(
                "contract_id",
                1,
                "source",
                source,
                "reference_date",
                "2026-09-15",
                "provider_id",
                "fixture",
                "provider_consent",
                true,
                "prepare_invoice",
                true,
                "period_start",
                "2026-09-01",
                "period_end",
                "2026-09-30"
            )
        );
        assertEquals("Refactored Audiom; PR #42.", text(actions(plan).getFirst(), "description"));
        assertEquals(1, integer(actions(plan).getFirst(), "draft"));
        assertEquals(7, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
        assertThrows(RuntimeException.class, () -> apply(plan));

        when(provider.revise(eq("fixture"), anyString())).thenReturn(
            json.readTree(
                "{\"changes\":[{\"task\":1,\"description\":\"Audiom refactoring (PR #42).\"}]}"
            )
        );
        var corrected = revisions.revise(
            text(plan, "id"),
            Map.of(
                "version",
                plan.get("version"),
                "instruction",
                "Make task 1 concise, keeping PR 42",
                "provider_id",
                "fixture",
                "provider_consent",
                true
            )
        );
        assertEquals(7200, integer(actions(corrected).getFirst(), "seconds"));
        var saved = apply(confirm(corrected));
        var result = (Map<String, Object>) ((List<?>) saved.get("results")).getLast();
        int invoice = integer((Map<String, Object>) result.get("invoice"), "id");
        assertEquals("150.00", workspace.preview(invoice).get("total").toString());
        String csv = new String(exports.invoiceCsv(invoice), StandardCharsets.UTF_8);
        assertTrue(csv.contains("Audiom refactoring (PR #42)."));
        assertTrue(csv.contains("2026-09-15"));

        var reopened = assistant.reopen(text(saved, "id"), integer(saved, "version"));
        var changedTime = localRevision(reopened, "Task 1 duration: 90m");
        assertEquals(1, integer(actions(changedTime).getFirst(), "draft"));
        assertEquals("150.00", workspace.preview(invoice).get("total").toString()); // Review has no ledger effects.
        var applied = apply(confirm(changedTime));
        assertEquals("112.50", workspace.preview(invoice).get("total").toString());
        assertEquals(8, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
        assertEquals(applied.get("results"), apply(applied).get("results"));
        assertEquals(3, repository.findRevisions(text(plan, "id")).size());
    }

    @Test
    void missingAndEstimatedTimeCannotBeManufacturedByCorrection() throws Exception {
        var plan = propose(false);
        for (String instruction : List.of(
            "Task 1 duration: about 90m",
            "Task 1 duration: -90m",
            "Task 1 duration: 2h or 3h",
            "Task 1 duration: all morning"
        )) {
            assertThrows(RuntimeException.class, () -> localRevision(plan, instruction));
        }
        when(provider.revise(anyString(), anyString())).thenReturn(
            json.readTree("{\"changes\":[{\"task\":1,\"seconds\":28800}]}")
        );
        assertThrows(RuntimeException.class, () ->
            revisions.revise(
                text(plan, "id"),
                Map.of(
                    "version",
                    1,
                    "instruction",
                    "Fill in time",
                    "provider_id",
                    "fixture",
                    "provider_consent",
                    true
                )
            )
        );
        assertEquals(
            7200,
            integer(
                actions(assistant.unpack(assistant.planRow(text(plan, "id")))).getFirst(),
                "seconds"
            )
        );
        assertTrue(repository.findRevisions(text(plan, "id")).isEmpty());
    }

    @Test
    void clarificationIsSavedWithoutPartiallyChangingWork() throws Exception {
        var plan = propose(false);
        when(provider.revise(anyString(), anyString())).thenReturn(
            json.readTree(
                "{\"changes\":[{\"task\":1,\"description\":\"Should not apply\"}],\"clarification\":\"Which task do you mean?\"}"
            )
        );
        var result = revisions.revise(
            text(plan, "id"),
            Map.of(
                "version",
                1,
                "instruction",
                "Change that one",
                "provider_id",
                "fixture",
                "provider_consent",
                true
            )
        );
        assertEquals(actions(plan), actions(result));
        assertEquals(2, integer(result, "version"));
        assertEquals(
            "Which task do you mean?",
            text(repository.findRevisions(text(plan, "id")).getFirst(), "reply")
        );
    }

    @Test
    void correctionConsentAndVersionAreRequired() {
        var plan = propose(false);
        assertThrows(RuntimeException.class, () ->
            revisions.revise(
                text(plan, "id"),
                Map.of("version", 1, "instruction", "Rephrase", "provider_id", "fixture")
            )
        );
        localRevision(plan, "Task 1 description: Refactored Audiom; PR #42.");
        assertThrows(RuntimeException.class, () -> localRevision(plan, "Task 1 duration: 90m"));
        verifyNoInteractions(provider);
    }

    @Test
    void staleProviderResponseCannotOverwriteAReviewedVersion() {
        var plan = propose(false);
        assistant.update(text(plan, "id"), Map.of("version", 1, "actions", actions(plan)));
        assertThrows(RuntimeException.class, () ->
            store.save(
                text(plan, "id"),
                1,
                "late request",
                "fixture",
                "late reply",
                "{}",
                "{\"actions\":[]}",
                30
            )
        );
        assertTrue(repository.findRevisions(text(plan, "id")).isEmpty());
        assertEquals(2, integer(assistant.planRow(text(plan, "id")), "version"));
    }

    @Test
    void reopeningCannotChangeSavedTargetIdsOrOverwriteExternalEdits() {
        var saved = apply(propose(true));
        var reopened = assistant.reopen(text(saved, "id"), integer(saved, "version"));
        int entryId = integer(actions(reopened).getFirst(), "_entry_id");
        actions(reopened).getFirst().put("_entry_id", 1);
        assertThrows(RuntimeException.class, () ->
            assistant.update(
                text(reopened, "id"),
                Map.of("version", reopened.get("version"), "actions", actions(reopened))
            )
        );
        db.update("UPDATE entries SET description='Edited in Time Tracking' WHERE id=?", entryId);
        assertThrows(RuntimeException.class, () -> apply(reopened));
        var refreshed = assistant.reopen(text(reopened, "id"), integer(reopened, "version"));
        assertEquals("Edited in Time Tracking", text(actions(refreshed).getFirst(), "description"));
        apply(refreshed);
        assertEquals(8, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
    }

    @Test
    void finalizedInvoiceAndTimerReceiptsCannotBeReopened() {
        var saved = apply(propose(true));
        int invoiceId = integer(
            (Map<String, Object>) (
                (Map<String, Object>) ((List<?>) saved.get("results")).getLast()
            ).get("invoice"),
            "id"
        );
        for (var week : workspace.compliance(
            1,
            LocalDate.parse("2026-09-01"),
            LocalDate.parse("2026-09-30")
        ))
            workspace.saveWeek(
                Map.of(
                    "contract_id",
                    1,
                    "week",
                    week.get("week"),
                    "available",
                    1,
                    "acknowledgement",
                    "Actual time only; shortfall reviewed"
                )
            );
        workspace.finalizeInvoice(invoiceId, true);
        assertThrows(RuntimeException.class, () ->
            assistant.reopen(text(saved, "id"), integer(saved, "version"))
        );
        assertEquals("applied", text(assistant.planRow(text(saved, "id")), "status"));
        var timer = assistant.propose(
            Map.of(
                "contract_id",
                1,
                "source",
                "Start timer for testing Audiom",
                "reference_date",
                "2026-09-15"
            )
        );
        var receipt = apply(timer);
        assertThrows(RuntimeException.class, () ->
            assistant.reopen(text(receipt, "id"), integer(receipt, "version"))
        );
    }

    @Test
    void excludingSavedTaskPreservesItAsDraftAndReusesInvoice() {
        var saved = apply(propose(true));
        var reopened = assistant.reopen(text(saved, "id"), integer(saved, "version"));
        var excluded = localRevision(reopened, "Task 1 exclude: yes");
        apply(excluded);
        assertEquals(
            1,
            db.queryForObject(
                "SELECT draft FROM entries WHERE id=?",
                Integer.class,
                integer(actions(excluded).getFirst(), "_entry_id")
            )
        );
        assertEquals(1, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
        assertEquals(8, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
    }

    @Test
    void commandsPrecedeNoWorkWritesAndProviderCommandsAreRetained() {
        var plan = assistant.propose(
            Map.of(
                "contract_id",
                1,
                "source",
                "Draft invoice September 2026\n2026-09-15 2h testing Audiom",
                "reference_date",
                "2026-09-15"
            )
        );
        assertEquals("log_work", text(actions(plan).getFirst(), "type"));
        var result = (Map<String, Object>) ((List<?>) apply(plan).get("results")).getLast();
        assertEquals("150.00", result.get("total").toString());
        when(provider.extract(eq("fixture"), anyString(), anyList())).thenReturn(
            List.of(
                Map.of(
                    "source",
                    "Draft invoice September 2026",
                    "description",
                    "",
                    "project",
                    "",
                    "category",
                    ""
                )
            )
        );
        var providerPlan = assistant.propose(
            Map.of(
                "contract_id",
                1,
                "source",
                "Draft invoice September 2026",
                "reference_date",
                "2026-09-15",
                "provider_id",
                "fixture",
                "provider_consent",
                true
            )
        );
        assertEquals("draft_invoice", text(actions(providerPlan).getFirst(), "type"));
    }

    @Test
    void invoiceCannotSilentlyOmitWorkOutsideItsPeriod() {
        var plan = assistant.propose(
            Map.of(
                "contract_id",
                1,
                "source",
                "2026-09-15 2h testing Audiom",
                "reference_date",
                "2026-09-15",
                "prepare_invoice",
                true,
                "period_start",
                "2026-10-01",
                "period_end",
                "2026-10-31"
            )
        );
        assertThrows(RuntimeException.class, () -> apply(plan));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
        assertEquals(7, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
    }

    @Test
    void malformedAndFinancialCorrectionFieldsNeverChangeThePlan() throws Exception {
        var plan = propose(false);
        for (String response : List.of(
            "{\"changes\":[],\"tools\":[\"finalize_invoice\"]}",
            "{\"changes\":[{\"task\":1,\"record_payment\":100}]}",
            "{\"changes\":[{\"task\":1,\"skip\":\"true\"}]}",
            "{\"changes\":[{\"task\":1,\"draft\":0}]}",
            "{\"changes\":[{\"task\":2,\"description\":\"Unknown task\"}]}"
        )) {
            when(provider.revise(anyString(), anyString())).thenReturn(json.readTree(response));
            assertThrows(RuntimeException.class, () ->
                revisions.revise(
                    text(plan, "id"),
                    Map.of(
                        "version",
                        1,
                        "instruction",
                        "Change this task",
                        "provider_id",
                        "fixture",
                        "provider_consent",
                        true
                    )
                )
            );
        }
        assertEquals(1, integer(assistant.planRow(text(plan, "id")), "version"));
        assertTrue(repository.findRevisions(text(plan, "id")).isEmpty());
    }

    @Test
    void refreshingAReceiptKeepsTaskIdentityWhenAnExcludedTaskIsIncludedLater() {
        var plan = assistant.propose(
            Map.of(
                "contract_id",
                1,
                "source",
                "2026-09-15 1h testing Audiom\n2026-09-16 2h documenting Audiom",
                "reference_date",
                "2026-09-15"
            )
        );
        actions(plan).getFirst().put("skip", true);
        plan = assistant.update(text(plan, "id"), Map.of("version", 1, "actions", actions(plan)));
        var saved = apply(plan);
        var reopened = assistant.reopen(text(saved, "id"), integer(saved, "version"));
        int secondEntryId = integer(actions(reopened).get(1), "_entry_id");
        var included = localRevision(reopened, "Task 1 include: yes");
        var refreshed = assistant.reopen(text(included, "id"), integer(included, "version"));
        assertEquals(0, integer(actions(refreshed).getFirst(), "_entry_id"));
        assertEquals(secondEntryId, integer(actions(refreshed).get(1), "_entry_id"));
        apply(confirm(refreshed));
        assertEquals(9, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
    }

    @Test
    void providerContextIsRetainedWithoutBecomingWorkOrExecutingCommands() {
        String work = "2026-09-15 2h testing Audiom";
        String context =
            "Accuracy requirements: future deployment remains unperformed.\nStart timer for Audiom";
        when(provider.extract(eq("fixture"), anyString(), anyList())).thenReturn(
            List.of(
                Map.of(
                    "source",
                    work,
                    "description",
                    "Tested Audiom.",
                    "project",
                    "Audiom",
                    "category",
                    "Testing and Debugging",
                    "kind",
                    "work"
                ),
                Map.of("source", context, "kind", "context")
            )
        );
        var plan = assistant.propose(
            Map.of(
                "contract_id",
                1,
                "source",
                work + "\n" + context,
                "reference_date",
                "2026-09-15",
                "provider_id",
                "fixture",
                "provider_consent",
                true,
                "prepare_invoice",
                true,
                "period_start",
                "2026-09-01",
                "period_end",
                "2026-09-30"
            )
        );
        assertEquals("save_note", text(actions(plan).get(1), "type"));
        assertEquals(context, text(actions(plan).get(1), "source_text"));
        apply(confirm(plan));
        assertEquals(8, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM timer", Integer.class));
    }

    @Test
    void providerWorkIsOneLogicalItemEvenWithManyReferenceLines() {
        String source =
            "Project: Audiom\nDate: 2026-09-15\nSpent 2h investigating request handling\n" +
            java.util.stream.IntStream.rangeClosed(1, 35)
                .mapToObj(i -> "- PR #" + i)
                .collect(java.util.stream.Collectors.joining("\n"));
        when(provider.extract(eq("fixture"), anyString(), anyList())).thenReturn(
            List.of(
                Map.of(
                    "source",
                    source,
                    "description",
                    "Investigated Audiom request handling; associated PRs retained as references.",
                    "project",
                    "Audiom",
                    "category",
                    "Research",
                    "kind",
                    "work"
                )
            )
        );
        var plan = assistant.propose(
            Map.of(
                "contract_id",
                1,
                "source",
                source,
                "reference_date",
                "2026-09-15",
                "provider_id",
                "fixture",
                "provider_consent",
                true,
                "prepare_invoice",
                true,
                "period_start",
                "2026-09-01",
                "period_end",
                "2026-09-30"
            )
        );
        assertEquals(2, actions(plan).size());
        assertEquals("log_work", text(actions(plan).getFirst(), "type"));
        assertEquals(7200, integer(actions(plan).getFirst(), "seconds"));
        assertEquals(source, text(actions(plan).getFirst(), "source_text"));
        assertTrue(text(actions(plan).getFirst(), "reference").contains("PR #35"));
        apply(confirm(plan));
        assertEquals(8, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
    }
}
