package io.consulting.service;

import static io.consulting.domain.DomainValues.charge;
import static io.consulting.domain.DomainValues.hours;
import static io.consulting.domain.DomainValues.integer;
import static io.consulting.domain.DomainValues.money;
import static io.consulting.domain.DomainValues.text;

import io.consulting.repository.InvoiceQueryRepository;
import io.consulting.repository.RecordRepository;
import io.consulting.support.JsonDocuments;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class InvoiceQueryService {

    private final InvoiceQueryRepository repository;
    private final RecordRepository records;
    private final ClientService clients;
    private final ComplianceService compliance;
    private final JsonDocuments documents;

    public InvoiceQueryService(
        InvoiceQueryRepository repository,
        RecordRepository records,
        ClientService clients,
        ComplianceService compliance,
        JsonDocuments documents
    ) {
        this.repository = repository;
        this.records = records;
        this.clients = clients;
        this.compliance = compliance;
        this.documents = documents;
    }

    public Map<String, Object> preview(int id) {
        var invoice = records.findById("invoices", id);
        if (!text(invoice, "snapshot").isEmpty()) {
            var result = documents.decode(text(invoice, "snapshot"));
            result.put("invoice", invoiceState(invoice));
            return result;
        }
        int contractId = integer(invoice, "contract_id"),
            replacement = integer(invoice, "replacement_for");
        var contract = clients.contract(contractId);
        var entries = repository.findEligibleWork(
            contractId,
            text(invoice, "period_start"),
            text(invoice, "period_end"),
            replacement
        );
        var expenses = repository.findEligibleExpenses(
            contractId,
            text(invoice, "period_start"),
            text(invoice, "period_end"),
            replacement
        );
        var warnings = new ArrayList<String>();
        long seconds = 0;
        BigDecimal subtotal = BigDecimal.ZERO;
        for (var entry : entries) {
            seconds += integer(entry, "seconds");
            var amount = charge(integer(entry, "seconds"), money(contract.get("rate")));
            entry.put("amount", amount);
            subtotal = subtotal.add(amount);
            if (text(entry, "description").isEmpty()) warnings.add(
                "Entry " + integer(entry, "id") + ": description missing"
            );
            if (
                text(entry, "project").isEmpty() && text(entry, "repository").isEmpty()
            ) warnings.add("Entry " + integer(entry, "id") + ": project reference missing");
        }
        BigDecimal expenseTotal = BigDecimal.ZERO;
        for (var entry : expenses) {
            if (text(entry, "approval_ref").isEmpty()) warnings.add(
                "Expense " + integer(entry, "id") + ": prior approval missing"
            );
            expenseTotal = expenseTotal.add(money(entry.get("amount")));
        }
        if (
            entries.isEmpty() &&
            expenses.isEmpty() &&
            money(invoice.get("adjustment")).signum() == 0
        ) warnings.add("No eligible billable work or approved charges");
        for (var w : compliance.compliance(
            contractId,
            LocalDate.parse(text(invoice, "period_start")),
            LocalDate.parse(text(invoice, "period_end"))
        ))
            if (Boolean.TRUE.equals(w.get("unresolved"))) warnings.add(
                w.get("week") +
                    ": " +
                    w.get("status") +
                    (Boolean.TRUE.equals(w.get("partial"))
                        ? "; partial contract week needs clarification"
                        : "")
            );
        var result = new LinkedHashMap<String, Object>();
        result.put("invoice", invoice);
        if (replacement > 0) result.put(
            "replacementNumber",
            text(records.findById("invoices", replacement), "number")
        );
        result.put("contract", contract);
        result.put("client", records.findById("clients", integer(contract, "client_id")));
        result.put("entries", entries);
        result.put("expenses", expenses);
        result.put("hours", hours(seconds));
        result.put("subtotal", subtotal.setScale(2));
        result.put("expenseTotal", expenseTotal.setScale(2));
        result.put(
            "total",
            subtotal
                .add(expenseTotal)
                .add(money(invoice.get("adjustment")))
                .setScale(2)
        );
        result.put("warnings", warnings);
        return result;
    }

    public Map<String, Object> invoiceState(Map<String, Object> raw) {
        var invoice = new LinkedHashMap<>(raw);
        BigDecimal paid = repository
            .findPayments(integer(invoice, "id"))
            .stream()
            .map(x -> money(x.get("amount")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        var total = money(invoice.get("total"));
        invoice.put("paid", paid);
        invoice.put("balance", total.subtract(paid));
        String state = text(invoice, "state");
        invoice.put("workflow_state", state);
        var invoiceClient = text(invoice, "snapshot").isEmpty()
            ? records.findById(
                  "clients",
                  integer(clients.contract(integer(invoice, "contract_id")), "client_id")
              )
            : (Map<String, Object>) documents.decode(text(invoice, "snapshot")).get("client");
        invoice.put("client_name", text(invoiceClient, "name"));
        if (!text(invoice, "submitted").isEmpty()) {
            var due = LocalDate.parse(text(invoice, "submitted")).plusDays(
                integer(
                    text(invoice, "snapshot").isEmpty()
                        ? clients.contract(integer(invoice, "contract_id"))
                        : (Map<String, Object>) documents
                              .decode(text(invoice, "snapshot"))
                              .get("contract"),
                    "payment_days"
                )
            );
            invoice.put("due", due.toString());
            if (!Set.of("Replaced", "Withdrawn").contains(state) && !state.equals("Disputed")) {
                if (paid.compareTo(total) >= 0) state = "Paid";
                else if (due.isBefore(LocalDate.now())) state = "Overdue";
                else if (paid.signum() > 0) state = "Partially Paid";
            }
        }
        if (Set.of("Replaced", "Withdrawn").contains(state)) invoice.put(
            "balance",
            new BigDecimal("0.00")
        );
        invoice.put("state", state);
        invoice.put(
            "overdue",
            invoice.containsKey("due") &&
                LocalDate.parse(text(invoice, "due")).isBefore(LocalDate.now()) &&
                total.compareTo(paid) > 0 &&
                !Set.of("Replaced", "Withdrawn").contains(state)
        );
        return invoice;
    }

    public Map<String, Object> email(int id) {
        var preview = preview(id);
        var invoice = (Map<String, Object>) preview.get("invoice");
        var contract = (Map<String, Object>) preview.get("contract");
        var client = (Map<String, Object>) preview.get("client");
        String period = text(invoice, "period_start") + " to " + text(invoice, "period_end");
        return Map.of(
            "to",
            text(client, "email"),
            "subject",
            "Consulting Invoice — " + period + " — " + text(contract, "contractor"),
            "body",
            "Hello,\n\nAttached is invoice " +
                text(invoice, "number") +
                " for consulting services provided during " +
                period +
                " under our Independent Contractor Agreement.\n\nThe invoice includes an itemized breakdown of work performed, dates, billable hours, and applicable project references.\n\nPlease let me know if any additional information is needed.\n\nThank you,\n" +
                text(contract, "contractor")
        );
    }
}
