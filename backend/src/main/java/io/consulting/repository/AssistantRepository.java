package io.consulting.repository;

import io.consulting.domain.WorkPeriod;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Assistant drafts and receipts use the same database and transaction manager as the ledger. */
@Repository
public class AssistantRepository {

    private final JdbcTemplate jdbc;

    public AssistantRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> findDraft(int contractId) {
        return jdbc.queryForList(
            "SELECT source,updated FROM assistant_drafts WHERE contract_id=?",
            contractId
        );
    }

    public List<Map<String, Object>> findPlans(int contractId, int limit) {
        return jdbc.queryForList(
            "SELECT * FROM assistant_plans WHERE contract_id=? ORDER BY created DESC LIMIT ?",
            contractId,
            limit
        );
    }

    public List<Map<String, Object>> findPlan(String id) {
        return jdbc.queryForList("SELECT * FROM assistant_plans WHERE id=?", id);
    }

    public void saveDraft(int contractId, String source) {
        jdbc.update(
            """
            INSERT INTO assistant_drafts(contract_id, source, updated)
            VALUES(?, ?, ?)
            ON CONFLICT(contract_id)
            DO UPDATE SET source=excluded.source, updated=excluded.updated
            """,
            contractId,
            source,
            Instant.now().toString()
        );
    }

    public List<Map<String, Object>> findProjects(int clientId) {
        return jdbc.queryForList("SELECT * FROM projects WHERE client_id=?", clientId);
    }

    public List<Map<String, Object>> findOtherClients(int clientId) {
        return jdbc.queryForList("SELECT * FROM clients WHERE id<>?", clientId);
    }

    public void createPlan(
        String id,
        int contractId,
        String source,
        String referenceDate,
        String engine,
        String document
    ) {
        jdbc.update(
            """
            INSERT INTO assistant_plans(id, contract_id, source, reference_date, engine, plan_json,
                created)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            contractId,
            source,
            referenceDate,
            engine,
            document,
            Instant.now().toString()
        );
    }

    public void updatePlan(String id, String document) {
        jdbc.update(
            "UPDATE assistant_plans SET plan_json=?,version=version+1 WHERE id=?",
            document,
            id
        );
    }

    public boolean updateProposedPlan(String id, int version, String document) {
        return (
            jdbc.update(
                "UPDATE assistant_plans SET plan_json=?,version=version+1 WHERE id=? AND version=? AND status='proposed'",
                document,
                id,
                version
            ) == 1
        );
    }

    public List<Map<String, Object>> findRevisions(String id) {
        return jdbc.queryForList(
            "SELECT version,instruction,engine,reply,created FROM assistant_revisions WHERE plan_id=? ORDER BY version",
            id
        );
    }

    public void saveRevision(
        String id,
        int version,
        String instruction,
        String engine,
        String reply,
        String before,
        String after
    ) {
        jdbc.update(
            """
            INSERT INTO assistant_revisions(plan_id,version,instruction,engine,reply,before_json,after_json,created)
            VALUES(?,?,?,?,?,?,?,?)
            """,
            id,
            version,
            instruction,
            engine,
            reply,
            before,
            after,
            Instant.now().toString()
        );
    }

    public boolean reopenPlan(String id, int version, String document) {
        return (
            jdbc.update(
                "UPDATE assistant_plans SET status='proposed',plan_json=?,version=version+1 WHERE id=? AND version=? AND results_json IS NOT NULL",
                document,
                id,
                version
            ) == 1
        );
    }

    public boolean isEntryAllocated(int id) {
        return (
            jdbc.queryForObject(
                "SELECT count(*) FROM allocations WHERE entry_id=?",
                Integer.class,
                id
            ) > 0
        );
    }

    public List<Map<String, Object>> findTimerOwner() {
        return jdbc.queryForList("SELECT contract_id FROM timer");
    }

    public List<Map<String, Object>> findInvoice(String number, int contractId) {
        return jdbc.queryForList(
            "SELECT id FROM invoices WHERE number=? AND contract_id=?",
            number,
            contractId
        );
    }

    public void completePlan(String id, String results) {
        jdbc.update(
            "UPDATE assistant_plans SET status='applied',results_json=?,applied=? WHERE id=?",
            results,
            Instant.now().toString(),
            id
        );
    }

    public List<Map<String, Object>> findReportWork(int contractId, WorkPeriod range) {
        return jdbc.queryForList(
            """
            SELECT e.*, p.name AS project
            FROM entries e
            LEFT JOIN projects p ON p.id=e.project_id
            WHERE e.contract_id=?
              AND e.draft=0
              AND e.work_date BETWEEN ?
              AND ?
            ORDER BY e.work_date
            """,
            contractId,
            range.from().toString(),
            range.to().toString()
        );
    }
}
