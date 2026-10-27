package io.consulting.repository;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RecordLifecycleRepository {

    private final JdbcTemplate jdbc;

    public RecordLifecycleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean archived(String type, int id) {
        return (
            jdbc.queryForObject(
                "SELECT count(*) FROM record_archives WHERE record_type=? AND record_id=?",
                Integer.class,
                type,
                id
            ) > 0
        );
    }

    public void archive(String type, int id, boolean archived) {
        if (archived) jdbc.update(
            "INSERT OR IGNORE INTO record_archives VALUES(?,?,?)",
            type,
            id,
            Instant.now().toString()
        );
        else jdbc.update(
            "DELETE FROM record_archives WHERE record_type=? AND record_id=?",
            type,
            id
        );
    }

    public boolean hasTimer(String type, int id) {
        return switch (type) {
            case "contracts" -> jdbc.queryForObject(
                "SELECT count(*) FROM timer WHERE contract_id=?",
                Integer.class,
                id
            ) > 0;
            case "projects" -> jdbc.queryForObject(
                "SELECT count(*) FROM timer WHERE project_id=?",
                Integer.class,
                id
            ) > 0;
            case "clients" -> jdbc.queryForObject(
                "SELECT count(*) FROM timer t JOIN contracts c ON c.id=t.contract_id WHERE c.client_id=?",
                Integer.class,
                id
            ) > 0;
            default -> false;
        };
    }
}
