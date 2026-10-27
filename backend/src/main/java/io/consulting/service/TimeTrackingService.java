package io.consulting.service;

import static io.consulting.domain.DomainValues.integer;
import static io.consulting.domain.DomainValues.require;
import static io.consulting.domain.DomainValues.text;

import io.consulting.repository.RecordRepository;
import io.consulting.repository.TimeTrackingRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TimeTrackingService {

    private final TimeTrackingRepository repository;
    private final RecordRepository records;
    private final ClientService clients;
    private final AuditService audit;

    public TimeTrackingService(
        TimeTrackingRepository repository,
        RecordRepository records,
        ClientService clients,
        AuditService audit
    ) {
        this.repository = repository;
        this.records = records;
        this.clients = clients;
        this.audit = audit;
    }

    @Transactional
    public Map<String, Object> saveEntry(Map<String, Object> command) {
        int id = integer(command, "id"),
            contractId = integer(command, "contract_id");
        var contract = clients.contract(contractId);
        if (id == 0) clients.requireActive("contracts", contractId);
        require(
            id == 0 || repository.countEntryAllocations(id) == 0,
            "Issued time is immutable; use a replacement invoice"
        );
        if (id > 0) audit.record(
            "entry before edit",
            "entry:" + id,
            records.findById("entries", id)
        );
        boolean draft = integer(command, "draft") == 1;
        int seconds = integer(command, "seconds");
        require(
            Set.of("0", "1").contains(text(command, "draft")) &&
                Set.of("0", "1").contains(text(command, "billable")),
            "Billing and draft flags must be 0 or 1"
        );
        require(seconds >= 0 && seconds <= 86400, "Duration must be between 0 and 24 hours");
        require(!text(command, "description").isEmpty(), "Task description required");
        require(!text(command, "category").isEmpty(), "Category required");
        Integer projectId =
            integer(command, "project_id") == 0 ? null : integer(command, "project_id");
        if (projectId != null && id == 0) clients.requireActive("projects", projectId);
        if (projectId != null) require(
            integer(records.findById("projects", projectId), "client_id") ==
                integer(contract, "client_id"),
            "Project belongs to another client"
        );
        String date = text(command, "work_date"),
            start = text(command, "start_time");
        if (!date.isEmpty()) {
            var d = LocalDate.parse(date);
            require(
                !d.isBefore(LocalDate.parse(text(contract, "start_date"))) &&
                    (text(contract, "end_date").isEmpty() ||
                        !d.isAfter(LocalDate.parse(text(contract, "end_date")))),
                "Work date outside contract"
            );
        }
        if (!draft) {
            require(
                !date.isEmpty() && seconds > 0 && seconds <= 86400,
                "Confirmed work needs a date and actual duration (at most 24 hours)"
            );
            if (!start.isEmpty()) {
                int from = LocalTime.parse(start).toSecondOfDay();
                require(
                    from + seconds <= 86400,
                    "Split work crossing midnight into separate entries"
                );
                for (var other : repository.findTimedEntriesOnDate(date, id)) {
                    int a = LocalTime.parse(text(other, "start_time")).toSecondOfDay();
                    require(
                        from + seconds <= a || from >= a + integer(other, "seconds"),
                        "Work overlaps an existing entry"
                    );
                }
            }
        }
        Object[] a = {
            contractId,
            projectId,
            date.isEmpty() ? null : date,
            start.isEmpty() ? null : start,
            draft && seconds == 0 ? null : seconds,
            text(command, "category"),
            text(command, "description"),
            text(command, "repository"),
            text(command, "reference"),
            text(command, "notes"),
            integer(command, "billable"),
            draft ? 1 : 0,
        };
        if (id == 0) id = repository.createEntry(a);
        else {
            var list = new ArrayList<>(Arrays.asList(a));
            list.add(id);
            repository.updateEntry(list.toArray());
        }
        audit.record("entry saved", "entry:" + id, command);
        return records.findById("entries", id);
    }

    @Transactional
    public void deleteEntry(int id, boolean confirmed) {
        require(confirmed, "Deletion confirmation required");
        require(repository.countEntryAllocations(id) == 0, "Issued work cannot be deleted");
        audit.record("entry deleted", "entry:" + id, records.findById("entries", id));
        repository.deleteEntry(id);
    }

    @Transactional
    public Object timer(String action, Map<String, Object> command) {
        var timers = repository.findTimer();
        if (action.equals("start")) {
            require(timers.isEmpty(), "A timer already exists");
            var timerContract = clients.contract(integer(command, "contract_id"));
            clients.requireActive("contracts", integer(command, "contract_id"));
            if (integer(command, "project_id") > 0) clients.requireActive(
                "projects",
                integer(command, "project_id")
            );
            var workDate = LocalDate.parse(text(command, "work_date"));
            require(
                !workDate.isBefore(LocalDate.parse(text(timerContract, "start_date"))) &&
                    (text(timerContract, "end_date").isEmpty() ||
                        !workDate.isAfter(LocalDate.parse(text(timerContract, "end_date")))),
                "Timer date outside contract"
            );
            require(
                !text(command, "category").isEmpty() && integer(command, "project_id") >= 0,
                "Timer category and valid project selection required"
            );
            if (integer(command, "project_id") > 0) require(
                integer(
                    records.findById("projects", integer(command, "project_id")),
                    "client_id"
                ) == integer(clients.contract(integer(command, "contract_id")), "client_id"),
                "Project belongs to another client"
            );
            require(!text(command, "description").isEmpty(), "Description required");
            LocalDate.parse(text(command, "work_date"));
            repository.createTimer(
                integer(command, "contract_id"),
                integer(command, "project_id") == 0 ? null : integer(command, "project_id"),
                text(command, "description"),
                text(command, "category"),
                text(command, "work_date"),
                Instant.now().toString()
            );
        } else {
            require(!timers.isEmpty(), "No timer exists");
            var timer = timers.getFirst();
            long elapsed =
                integer(timer, "elapsed") +
                (text(timer, "started").isEmpty()
                    ? 0
                    : Duration.between(
                          Instant.parse(text(timer, "started")),
                          Instant.now()
                      ).getSeconds());
            switch (action) {
                case "pause" -> repository.pauseTimer(elapsed);
                case "resume" -> {
                    require(text(timer, "started").isEmpty(), "Timer already running");
                    repository.resumeTimer(Instant.now().toString());
                }
                case "stop" -> {
                    var entry = new HashMap<>(timer);
                    entry.remove("id");
                    entry.put("seconds", elapsed <= 86400 ? elapsed : 0);
                    entry.put("draft", 1);
                    entry.put("billable", 1);
                    entry.put(
                        "notes",
                        "Timer result: " +
                            elapsed +
                            " elapsed seconds. Confirm local start time, date and duration before billing. Split sessions over 24 hours into dated entries; their duration is left incomplete."
                    );
                    saveEntry(entry);
                    repository.deleteTimer();
                }
                default -> throw new IllegalArgumentException("Unknown timer action");
            }
        }
        audit.record("timer " + action, "timer", command);
        return repository.findTimer();
    }
}
