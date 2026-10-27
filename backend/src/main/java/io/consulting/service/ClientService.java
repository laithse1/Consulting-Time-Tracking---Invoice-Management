package io.consulting.service;

import static io.consulting.domain.DomainValues.integer;
import static io.consulting.domain.DomainValues.money;
import static io.consulting.domain.DomainValues.require;
import static io.consulting.domain.DomainValues.text;

import io.consulting.repository.ClientRepository;
import io.consulting.repository.RecordRepository;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Client identity, projects, and the contract settings that govern billing. */
@Service
public class ClientService {

    private final ClientRepository repository;
    private final RecordRepository records;
    private final AuditService audit;
    private final io.consulting.repository.RecordLifecycleRepository lifecycle;

    public ClientService(
        ClientRepository repository,
        RecordRepository records,
        AuditService audit,
        io.consulting.repository.RecordLifecycleRepository lifecycle
    ) {
        this.repository = repository;
        this.records = records;
        this.audit = audit;
        this.lifecycle = lifecycle;
    }

    public Map<String, Object> contract(int id) {
        return records.findById("contracts", id);
    }

    public boolean archived(String type, int id) {
        var row = records.findById(type, id);
        return (
            lifecycle.archived(type, id) ||
            (!type.equals("clients") && lifecycle.archived("clients", integer(row, "client_id")))
        );
    }

    public void requireActive(String type, int id) {
        require(
            !archived(type, id),
            "This " + type + " record or its client is archived. Restore it before adding new work."
        );
    }

    public java.util.List<Map<String, Object>> archiveState(
        String type,
        java.util.List<Map<String, Object>> rows
    ) {
        return rows
            .stream()
            .map(row -> {
                var out = new java.util.LinkedHashMap<>(row);
                out.put("archived", archived(type, integer(row, "id")));
                out.put("archived_self", lifecycle.archived(type, integer(row, "id")));
                return (Map<String, Object>) out;
            })
            .toList();
    }

    @Transactional
    public void archive(String type, int id, boolean archived, boolean confirmed) {
        require(
            java.util.Set.of("clients", "contracts", "projects").contains(type),
            "Unknown archive record type"
        );
        require(confirmed, "Confirm this archive or restore action");
        var row = records.findById(type, id);
        require(
            !archived || !lifecycle.hasTimer(type, id),
            "Stop the active timer before archiving this record"
        );
        lifecycle.archive(type, id, archived);
        audit.record(archived ? "record archived" : "record restored", type + ":" + id, row);
    }

    @Transactional
    public Map<String, Object> saveClient(Map<String, Object> command) {
        String name = text(command, "name");
        String email = text(command, "email");
        String address = text(command, "address");
        require(!name.isEmpty(), "Client name required");
        require(email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"), "Valid submission email required");
        int id = integer(command, "id");
        if (id == 0) {
            id = repository.createClient(name, email, address);
        } else {
            repository.updateClient(id, name, email, address);
        }
        audit.record("client saved", "client:" + id, command);
        return records.findById("clients", id);
    }

    @Transactional
    public Map<String, Object> saveProject(Map<String, Object> command) {
        int clientId = integer(command, "client_id");
        records.findById("clients", clientId);
        String name = text(command, "name");
        String repositoryUrl = text(command, "repository");
        require(!name.isEmpty(), "Project name required");
        int id = integer(command, "id");
        if (id == 0) {
            requireActive("clients", clientId);
            id = repository.createProject(clientId, name, repositoryUrl);
        } else {
            require(
                integer(records.findById("projects", id), "client_id") == clientId,
                "A project's client cannot be reassigned"
            );
            repository.updateProject(id, name, repositoryUrl);
        }
        audit.record("project saved", "project:" + id, command);
        return records.findById("projects", id);
    }

    @Transactional
    public Map<String, Object> saveContract(Map<String, Object> command) {
        int clientId = integer(command, "client_id");
        records.findById("clients", clientId);
        LocalDate start = LocalDate.parse(text(command, "start_date"));
        String end = text(command, "end_date");
        require(end.isEmpty() || !LocalDate.parse(end).isBefore(start), "End date precedes start");
        require(
            money(command.get("rate")).signum() > 0 &&
                integer(command, "minimum_hours") >= 0 &&
                integer(command, "maximum_hours") >= integer(command, "minimum_hours") &&
                integer(command, "week_start") >= 1 &&
                integer(command, "week_start") <= 7 &&
                integer(command, "payment_days") > 0 &&
                integer(command, "dispute_days") > 0 &&
                !text(command, "contractor").isEmpty(),
            "Invalid contract settings"
        );
        Object[] fields = {
            clientId,
            text(command, "contractor"),
            start.toString(),
            end.isEmpty() ? null : end,
            money(command.get("rate")).toPlainString(),
            integer(command, "minimum_hours"),
            integer(command, "maximum_hours"),
            integer(command, "payment_days"),
            integer(command, "dispute_days"),
            integer(command, "week_start"),
            text(command, "payment_instructions"),
            text(command, "contractor_address"),
        };
        int id = integer(command, "id");
        if (id == 0) {
            requireActive("clients", clientId);
            id = repository.createContract(fields);
        } else {
            require(
                integer(contract(id), "client_id") == clientId,
                "A contract's client cannot be reassigned"
            );
            repository.updateContract(id, fields);
        }
        audit.record("contract saved", "contract:" + id, command);
        return contract(id);
    }
}
