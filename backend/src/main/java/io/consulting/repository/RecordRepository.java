package io.consulting.repository;

import static io.consulting.domain.DomainValues.require;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Shared row lookups; dynamic table names are restricted to this allowlist. */
@Repository
public class RecordRepository {

    private static final Set<String> TABLES = Set.of(
        "clients",
        "contracts",
        "projects",
        "entries",
        "weekly",
        "expenses",
        "historical",
        "audit",
        "invoices",
        "payments",
        "timer"
    );
    private final JdbcTemplate jdbc;

    public RecordRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> findById(String table, int id) {
        validateTable(table);
        List<Map<String, Object>> records = jdbc.queryForList(
            "SELECT * FROM " + table + " WHERE id=?",
            id
        );
        require(!records.isEmpty(), "Record not found");
        return records.getFirst();
    }

    public List<Map<String, Object>> findAll(String table) {
        validateTable(table);
        return jdbc.queryForList("SELECT * FROM " + table);
    }

    public List<Map<String, Object>> findNewestFirst(String table) {
        validateTable(table);
        return jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id DESC");
    }

    private void validateTable(String table) {
        require(TABLES.contains(table), "Unknown record type");
    }
}
