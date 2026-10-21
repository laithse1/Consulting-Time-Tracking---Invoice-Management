package io.consulting.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExpenseRepository extends JdbcInsertSupport {

    public ExpenseRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public void updateExpense(
        String date,
        String description,
        String amount,
        String approvalReference,
        int expenseId
    ) {
        jdbc.update(
            "UPDATE expenses SET expense_date=?,description=?,amount=?,approval_ref=? WHERE id=?",
            date,
            description,
            amount,
            approvalReference,
            expenseId
        );
    }

    public Integer countExpenseAllocations(int expenseId) {
        return jdbc.queryForObject(
            "SELECT count(*) FROM expense_allocations WHERE expense_id=?",
            Integer.class,
            expenseId
        );
    }

    public int createExpense(
        int contractId,
        String date,
        String description,
        String amount,
        String approvalReference
    ) {
        return insert(
            "INSERT INTO expenses(contract_id,expense_date,description,amount,approval_ref) VALUES(?,?,?,?,?)",
            contractId,
            date,
            description,
            amount,
            approvalReference
        );
    }
}
