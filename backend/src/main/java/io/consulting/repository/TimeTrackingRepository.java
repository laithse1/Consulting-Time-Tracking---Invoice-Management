package io.consulting.repository;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TimeTrackingRepository extends JdbcInsertSupport {

    public TimeTrackingRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public List<Map<String, Object>> findTimer() {
        return jdbc.queryForList("SELECT * FROM timer");
    }

    public void deleteTimer() {
        jdbc.update("DELETE FROM timer");
    }

    public void resumeTimer(String startedAt) {
        jdbc.update("UPDATE timer SET started=? WHERE id=1", startedAt);
    }

    public void pauseTimer(long elapsedSeconds) {
        jdbc.update("UPDATE timer SET started=NULL,elapsed=? WHERE id=1", elapsedSeconds);
    }

    public void createTimer(
        int contractId,
        Integer projectId,
        String description,
        String category,
        String workDate,
        String startedAt
    ) {
        jdbc.update(
            "INSERT INTO timer VALUES(1,?,?,?,?,?,?,0)",
            contractId,
            projectId,
            description,
            category,
            workDate,
            startedAt
        );
    }

    public void deleteEntry(int entryId) {
        jdbc.update("DELETE FROM entries WHERE id=?", entryId);
    }

    public Integer countEntryAllocations(int entryId) {
        return jdbc.queryForObject(
            "SELECT count(*) FROM allocations WHERE entry_id=?",
            Integer.class,
            entryId
        );
    }

    public void updateEntry(Object[] fields) {
        jdbc.update(
            """
            UPDATE entries
            SET contract_id=?, project_id=?, work_date=?, start_time=?, seconds=?, category=?,
                description=?, repository=?, reference=?, notes=?, billable=?, draft=?
            WHERE id=?
            """,
            fields
        );
    }

    public int createEntry(Object[] fields) {
        return insert(
            """
            INSERT INTO entries(contract_id, project_id, work_date, start_time, seconds, category,
                description, repository, reference, notes, billable, draft)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            fields
        );
    }

    public List<Map<String, Object>> findTimedEntriesOnDate(String workDate, int excludedEntryId) {
        return jdbc.queryForList(
            "SELECT * FROM entries WHERE draft=0 AND start_time IS NOT NULL AND work_date=? AND id<>?",
            workDate,
            excludedEntryId
        );
    }
}
