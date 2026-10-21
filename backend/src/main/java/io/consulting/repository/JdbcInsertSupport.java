package io.consulting.repository;

import org.springframework.jdbc.core.JdbcTemplate;

/** SQLite identity reads share the transaction-bound connection with their insert. */
abstract class JdbcInsertSupport {

    protected final JdbcTemplate jdbc;

    protected JdbcInsertSupport(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    protected int insert(String sql, Object... parameters) {
        jdbc.update(sql, parameters);
        return jdbc.queryForObject("SELECT last_insert_rowid()", Integer.class);
    }
}
