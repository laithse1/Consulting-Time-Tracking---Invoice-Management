package io.consulting.service.assistant;

import static io.consulting.domain.DomainValues.*;

import io.consulting.repository.AssistantRepository;
import io.consulting.service.WorkspaceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

/** Server-calculated review figures and reports reuse the ledger's exact arithmetic. */
@Service
public class AssistantReportingService {

    private final AssistantRepository repository;
    private final WorkspaceService workspace;

    public AssistantReportingService(AssistantRepository repository, WorkspaceService workspace) {
        this.repository = repository;
        this.workspace = workspace;
    }

    public Map<String, Object> report(int contractId, LocalDate from, LocalDate to) {
        require(
            !to.isBefore(from) && to.toEpochDay() - from.toEpochDay() <= 366,
            "Report period must be at most 366 days"
        );
        var c = workspace.contract(contractId);
        var rows = repository.findReportWork(
            contractId,
            new io.consulting.domain.WorkPeriod(from, to)
        );
        long actual = 0,
            billable = 0;
        BigDecimal amount = BigDecimal.ZERO;
        var groups = new TreeMap<String, Long>();
        for (var e : rows) {
            actual += integer(e, "seconds");
            groups.merge(text(e, "project"), (long) integer(e, "seconds"), Long::sum);
            if (integer(e, "billable") == 1) {
                billable += integer(e, "seconds");
                amount = amount.add(charge(integer(e, "seconds"), money(c.get("rate"))));
            }
        }
        return Map.of(
            "from",
            from.toString(),
            "to",
            to.toString(),
            "actualHours",
            hours(actual),
            "billableHours",
            hours(billable),
            "servicesValue",
            amount.setScale(2),
            "projects",
            groups
                .entrySet()
                .stream()
                .map(e -> Map.of("project", e.getKey(), "hours", hours(e.getValue())))
                .toList(),
            "note",
            "Value uses current contract rate and rounded work lines; issued invoices retain their own frozen rates."
        );
    }

    public Map<String, Object> review(int contractId, List<Map<String, Object>> actions) {
        var contract = workspace.contract(contractId);
        long seconds = 0;
        BigDecimal value = BigDecimal.ZERO;
        int incomplete = 0;
        for (var action : actions) {
            if (
                !text(action, "type").equals("log_work") || Boolean.TRUE.equals(action.get("skip"))
            ) continue;
            if (
                integer(action, "draft") == 1 ||
                integer(action, "seconds") == 0 ||
                text(action, "work_date").isBlank() ||
                integer(action, "project_id") == 0
            ) incomplete++;
            else {
                seconds += integer(action, "seconds");
                if (integer(action, "billable") == 1) value = value.add(
                    charge(integer(action, "seconds"), money(contract.get("rate")))
                );
            }
        }
        return Map.of(
            "hours",
            hours(seconds),
            "servicesValue",
            value.setScale(2),
            "incompleteTasks",
            incomplete,
            "note",
            "Reviewed tasks only. The invoice preview also includes existing eligible unbilled work and approved expenses in its service period."
        );
    }
}
