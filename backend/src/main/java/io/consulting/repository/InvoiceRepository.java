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

    public void editDraft(
        int id,
        String from,
        String to,
        String date,
        String presentation,
        String adjustment,
        String reason,
        String approval,
        String notes
    ) {
        jdbc.update(
            "UPDATE invoices SET period_start=?,period_end=?,invoice_date=?,presentation=?,adjustment=?,adjustment_reason=?,adjustment_approval=?,notes=? WHERE id=?",
            from,
            to,
            date,
            presentation,
            adjustment,
            reason,
            approval,
            notes,
            id
        );
    }

    public void deleteDraft(int id) {
        // Retain the number and audit identity; it must never be reassigned to another invoice.
        jdbc.update("UPDATE invoices SET state='Deleted' WHERE id=?", id);
    }

    public java.util.List<java.util.Map<String, Object>> linkedPlans(int invoiceId) {
        return jdbc.queryForList(
            """
            SELECT * FROM assistant_plans WHERE EXISTS (
                SELECT 1 FROM json_each(plan_json,'$.actions')
                WHERE CAST(json_extract(value,'$._invoice_id') AS INTEGER)=?
            )
            """,
            invoiceId
        );
    }

    public void synchronizePlan(String id, int version, String document) {
        jdbc.update(
            "UPDATE assistant_plans SET plan_json=?,version=version+1 WHERE id=? AND version=?",
            document,
            id,
            version
        );
    }
}
