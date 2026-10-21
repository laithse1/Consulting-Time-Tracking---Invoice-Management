package io.consulting.service;

import static io.consulting.domain.DomainValues.hours;
import static io.consulting.domain.DomainValues.integer;
import static io.consulting.domain.DomainValues.require;
import static io.consulting.domain.DomainValues.text;
import static io.consulting.domain.DomainValues.weekStart;

import io.consulting.repository.ComplianceRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ComplianceService {

    private final ComplianceRepository repository;
    private final ClientService clients;
    private final AuditService audit;

    public ComplianceService(
        ComplianceRepository repository,
        ClientService clients,
        AuditService audit
    ) {
        this.repository = repository;
        this.clients = clients;
        this.audit = audit;
    }

    public List<Map<String, Object>> compliance(int contractId, LocalDate from, LocalDate to) {
        var contract = clients.contract(contractId);
        require(
            !to.isBefore(from) &&
                Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays() <= 370,
            "Choose at most 370 days"
        );
        var result = new ArrayList<Map<String, Object>>();
        var first = LocalDate.parse(text(contract, "start_date"));
        LocalDate last = text(contract, "end_date").isEmpty()
            ? to.plusDays(7)
            : LocalDate.parse(text(contract, "end_date"));
        for (
            var review = weekStart(from, integer(contract, "week_start"));
            !review.isAfter(to);
            review = review.plusWeeks(1)
        ) {
            if (review.plusDays(6).isBefore(first) || review.isAfter(last)) continue;
            var policies = repository.findWeekReview(contractId, review.toString());
            var p = policies.isEmpty() ? new HashMap<String, Object>() : policies.getFirst();
            long sec = repository.sumConfirmedBillableSeconds(
                contractId,
                review.toString(),
                review.plusDays(6).toString()
            );
            var actualHours = hours(sec);
            boolean shortfall =
                    actualHours.compareTo(BigDecimal.valueOf(integer(contract, "minimum_hours"))) <
                    0,
                over =
                    actualHours.compareTo(BigDecimal.valueOf(integer(contract, "maximum_hours"))) >
                    0,
                partial = review.isBefore(first) || review.plusDays(6).isAfter(last);
            String status = !text(p, "exception_ref").isEmpty()
                ? "Exception agreed in writing"
                : over
                  ? text(p, "overtime_ref").isEmpty()
                      ? "Above maximum — written approval required"
                      : "Above maximum — approval recorded"
                  : shortfall
                    ? p.containsKey("available") && integer(p, "available") == 0
                        ? "Below minimum — contractor unavailable"
                        : "Below minimum — work not assigned"
                    : "Within expected range";
            var reviews = new LinkedHashMap<String, Object>(p);
            reviews.put("week", review.toString());
            reviews.put("hours", actualHours);
            reviews.put("minimum", integer(contract, "minimum_hours"));
            reviews.put("maximum", integer(contract, "maximum_hours"));
            reviews.put(
                "difference",
                actualHours.subtract(BigDecimal.valueOf(integer(contract, "minimum_hours")))
            );
            reviews.put("status", status);
            reviews.put("partial", partial);
            reviews.put(
                "unresolved",
                (over && text(p, "overtime_ref").isEmpty() && text(p, "exception_ref").isEmpty()) ||
                    ((shortfall || partial) &&
                        text(p, "exception_ref").isEmpty() &&
                        text(p, "acknowledgement").isEmpty())
            );
            result.add(reviews);
        }
        return result;
    }

    @Transactional
    public void saveWeek(Map<String, Object> command) {
        require(
            Set.of("0", "1").contains(text(command, "available")),
            "Availability must be 0 or 1"
        );
        var contract = clients.contract(integer(command, "contract_id"));
        require(
            weekStart(LocalDate.parse(text(command, "week")), integer(contract, "week_start"))
                .toString()
                .equals(text(command, "week")),
            "Date must be the configured week start"
        );
        repository.saveWeekReview(
            integer(command, "contract_id"),
            text(command, "week"),
            integer(command, "available"),
            text(command, "exception_ref"),
            text(command, "overtime_ref"),
            text(command, "acknowledgement")
        );
        audit.record("weekly policy saved", "contract:" + integer(command, "contract_id"), command);
    }
}
