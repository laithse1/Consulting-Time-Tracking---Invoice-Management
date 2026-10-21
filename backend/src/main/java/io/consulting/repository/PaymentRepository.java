package io.consulting.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRepository extends JdbcInsertSupport {

    public PaymentRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public void confirmHistoricalReceipt(String receivedDate, String reference) {
        jdbc.update(
            "UPDATE historical SET received=?,reference=? WHERE id=1",
            receivedDate,
            reference
        );
    }

    public int createPayment(
        int invoiceId,
        String amount,
        String receivedDate,
        String method,
        String reference
    ) {
        return insert(
            "INSERT INTO payments(invoice_id,amount,received,method,reference) VALUES(?,?,?,?,?)",
            invoiceId,
            amount,
            receivedDate,
            method,
            reference
        );
    }
}
