package io.consulting.repository;

import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditRepository {

    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void append(String action, String entity, String document) {
        jdbc.update(
            "INSERT INTO audit(occurred,action,entity,detail) VALUES(?,?,?,?)",
            Instant.now().toString(),
            action,
            entity,
            document
        );
    }
}
