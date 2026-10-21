package io.consulting.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InvoiceRepository extends JdbcInsertSupport {

    public InvoiceRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public void markWithdrawn(String reason, int invoiceId) {
        jdbc.update("UPDATE invoices SET state='Withdrawn',notes=? WHERE id=?", reason, invoiceId);
    }

    public void releaseExpenses(int invoiceId) {
        jdbc.update("DELETE FROM expense_allocations WHERE invoice_id=?", invoiceId);
    }

    public void releaseWork(int invoiceId) {
        jdbc.update("DELETE FROM allocations WHERE invoice_id=?", invoiceId);
    }

    public Integer countPayments(int invoiceId) {
        return jdbc.queryForObject(
            "SELECT count(*) FROM payments WHERE invoice_id=?",
            Integer.class,
            invoiceId
        );
    }

    public void updateStatus(String status, String notes, int invoiceId) {
        jdbc.update("UPDATE invoices SET state=?,notes=? WHERE id=?", status, notes, invoiceId);
    }

    public void markSubmitted(String submittedDate, int invoiceId) {
        jdbc.update(
            "UPDATE invoices SET state='Sent',submitted=? WHERE id=?",
            submittedDate,
            invoiceId
        );
    }

    public void freezeSnapshot(String snapshot, String total, int invoiceId) {
        jdbc.update(
            "UPDATE invoices SET snapshot=?,total=?,state='Ready for Review' WHERE id=?",
            snapshot,
            total,
            invoiceId
        );
    }

    public void allocateExpense(int expenseId, int invoiceId) {
        jdbc.update("INSERT INTO expense_allocations VALUES(?,?)", expenseId, invoiceId);
    }

    public void allocateWork(int entryId, int invoiceId) {
        jdbc.update("INSERT INTO allocations VALUES(?,?)", entryId, invoiceId);
    }

    public void markReplaced(int invoiceId) {
        jdbc.update("UPDATE invoices SET state='Replaced' WHERE id=?", invoiceId);
    }

    public int createInvoice(
        String number,
        int contractId,
        String periodStart,
        String periodEnd,
        String invoiceDate,
        String adjustment,
        String adjustmentReason,
        String adjustmentApproval,
        String presentation,
        Integer replacementFor,
        String revisionReason
    ) {
        return insert(
            """
            INSERT INTO invoices(number, contract_id, period_start, period_end, invoice_date, adjustment,
                adjustment_reason, adjustment_approval, presentation, replacement_for, revision_reason)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            number,
            contractId,
            periodStart,
            periodEnd,
            invoiceDate,
            adjustment,
            adjustmentReason,
            adjustmentApproval,
            presentation,
            replacementFor,
            revisionReason
        );
    }

    public Integer nextSequence(String prefixPattern) {
        return jdbc.queryForObject(
            "SELECT count(*)+1 FROM invoices WHERE number LIKE ?",
            Integer.class,
            prefixPattern
        );
    }
}
