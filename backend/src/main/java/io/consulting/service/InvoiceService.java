package io.consulting.service;

import static io.consulting.domain.DomainValues.integer;
import static io.consulting.domain.DomainValues.money;
import static io.consulting.domain.DomainValues.require;
import static io.consulting.domain.DomainValues.text;

import io.consulting.repository.InvoiceRepository;
import io.consulting.repository.RecordRepository;
import io.consulting.support.JsonDocuments;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private final io.consulting.config.ApplicationProperties settings;
    private final InvoiceRepository repository;
    private final RecordRepository records;
    private final ClientService clients;
    private final AuditService audit;
    private final JsonDocuments documents;
    private final InvoiceQueryService queries;

    public InvoiceService(
        InvoiceRepository repository,
        RecordRepository records,
        ClientService clients,
        AuditService audit,
        JsonDocuments documents,
        InvoiceQueryService queries,
        io.consulting.config.ApplicationProperties settings
    ) {
        this.repository = repository;
        this.records = records;
        this.clients = clients;
        this.audit = audit;
        this.documents = documents;
        this.queries = queries;
        this.settings = settings;
    }

    @Transactional
    public Map<String, Object> draft(Map<String, Object> command) {
        int contractId = integer(command, "contract_id");
        clients.contract(contractId);
        var from = LocalDate.parse(text(command, "period_start"));
        var to = LocalDate.parse(text(command, "period_end"));
        require(
            !to.isBefore(from) && to.toEpochDay() - from.toEpochDay() <= 366,
            "Invalid service period"
        );
        String prefix =
            settings.invoice().numberPrefix() + "-" + from.toString().substring(0, 7) + "-";
        int sequence = repository.nextSequence(prefix + "%");
        var adjustment = money(command.getOrDefault("adjustment", "0"));
        require(adjustment.signum() >= 0, "Adjustment cannot be negative");
        require(
            adjustment.signum() == 0 ||
                (!text(command, "adjustment_reason").isEmpty() &&
                    !text(command, "adjustment_approval").isEmpty()),
            "Adjustment needs documented reason and explicit manual approval reference"
        );
        int replacement = integer(command, "replacement_for");
        if (replacement > 0) {
            require(!text(command, "revision_reason").isEmpty(), "Replacement reason required");
            var old = records.findById("invoices", replacement);
            require(
                integer(old, "contract_id") == contractId &&
                    !text(old, "snapshot").isEmpty() &&
                    text(old, "state").equals("Withdrawn"),
                "Invalid replacement"
            );
            require(
                repository.countPayments(replacement) == 0,
                "Paid invoices require an accounting credit/refund process; replacement is blocked"
            );
        }
        int id = repository.createInvoice(
            prefix + String.format("%03d", sequence),
            contractId,
            from.toString(),
            to.toString(),
            LocalDate.now().toString(),
            adjustment.toPlainString(),
            text(command, "adjustment_reason"),
            text(command, "adjustment_approval"),
            text(command, "presentation").equals("grouped") ? "grouped" : "itemized",
            replacement == 0 ? null : replacement,
            text(command, "revision_reason")
        );
        audit.record("draft created", "invoice:" + id, command);
        return queries.preview(id);
    }

    @Transactional
    public Map<String, Object> finalizeInvoice(int id, boolean confirmed) {
        require(confirmed, "Explicit confirmation required");
        var invoice = records.findById("invoices", id);
        require(text(invoice, "snapshot").isEmpty(), "Invoice already finalized");
        var preview = queries.preview(id);
        require(
            ((List<?>) preview.get("warnings")).isEmpty(),
            "Resolve validation warnings before finalization"
        );
        int replacement = integer(invoice, "replacement_for");
        if (replacement > 0) {
            var old = records.findById("invoices", replacement);
            require(
                text(old, "state").equals("Withdrawn") &&
                    repository.countPayments(replacement) == 0,
                "Replacement source changed"
            );
            repository.releaseWork(replacement);
            repository.releaseExpenses(replacement);
            repository.markReplaced(replacement);
            audit.record(
                "invoice replaced",
                "invoice:" + replacement,
                text(invoice, "revision_reason")
            );
        }
        for (var entry : (List<Map<String, Object>>) preview.get("entries"))
            repository.allocateWork(integer(entry, "id"), id);
        for (var entry : (List<Map<String, Object>>) preview.get("expenses"))
            repository.allocateExpense(integer(entry, "id"), id);
        repository.freezeSnapshot(documents.encode(preview), preview.get("total").toString(), id);
        audit.record("invoice finalized", "invoice:" + id, preview);
        return queries.preview(id);
    }

    @Transactional
    public Map<String, Object> editDraft(int id, Map<String, Object> command) {
        var invoice = editableDraft(id, text(command, "expected_token"));
        var from = LocalDate.parse(text(command, "period_start"));
        var to = LocalDate.parse(text(command, "period_end"));
        require(
            !to.isBefore(from) && to.toEpochDay() - from.toEpochDay() <= 366,
            "Invalid service period"
        );
        String date = LocalDate.parse(text(command, "invoice_date")).toString();
        String presentation = text(command, "presentation");
        require(
            Set.of("itemized", "grouped").contains(presentation),
            "Choose itemized or grouped presentation"
        );
        var adjustment = money(command.getOrDefault("adjustment", "0"));
        require(adjustment.signum() >= 0, "Adjustment cannot be negative");
        require(
            adjustment.signum() == 0 ||
                (!text(command, "adjustment_reason").isBlank() &&
                    !text(command, "adjustment_approval").isBlank()),
            "Adjustment needs documented reason and explicit manual approval reference"
        );
        audit.record("invoice draft before edit", "invoice:" + id, invoice);
        repository.editDraft(
            id,
            from.toString(),
            to.toString(),
            date,
            presentation,
            adjustment.toPlainString(),
            text(command, "adjustment_reason"),
            text(command, "adjustment_approval"),
            text(command, "notes")
        );
        synchronizePlans(id, false, from.toString(), to.toString());
        audit.record("invoice draft edited", "invoice:" + id, command);
        return queries.preview(id);
    }

    @Transactional
    public void deleteDraft(int id, String expectedToken, boolean confirmed) {
        require(confirmed, "Confirm draft invoice deletion");
        var invoice = editableDraft(id, expectedToken);
        audit.record("invoice draft deleted", "invoice:" + id, invoice);
        repository.deleteDraft(id);
        synchronizePlans(id, true, "", "");
    }

    private Map<String, Object> editableDraft(int id, String expectedToken) {
        var invoice = records.findById("invoices", id);
        require(
            text(invoice, "state").equals("Draft") &&
                text(invoice, "snapshot").isBlank() &&
                repository.countPayments(id) == 0,
            "Only an unfinalized draft can be edited or deleted. Use withdrawal/replacement for an issued unpaid invoice."
        );
        require(
            !expectedToken.isBlank() && documents.fingerprint(invoice).equals(expectedToken),
            "Invoice changed; reload it before editing or deleting"
        );
        return invoice;
    }

    private void synchronizePlans(int invoiceId, boolean deleted, String from, String to) {
        for (var row : repository.linkedPlans(invoiceId)) {
            var document = documents.decode(text(row, "plan_json"));
            for (var action : (List<Map<String, Object>>) document.get("actions")) {
                if (integer(action, "_invoice_id") != invoiceId) continue;
                if (deleted) {
                    action.remove("_invoice_id");
                    action.put("skip", true);
                } else {
                    action.put("period_start", from);
                    action.put("period_end", to);
                }
            }
            repository.synchronizePlan(
                text(row, "id"),
                integer(row, "version"),
                documents.encode(document)
            );
        }
    }

    @Transactional
    public Object transition(int id, Map<String, Object> command) {
        var invoice = records.findById("invoices", id);
        String target = text(command, "state");
        require(
            !text(invoice, "snapshot").isEmpty() &&
                !Set.of("Replaced", "Withdrawn").contains(text(invoice, "state")),
            "Finalize the invoice first"
        );
        switch (target) {
            case "Sent" -> {
                require(
                    text(invoice, "submitted").isEmpty() &&
                        text(invoice, "state").equals("Ready for Review"),
                    "Already submitted"
                );
                var date = LocalDate.parse(text(command, "submitted"));
                require(!date.isAfter(LocalDate.now()), "Submission date cannot be in the future");
                repository.markSubmitted(date.toString(), id);
            }
            case "Approved", "Disputed" -> {
                require(!text(invoice, "submitted").isEmpty(), "Submit invoice first");
                require(
                    !target.equals("Disputed") || !text(command, "notes").isEmpty(),
                    "Document the dispute"
                );
                repository.updateStatus(target, text(command, "notes"), id);
            }
            default -> throw new IllegalArgumentException("Unsupported state transition");
        }
        audit.record("invoice " + target, "invoice:" + id, command);
        return queries.invoiceState(records.findById("invoices", id));
    }

    @Transactional
    public Object withdraw(int id, Map<String, Object> command) {
        var invoice = records.findById("invoices", id);
        require(
            Boolean.TRUE.equals(command.get("confirmed")) && !text(command, "reason").isEmpty(),
            "Explicit confirmation and a withdrawal reason are required"
        );
        require(
            !text(invoice, "snapshot").isEmpty() &&
                !Set.of("Replaced", "Withdrawn").contains(text(invoice, "state")),
            "Only a live finalized invoice can be withdrawn"
        );
        require(
            repository.countPayments(id) == 0,
            "Invoices with payments require an accounting credit/refund process"
        );
        repository.releaseWork(id);
        repository.releaseExpenses(id);
        repository.markWithdrawn(text(command, "reason"), id);
        audit.record("invoice explicitly withdrawn for correction", "invoice:" + id, command);
        return queries.preview(id);
    }
}
