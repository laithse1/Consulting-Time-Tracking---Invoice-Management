package io.consulting.service.assistant;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AssistantMigrationTest {

    @TempDir
    Path directory;

    @Test
    void upgradingExistingV2DatabasePreservesEveryBusinessRowAndReceipt() {
        String url = "jdbc:sqlite:" + directory.resolve("upgrade.db");
        Flyway.configure().dataSource(url, null, null).target("2").load().migrate();
        var db = new JdbcTemplate(new DriverManagerDataSource(url));
        db.update(
            "INSERT INTO assistant_drafts(contract_id,source,updated) VALUES(1,'Existing unfinished notes','2026-09-15')"
        );
        db.update(
            """
            INSERT INTO assistant_plans(id,contract_id,source,reference_date,engine,plan_json,status,results_json,created)
            VALUES('existing',1,'Previous work','2026-09-15','local-rules','{"actions":[]}','applied','{"results":[]}','2026-09-15')
            """
        );
        var tables = List.of(
            "clients",
            "contracts",
            "projects",
            "entries",
            "weekly",
            "timer",
            "expenses",
            "invoices",
            "allocations",
            "expense_allocations",
            "payments",
            "audit",
            "historical",
            "assistant_drafts",
            "assistant_plans"
        );
        Map<String, List<Map<String, Object>>> before = new LinkedHashMap<>();
        tables.forEach(table -> before.put(table, db.queryForList("SELECT * FROM " + table)));
        assertEquals(
            1,
            Flyway.configure().dataSource(url, null, null).load().migrate().migrationsExecuted
        );
        for (String table : tables)
            assertEquals(before.get(table), db.queryForList("SELECT * FROM " + table), table);
        assertEquals("ok", db.queryForObject("PRAGMA integrity_check", String.class));
        assertEquals(
            0,
            db.queryForObject("SELECT count(*) FROM assistant_revisions", Integer.class)
        );
    }
}
