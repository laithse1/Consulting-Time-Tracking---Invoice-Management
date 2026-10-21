package io.consulting.repository;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ComplianceRepository extends JdbcInsertSupport {

    public ComplianceRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public void saveWeekReview(
        int contractId,
        String weekStart,
        int available,
        String exceptionReference,
        String overtimeReference,
        String acknowledgement
    ) {
        jdbc.update(
            """
            INSERT INTO weekly(contract_id, week, available, exception_ref, overtime_ref, acknowledgement)
            VALUES(?, ?, ?, ?, ?, ?)
            ON CONFLICT(contract_id, week)
            DO UPDATE SET available=excluded.available, exception_ref=excluded.exception_ref,
                overtime_ref=excluded.overtime_ref, acknowledgement=excluded.acknowledgement
            """,
            contractId,
            weekStart,
            available,
            exceptionReference,
            overtimeReference,
            acknowledgement
        );
    }

    public Long sumConfirmedBillableSeconds(int contractId, String from, String to) {
        return jdbc.queryForObject(
            """
            SELECT coalesce(sum(seconds), 0)
            FROM entries
            WHERE contract_id=?
              AND draft=0
              AND billable=1
              AND work_date BETWEEN ?
              AND ?
            """,
            Long.class,
            contractId,
            from,
            to
        );
    }

    public List<Map<String, Object>> findWeekReview(int contractId, String weekStart) {
        return jdbc.queryForList(
            "SELECT * FROM weekly WHERE contract_id=? AND week=?",
            contractId,
            weekStart
        );
    }
}
