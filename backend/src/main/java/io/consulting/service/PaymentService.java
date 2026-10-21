package io.consulting.service;

import static io.consulting.domain.DomainValues.money;
import static io.consulting.domain.DomainValues.require;
import static io.consulting.domain.DomainValues.text;

import io.consulting.repository.PaymentRepository;
import io.consulting.repository.RecordRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository repository;
    private final RecordRepository records;
    private final AuditService audit;
    private final InvoiceQueryService queries;

    public PaymentService(
        PaymentRepository repository,
        RecordRepository records,
        AuditService audit,
        InvoiceQueryService queries
    ) {
        this.repository = repository;
        this.records = records;
        this.audit = audit;
        this.queries = queries;
    }

    @Transactional
    public Object payment(int id, Map<String, Object> command) {
        var invoice = queries.invoiceState(records.findById("invoices", id));
        require(
            !text(invoice, "submitted").isEmpty() &&
                !Set.of("Replaced", "Withdrawn").contains(text(invoice, "state")),
            "Invoice must be submitted"
        );
        var amount = money(command.get("amount"));
        var date = LocalDate.parse(text(command, "received"));
        require(
            !date.isAfter(LocalDate.now()) &&
                !date.isBefore(LocalDate.parse(text(invoice, "submitted"))),
            "Invalid receipt date"
        );
        require(
            amount.signum() > 0 && amount.compareTo(money(invoice.get("balance"))) <= 0,
            "Payment must be positive and cannot exceed balance"
        );
        require(
            !text(command, "method").isEmpty() && !text(command, "reference").isEmpty(),
            "Payment method and reference required"
        );
        repository.createPayment(
            id,
            amount.toPlainString(),
            date.toString(),
            text(command, "method"),
            text(command, "reference")
        );
        audit.record("payment received", "invoice:" + id, command);
        return queries.invoiceState(records.findById("invoices", id));
    }

    @Transactional
    public void historical(Map<String, Object> command) {
        LocalDate receivedDate = LocalDate.parse(text(command, "received"));
        require(
            !receivedDate.isAfter(LocalDate.now()) && !text(command, "reference").isEmpty(),
            "Actual date and receipt reference required"
        );
        var old = records.findById("historical", 1);
        require(text(old, "received").isEmpty(), "Historical receipt already recorded");
        repository.confirmHistoricalReceipt(receivedDate.toString(), text(command, "reference"));
        audit.record("historical receipt recorded", "historical:1", command);
    }
}
