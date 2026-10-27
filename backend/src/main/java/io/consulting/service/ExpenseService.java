package io.consulting.service;

import static io.consulting.domain.DomainValues.integer;
import static io.consulting.domain.DomainValues.money;
import static io.consulting.domain.DomainValues.require;
import static io.consulting.domain.DomainValues.text;

import io.consulting.repository.ExpenseRepository;
import io.consulting.repository.RecordRepository;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

    private final ExpenseRepository repository;
    private final RecordRepository records;
    private final ClientService clients;
    private final AuditService audit;

    public ExpenseService(
        ExpenseRepository repository,
        RecordRepository records,
        ClientService clients,
        AuditService audit
    ) {
        this.repository = repository;
        this.records = records;
        this.clients = clients;
        this.audit = audit;
    }

    @Transactional
    public Object expense(Map<String, Object> command) {
        var contract = clients.contract(integer(command, "contract_id"));
        LocalDate.parse(text(command, "expense_date"));
        require(
            money(command.get("amount")).signum() > 0 && !text(command, "description").isEmpty(),
            "Positive expense and description required"
        );
        int id = integer(command, "id");
        if (id == 0) clients.requireActive("contracts", integer(command, "contract_id"));
        if (id == 0) id = repository.createExpense(
            integer(contract, "id"),
            text(command, "expense_date"),
            text(command, "description"),
            money(command.get("amount")).toPlainString(),
            text(command, "approval_ref")
        );
        else {
            require(repository.countExpenseAllocations(id) == 0, "Issued expenses are immutable");
            require(
                integer(records.findById("expenses", id), "contract_id") == integer(contract, "id"),
                "Expense belongs to another contract"
            );
            audit.record("expense before edit", "expense:" + id, records.findById("expenses", id));
            repository.updateExpense(
                text(command, "expense_date"),
                text(command, "description"),
                money(command.get("amount")).toPlainString(),
                text(command, "approval_ref"),
                id
            );
        }
        audit.record("expense recorded", "expense:" + id, command);
        return records.findById("expenses", id);
    }

    @Transactional
    public void delete(int id, boolean confirmed) {
        require(confirmed, "Confirm expense deletion");
        var expense = records.findById("expenses", id);
        require(
            repository.countExpenseAllocations(id) == 0,
            "Issued expenses are immutable; use the invoice correction workflow"
        );
        audit.record("expense deleted", "expense:" + id, expense);
        repository.deleteExpense(id);
    }
}
