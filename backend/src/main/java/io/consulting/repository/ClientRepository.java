package io.consulting.repository;

import java.util.ArrayList;
import java.util.Arrays;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ClientRepository extends JdbcInsertSupport {

    public ClientRepository(JdbcTemplate jdbc) {
        super(jdbc);
    }

    public int createClient(String name, String email, String address) {
        return insert(
            "INSERT INTO clients(name,email,address) VALUES(?,?,?)",
            name,
            email,
            address
        );
    }

    public void updateClient(int id, String name, String email, String address) {
        jdbc.update(
            "UPDATE clients SET name=?,email=?,address=? WHERE id=?",
            name,
            email,
            address,
            id
        );
    }

    public int createProject(int clientId, String name, String repository) {
        return insert(
            "INSERT INTO projects(client_id,name,repository) VALUES(?,?,?)",
            clientId,
            name,
            repository
        );
    }

    public void updateProject(int id, String name, String repository) {
        jdbc.update("UPDATE projects SET name=?,repository=? WHERE id=?", name, repository, id);
    }

    public int createContract(Object[] fields) {
        return insert(
            """
            INSERT INTO contracts(client_id, contractor, start_date, end_date, rate, minimum_hours,
                maximum_hours, payment_days, dispute_days, week_start, payment_instructions,
                contractor_address)
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            fields
        );
    }

    public void updateContract(int id, Object[] fields) {
        var parameters = new ArrayList<>(Arrays.asList(fields));
        parameters.add(id);
        jdbc.update(
            """
            UPDATE contracts
            SET client_id=?, contractor=?, start_date=?, end_date=?, rate=?, minimum_hours=?,
                maximum_hours=?, payment_days=?, dispute_days=?, week_start=?, payment_instructions=?,
                contractor_address=?
            WHERE id=?
            """,
            parameters.toArray()
        );
    }
}
