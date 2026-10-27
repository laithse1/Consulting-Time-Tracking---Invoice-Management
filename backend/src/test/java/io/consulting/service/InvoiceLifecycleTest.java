package io.consulting.service;

import static io.consulting.domain.DomainValues.*;
import static org.junit.jupiter.api.Assertions.*;

import io.consulting.service.assistant.WorkAssistantService;
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
class InvoiceLifecycleTest {

    @Autowired
    InvoiceService invoices;

    @Autowired
    InvoiceQueryService queries;

    @Autowired
    WorkspaceService workspace;

    @Autowired
    WorkAssistantService assistant;

    @Autowired
    ClientService clients;

    @Autowired
    ExpenseService expenses;

    @Autowired
    JdbcTemplate db;

    private Map<String, Object> draft() {
        return invoices.draft(
            Map.of("contract_id", 1, "period_start", "2026-10-01", "period_end", "2026-10-30")
        );
    }

    private int id(Map<String, Object> preview) {
        return integer((Map<String, Object>) preview.get("invoice"), "id");
    }

    private Map<String, Object> edits(Map<String, Object> preview) {
        return new HashMap<>(
            Map.of(
                "expected_token",
                text(preview, "edit_token"),
                "period_start",
                "2026-10-02",
                "period_end",
                "2026-10-29",
                "invoice_date",
                "2026-10-25",
                "presentation",
                "grouped",
                "adjustment",
                "0",
                "notes",
                "Review before submission"
            )
        );
    }

    private Map<String, Object> tablePlan() throws Exception {
        try (var input = getClass().getResourceAsStream("/assistant/october-proposed-work.txt")) {
            return assistant.propose(
                Map.of(
                    "contract_id",
                    1,
                    "source",
                    new String(
                        Objects.requireNonNull(input).readAllBytes(),
                        StandardCharsets.UTF_8
                    ),
                    "reference_date",
                    "2026-10-25"
                )
            );
        }
    }

    @Test
    void deletedConversationRetainsPendingInvoiceSafeguardsAndLinkedReview() throws Exception {
        var plan = tablePlan();
        var created = assistant.invoiceDraft(
            text(plan, "id"),
            integer(plan, "version"),
            "2026-10-01",
            "2026-10-30"
        );
        var saved = (Map<String, Object>) created.get("plan");
        var preview = (Map<String, Object>) created.get("preview");
        var invoiceBefore = db.queryForList("SELECT * FROM invoices");
        var entriesBefore = db.queryForList("SELECT * FROM entries");
        assistant.deleteConversation(text(saved, "id"), integer(saved, "version"), true);
        assertTrue(
            ((List<Map<String, Object>>) assistant.history(1).get("plans"))
                .stream()
                .noneMatch(item -> text(item, "id").equals(text(saved, "id")))
        );
        assertFalse(text(assistant.getPlan(text(saved, "id")), "deleted_at").isBlank());
        assertEquals(13, ((List<?>) queries.preview(id(preview)).get("pendingWork")).size());
        assertThrows(RuntimeException.class, () -> invoices.finalizeInvoice(id(preview), true));
        assertEquals(invoiceBefore, db.queryForList("SELECT * FROM invoices"));
        assertEquals(entriesBefore, db.queryForList("SELECT * FROM entries"));
    }

    @Test
    void deletingAppliedConversationPreservesReceiptsNotesAndLedgerAndIsIdempotent() {
        var plan = assistant.propose(
            Map.of(
                "contract_id",
                1,
                "source",
                "2026-10-20: Spent 1h testing Audiom",
                "reference_date",
                "2026-10-25"
            )
        );
        var applied = assistant.apply(
            text(plan, "id"),
            Map.of("version", integer(plan, "version"), "confirmed", true)
        );
        assistant.saveDraft(1, "Unfinished composer notes");
        var entriesBefore = db.queryForList("SELECT * FROM entries");
        var draftsBefore = db.queryForList("SELECT * FROM assistant_drafts");
        var receiptsBefore = db.queryForList(
            "SELECT results_json FROM assistant_plans WHERE id=?",
            text(plan, "id")
        );
        assistant.deleteConversation(text(applied, "id"), integer(applied, "version"), true);
        assistant.deleteConversation(text(applied, "id"), integer(applied, "version"), true);
        assertEquals(entriesBefore, db.queryForList("SELECT * FROM entries"));
        assertEquals(draftsBefore, db.queryForList("SELECT * FROM assistant_drafts"));
        assertEquals(
            receiptsBefore,
            db.queryForList("SELECT results_json FROM assistant_plans WHERE id=?", text(plan, "id"))
        );
        assertEquals(
            1,
            db.queryForObject(
                "SELECT count(*) FROM assistant_conversation_deletions",
                Integer.class
            )
        );
        assertEquals(
            1,
            db.queryForObject(
                "SELECT count(*) FROM audit WHERE action='assistant conversation deleted'",
                Integer.class
            )
        );
    }

    @Test
    void conversationDeletionRequiresConfirmationAndCurrentVersion() {
        var plan = assistant.propose(
            Map.of(
                "contract_id",
                1,
                "source",
                "Today spent 1h testing Audiom",
                "reference_date",
                "2026-10-25"
            )
        );
        String planId = text(plan, "id");
        int version = integer(plan, "version");
        assertThrows(RuntimeException.class, () ->
            assistant.deleteConversation(planId, version, false)
        );
        assertThrows(RuntimeException.class, () ->
            assistant.deleteConversation(planId, version + 1, true)
        );
        assertThrows(RuntimeException.class, () ->
            assistant.deleteConversation("missing", version, true)
        );
        assertEquals(
            0,
            db.queryForObject(
                "SELECT count(*) FROM assistant_conversation_deletions",
                Integer.class
            )
        );
        assistant.deleteConversation(planId, version, true);
        assertThrows(RuntimeException.class, () ->
            assistant.apply(planId, Map.of("version", version, "confirmed", true))
        );
    }

    @Test
    void createRealDraftFromUnappliedWorkWithoutConvertingProposedHoursToCharges()
        throws Exception {
        var plan = tablePlan();
        var created = assistant.invoiceDraft(
            text(plan, "id"),
            integer(plan, "version"),
            "2026-10-01",
            "2026-10-30"
        );
        var preview = (Map<String, Object>) created.get("preview");
        assertEquals(13, ((List<?>) preview.get("pendingWork")).size());
        assertEquals(4, ((List<?>) preview.get("plannedWork")).size());
        assertEquals("0.00", text(preview, "total"));
        assertEquals(7, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
        assertThrows(RuntimeException.class, () -> invoices.finalizeInvoice(id(preview), true));
        var saved = (Map<String, Object>) created.get("plan");
        var repeated = assistant.invoiceDraft(
            text(plan, "id"),
            integer(saved, "version"),
            "2026-10-01",
            "2026-10-30"
        );
        assertEquals(id(preview), id((Map<String, Object>) repeated.get("preview")));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
    }

    @Test
    void editsAreVersionedAndKeepInvoiceNumberWhileSynchronizingLinkedPeriods() throws Exception {
        var plan = tablePlan();
        var created = assistant.invoiceDraft(text(plan, "id"), 1, "2026-10-01", "2026-10-30");
        var preview = (Map<String, Object>) created.get("preview");
        var changed = invoices.editDraft(id(preview), edits(preview));
        assertEquals(
            text((Map<String, Object>) preview.get("invoice"), "number"),
            text((Map<String, Object>) changed.get("invoice"), "number")
        );
        assertEquals(
            "2026-10-29",
            text((Map<String, Object>) changed.get("invoice"), "period_end")
        );
        assertThrows(RuntimeException.class, () -> invoices.editDraft(id(preview), edits(preview)));
        assertThrows(RuntimeException.class, () ->
            invoices.deleteDraft(id(preview), text(preview, "edit_token"), true)
        );
        var current = ((List<Map<String, Object>>) assistant.history(1).get("plans")).getFirst();
        var action = ((List<Map<String, Object>>) current.get("actions"))
            .stream()
            .filter(a -> text(a, "type").equals("draft_invoice"))
            .findFirst()
            .orElseThrow();
        assertEquals("2026-10-29", text(action, "period_end"));
    }

    @Test
    void invoiceCanBePreparedEarlyThenAppliedWithoutCreatingADuplicate() throws Exception {
        var plan = tablePlan();
        var created = assistant.invoiceDraft(text(plan, "id"), 1, "2026-10-01", "2026-10-30");
        var current = (Map<String, Object>) created.get("plan");
        var applied = assistant.apply(
            text(plan, "id"),
            Map.of("version", integer(current, "version"), "confirmed", true)
        );
        assertEquals("applied", text(applied, "status"));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
        assertTrue(
            ((List<Map<String, Object>>) applied.get("results"))
                .stream()
                .anyMatch(result -> text(result, "type").equals("draft_invoice"))
        );
        var preview = queries.preview(id((Map<String, Object>) created.get("preview")));
        assertEquals(13, ((List<?>) preview.get("pendingWork")).size());
    }

    @Test
    void deletingDraftKeepsWorkAuditAndNumberAndReleasesTheProposedPlanBinding() throws Exception {
        var plan = tablePlan();
        var created = assistant.invoiceDraft(text(plan, "id"), 1, "2026-10-01", "2026-10-30");
        var preview = (Map<String, Object>) created.get("preview");
        invoices.deleteDraft(id(preview), text(preview, "edit_token"), true);
        assertTrue(((List<?>) workspace.state().get("invoices")).isEmpty());
        assertEquals(7, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
        assertEquals(
            "Deleted",
            db.queryForObject("SELECT state FROM invoices WHERE id=?", String.class, id(preview))
        );
        assertTrue(
            db.queryForObject(
                "SELECT count(*) FROM audit WHERE action='invoice draft deleted'",
                Integer.class
            ) > 0
        );
        assertThrows(RuntimeException.class, () -> queries.preview(id(preview)));
        assertTrue(text((Map<String, Object>) draft().get("invoice"), "number").endsWith("002"));
        var current = ((List<Map<String, Object>>) assistant.history(1).get("plans")).getFirst();
        assertTrue(
            ((List<Map<String, Object>>) current.get("actions"))
                .stream()
                .noneMatch(a -> integer(a, "_invoice_id") > 0)
        );
    }

    @Test
    void finalizedInvoicesCannotBeEditedOrDeletedAndAdjustmentsNeedApproval() {
        var preview = draft();
        var edits = edits(preview);
        edits.put("adjustment", "10");
        assertThrows(RuntimeException.class, () -> invoices.editDraft(id(preview), edits));
        db.update(
            "UPDATE invoices SET state='Ready for Review',snapshot='{}' WHERE id=?",
            id(preview)
        );
        assertThrows(RuntimeException.class, () -> invoices.editDraft(id(preview), edits(preview)));
        assertThrows(RuntimeException.class, () ->
            invoices.deleteDraft(id(preview), text(preview, "edit_token"), true)
        );
    }

    @Test
    void expenseDeletionIsAuditedAndAllocatedExpensesAreProtected() {
        var expense = (Map<String, Object>) expenses.expense(
            Map.of(
                "contract_id",
                1,
                "expense_date",
                "2026-10-01",
                "description",
                "Test expense",
                "amount",
                "5",
                "approval_ref",
                "Approved"
            )
        );
        int id = integer(expense, "id");
        int invoice = id(draft());
        db.update("INSERT INTO expense_allocations VALUES(?,?)", id, invoice);
        assertThrows(RuntimeException.class, () -> expenses.delete(id, true));
        db.update("DELETE FROM expense_allocations WHERE expense_id=?", id);
        assertThrows(RuntimeException.class, () -> expenses.delete(id, false));
        expenses.delete(id, true);
        assertEquals(
            0,
            db.queryForObject("SELECT count(*) FROM expenses WHERE id=?", Integer.class, id)
        );
    }

    @Test
    void archiveRestorePreservesHistoryAndInheritedStateBlocksNewWork() {
        clients.archive("clients", 1, true, true);
        assertTrue(clients.archived("projects", 1));
        assertTrue(clients.archived("contracts", 1));
        assertThrows(RuntimeException.class, () ->
            workspace.saveEntry(Map.of("contract_id", 1, "description", "new work"))
        );
        assertEquals(7, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
        clients.archive("projects", 1, true, true);
        clients.archive("clients", 1, false, true);
        assertFalse(clients.archived("contracts", 1));
        assertTrue(clients.archived("projects", 1));
        clients.archive("projects", 1, false, true);
        assertFalse(clients.archived("projects", 1));
        clients.archive("contracts", 1, true, true);
        assertTrue(clients.archived("contracts", 1));
        clients.archive("contracts", 1, false, true);
        assertFalse(clients.archived("contracts", 1));
        assertThrows(RuntimeException.class, () -> clients.archive("invoices", 1, true, true));
    }

    @Test
    void activeTimerMustBeStoppedBeforeArchivingItsOwner() {
        workspace.timer(
            "start",
            Map.of(
                "contract_id",
                1,
                "project_id",
                1,
                "work_date",
                "2026-10-25",
                "category",
                "Testing and Debugging",
                "description",
                "Timer fixture"
            )
        );
        for (String type : List.of("clients", "contracts", "projects"))
            assertThrows(RuntimeException.class, () -> clients.archive(type, 1, true, true));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM record_archives", Integer.class));
    }

    @Test
    void createsInvoiceFromAppliedWorkWithoutReplayingEntriesAndKeepsReceiptReopenable()
        throws Exception {
        var plan = tablePlan();
        var applied = assistant.apply(
            text(plan, "id"),
            Map.of("version", integer(plan, "version"), "confirmed", true)
        );
        var entries = db.queryForList("SELECT * FROM entries ORDER BY id");
        var created = assistant.invoiceDraft(
            text(plan, "id"),
            integer(applied, "version"),
            "2026-10-01",
            "2026-10-30"
        );
        var current = (Map<String, Object>) created.get("plan");
        assertEquals("applied", text(current, "status"));
        assertEquals(entries, db.queryForList("SELECT * FROM entries ORDER BY id"));
        assertEquals(
            13,
            ((List<?>) ((Map<?, ?>) created.get("preview")).get("pendingWork")).size()
        );
        var reopened = assistant.reopen(text(plan, "id"), integer(current, "version"));
        assistant.apply(
            text(plan, "id"),
            Map.of("version", integer(reopened, "version"), "confirmed", true)
        );
        assertEquals(entries, db.queryForList("SELECT * FROM entries ORDER BY id"));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
    }

    @Test
    void appliedLegacyInvoiceReceiptIsReusedInsteadOfCreatingAnotherInvoice() {
        var plan = assistant.propose(
            Map.of(
                "contract_id",
                1,
                "source",
                "2026-10-20 1h testing Audiom\nDraft invoice October 2026",
                "reference_date",
                "2026-10-25"
            )
        );
        var applied = assistant.apply(
            text(plan, "id"),
            Map.of("version", integer(plan, "version"), "confirmed", true)
        );
        int oldId = db.queryForObject("SELECT id FROM invoices", Integer.class);
        var opened = assistant.invoiceDraft(
            text(plan, "id"),
            integer(applied, "version"),
            "2026-10-01",
            "2026-10-30"
        );
        assertEquals(oldId, id((Map<String, Object>) opened.get("preview")));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
        assertEquals(8, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
    }
}
