package io.consulting.service;

import static io.consulting.domain.DomainValues.require;

import io.consulting.repository.RecordRepository;
import io.consulting.support.JsonDocuments;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application boundary used by assistant orchestration and export integrations. */
@Service
public class WorkspaceService {

    private final ClientService clients;
    private final TimeTrackingService timeTracking;
    private final ComplianceService compliance;
    private final ExpenseService expenses;
    private final InvoiceService invoices;
    private final InvoiceQueryService invoiceQueries;
    private final PaymentService payments;
    private final RecordRepository records;
    private final JsonDocuments documents;
    private final AuditService audit;

    public WorkspaceService(
        ClientService clients,
        TimeTrackingService timeTracking,
        ComplianceService compliance,
        ExpenseService expenses,
        InvoiceService invoices,
        InvoiceQueryService invoiceQueries,
        PaymentService payments,
        RecordRepository records,
        JsonDocuments documents,
        AuditService audit
    ) {
        this.clients = clients;
        this.timeTracking = timeTracking;
        this.compliance = compliance;
        this.expenses = expenses;
        this.invoices = invoices;
        this.invoiceQueries = invoiceQueries;
        this.payments = payments;
        this.records = records;
        this.documents = documents;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> state() {
        Map<String, Object> state = new LinkedHashMap<>();
        for (String table : List.of(
            "clients",
            "contracts",
            "projects",
            "entries",
            "weekly",
            "expenses",
            "historical",
            "audit"
        )) {
            state.put(table, records.findNewestFirst(table));
        }
        state.put(
            "invoices",
            records.findNewestFirst("invoices").stream().map(invoiceQueries::invoiceState).toList()
        );
        state.put("payments", records.findNewestFirst("payments"));
        state.put("timer", records.findAll("timer"));
        return state;
    }

    public List<Map<String, Object>> historyRows(String table) {
        require(List.of("payments", "historical").contains(table), "Invalid history table");
        return records.findAll(table);
    }

    public Map<String, Object> saveClient(Map<String, Object> command) {
        return clients.saveClient(command);
    }

    public Map<String, Object> saveProject(Map<String, Object> command) {
        return clients.saveProject(command);
    }

    public Map<String, Object> saveContract(Map<String, Object> command) {
        return clients.saveContract(command);
    }

    public Map<String, Object> contract(int id) {
        return clients.contract(id);
    }

    public Map<String, Object> saveEntry(Map<String, Object> command) {
        return timeTracking.saveEntry(command);
    }

    public void deleteEntry(int id, boolean confirmed) {
        timeTracking.deleteEntry(id, confirmed);
    }

    public Object timer(String action, Map<String, Object> command) {
        return timeTracking.timer(action, command);
    }

    public List<Map<String, Object>> compliance(int contractId, LocalDate from, LocalDate to) {
        return compliance.compliance(contractId, from, to);
    }

    public void saveWeek(Map<String, Object> command) {
        compliance.saveWeek(command);
    }

    public Object expense(Map<String, Object> command) {
        return expenses.expense(command);
    }

    public Map<String, Object> draft(Map<String, Object> command) {
        return invoices.draft(command);
    }

    public Map<String, Object> preview(int id) {
        return invoiceQueries.preview(id);
    }

    public Map<String, Object> invoiceState(Map<String, Object> invoice) {
        return invoiceQueries.invoiceState(invoice);
    }

    public Map<String, Object> finalizeInvoice(int id, boolean confirmed) {
        return invoices.finalizeInvoice(id, confirmed);
    }

    public Object transition(int id, Map<String, Object> command) {
        return invoices.transition(id, command);
    }

    public Object payment(int id, Map<String, Object> command) {
        return payments.payment(id, command);
    }

    public Object withdraw(int id, Map<String, Object> command) {
        return invoices.withdraw(id, command);
    }

    public void historical(Map<String, Object> command) {
        payments.historical(command);
    }

    public Map<String, Object> email(int id) {
        return invoiceQueries.email(id);
    }

    public Map<String, Object> one(String table, int id) {
        return records.findById(table, id);
    }

    public String encode(Object value) {
        return documents.encode(value);
    }

    public Map<String, Object> decode(String value) {
        return documents.decode(value);
    }

    public void audit(String action, String entity, Object detail) {
        audit.record(action, entity, detail);
    }
}
