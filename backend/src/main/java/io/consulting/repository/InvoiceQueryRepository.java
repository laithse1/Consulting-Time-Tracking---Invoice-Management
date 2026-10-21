package io.consulting.repository;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InvoiceQueryRepository extends JdbcInsertSupport {

    public List<Map<String, Object>> findEligibleWork(
        int contractId,
        String from,
        String to,
        int replacementInvoiceId
    ) {
        return jdbc.queryForList(
            """
            SELECT e.*, p.name AS project
            FROM entries e
            LEFT JOIN projects p ON p.id=e.project_id
            LEFT JOIN allocations a ON a.entry_id=e.id
            WHERE e.contract_id=?
              AND e.draft=0
              AND e.billable=1
              AND e.work_date BETWEEN ?
              AND ?
              AND (a.invoice_id IS NULL OR a.invoice_id=?)
            ORDER BY e.work_date, e.start_time
            """,
            contractId,
            from,
            to,
            replacementInvoiceId
        );
    }

    public List<Map<String, Object>> findEligibleExpenses(
        int contractId,
        String from,
        String to,
        int replacementInvoiceId
    ) {
        return jdbc.queryForList(
            """
            SELECT e.*
            FROM expenses e
            LEFT JOIN expense_allocations a ON a.expense_id=e.id
            WHERE e.contract_id=?
              AND e.expense_date BETWEEN ?
              AND ?
              AND (a.invoice_id IS NULL OR a.invoice_id=?)
            """,
            contractId,
            from,
            to,
            replacementInvoiceId
        );
    }

    public InvoiceQueryRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public List<Map<String, Object>> findPayments(int invoiceId) {
        return jdbc.queryForList("SELECT amount FROM payments WHERE invoice_id=?", invoiceId);
    }
}
