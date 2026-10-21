package io.consulting.service.assistant;

import static io.consulting.domain.DomainValues.charge;
import static io.consulting.domain.DomainValues.hours;
import static io.consulting.domain.DomainValues.integer;
import static io.consulting.domain.DomainValues.money;
import static io.consulting.domain.DomainValues.require;
import static io.consulting.domain.DomainValues.text;

import io.consulting.config.ApplicationProperties;
import io.consulting.domain.assistant.WorkDescriptionParser;
import io.consulting.integration.ai.HttpJsonProviderClient;
import io.consulting.repository.AssistantRepository;
import io.consulting.service.WorkspaceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkAssistantService {

    private final WorkspaceService workspace;
    private final AssistantRepository repository;
    private final HttpJsonProviderClient providers;
    private final ApplicationProperties properties;

    public WorkAssistantService(
        WorkspaceService workspace,
        AssistantRepository repository,
        HttpJsonProviderClient providers,
        ApplicationProperties properties
    ) {
        this.workspace = workspace;
        this.repository = repository;
        this.providers = providers;
        this.properties = properties;
    }

    public Map<String, Object> history(int contractId) {
        workspace.contract(contractId);
        var drafts = repository.findDraft(contractId);
        return Map.of(
            "draft",
            drafts.isEmpty() ? Map.of("source", "") : drafts.getFirst(),
            "plans",
            repository
                .findPlans(contractId, properties.assistant().historyLimit())
                .stream()
                .map(this::unpack)
                .toList(),
            "providers",
            providers.available(),
            "limits",
            Map.of(
                "sourceCharacters",
                properties.assistant().maxSourceCharacters(),
                "actions",
                properties.assistant().maxActions(),
                "history",
                properties.assistant().historyLimit()
            )
        );
    }

    Map<String, Object> unpack(Map<String, Object> row) {
        var out = new LinkedHashMap<>(row);
        out.put("actions", workspace.decode(text(row, "plan_json")).get("actions"));
        out.remove("plan_json");
        out.put(
            "results",
            text(row, "results_json").isEmpty()
                ? List.of()
                : workspace.decode(text(row, "results_json")).get("results")
        );
        out.remove("results_json");
        return out;
    }

    Map<String, Object> planRow(String id) {
        var rows = repository.findPlan(id);
        require(!rows.isEmpty(), "Assistant plan not found");
        return rows.getFirst();
    }

    @Transactional
    public void saveDraft(int contractId, String source) {
        workspace.contract(contractId);
        require(
            source.length() <= properties.assistant().maxSourceCharacters(),
            "Use at most 10,000 characters"
        );
        repository.saveDraft(contractId, source);
    }

    // Network extraction is outside a DB transaction; controller saves the draft before this call.
    public Map<String, Object> propose(Map<String, Object> command) {
        int contractId = integer(command, "contract_id");
        var contract = workspace.contract(contractId);
        String source = text(command, "source");
        require(
            !source.isBlank() && source.length() <= properties.assistant().maxSourceCharacters(),
            "Describe your work using 1–10,000 characters"
        );
        LocalDate reference = LocalDate.parse(text(command, "reference_date"));
        var projects = repository.findProjects(integer(contract, "client_id"));
        String engine = text(command, "provider_id");
        List<Map<String, Object>> actions;
        if (engine.isBlank()) actions = WorkDescriptionParser.parse(source, reference, projects);
        else {
            require(
                Boolean.TRUE.equals(command.get("provider_consent")),
                "Confirm sending this text to the selected provider"
            );
            actions = new ArrayList<>();
            for (var segment : providers.extract(engine, source, projects)) {
                var action = WorkDescriptionParser.work(
                    segment.get("source"),
                    reference,
                    null,
                    null,
                    projects
                );
                if (integer(action, "project_id") == 0) projects
                    .stream()
                    .filter(p -> text(p, "name").equalsIgnoreCase(segment.get("project")))
                    .findFirst()
                    .ifPresent(p -> action.put("project_id", integer(p, "id")));
                if (WorkDescriptionParser.CATEGORIES.contains(segment.get("category"))) action.put(
                    "category",
                    segment.get("category")
                );
                action.put("draft", 1);
                action.put("provider_suggestion", true);
                actions.add(action);
            }
        }
        require(
            actions.size() <= properties.assistant().maxActions(),
            "Message exceeds the configured action limit"
        );
        // A foreign client's name in the source requires an explicit manual correction of the plan.
        for (var client : repository.findOtherClients(integer(contract, "client_id")))
            if (
                source
                    .toLowerCase(Locale.ROOT)
                    .contains(text(client, "name").toLowerCase(Locale.ROOT))
            ) for (var action : actions)
                if (text(action, "type").equals("log_work")) {
                    action.put("draft", 1);
                    action.put(
                        "questions",
                        List.of(
                            "The text names another client. Confirm the active contract and assignment before billing."
                        )
                    );
                }
        String id = UUID.randomUUID().toString();
        repository.createPlan(
            id,
            contractId,
            source,
            reference.toString(),
            engine.isBlank() ? "local-rules" : engine,
            workspace.encode(Map.of("actions", actions))
        );
        return unpack(planRow(id));
    }

    @Transactional
    public Map<String, Object> update(String id, Map<String, Object> command) {
        var row = planRow(id);
        require(text(row, "status").equals("proposed"), "This plan is already applied");
        require(
            integer(row, "version") == integer(command, "version"),
            "Plan changed in another window; reload it before applying"
        );
        require(command.get("actions") instanceof List<?>, "Actions are required");
        var actions = (List<Map<String, Object>>) command.get("actions");
        var previous = (List<Map<String, Object>>) workspace
            .decode(text(row, "plan_json"))
            .get("actions");
        require(
            actions.size() == previous.size(),
            "Do not add/remove actions in an existing plan; create a new message"
        );
        for (int i = 0; i < actions.size(); i++) {
            var a = actions.get(i);
            require(
                text(a, "type").equals(text(previous.get(i), "type")),
                "Action type cannot be changed"
            );
            require(workspace.encode(a).length() <= 20000, "Action is too large");
            if (text(a, "type").equals("log_work")) {
                require(
                    integer(a, "seconds") >= 0 && integer(a, "seconds") <= 86400,
                    "Actual duration must be between zero and 24 hours"
                );
                if (
                    text(a, "work_date").isEmpty() ||
                    integer(a, "seconds") == 0 ||
                    integer(a, "project_id") == 0
                ) a.put("draft", 1);
            }
        }
        repository.updatePlan(id, workspace.encode(Map.of("actions", actions)));
        return unpack(planRow(id));
    }

    @Transactional
    public Map<String, Object> apply(String id, Map<String, Object> request) {
        var row = planRow(id);
        require(
            Boolean.TRUE.equals(request.get("confirmed")),
            "Review and explicitly confirm this action plan"
        );
        if (text(row, "status").equals("applied")) return unpack(row);
        require(
            integer(row, "version") == integer(request, "version"),
            "Plan changed in another window; reload it before applying"
        );
        int contractId = integer(row, "contract_id");
        var actions = (List<Map<String, Object>>) workspace
            .decode(text(row, "plan_json"))
            .get("actions");
        var results = new ArrayList<Map<String, Object>>();
        for (var a : actions) {
            String type = text(a, "type");
            var result = new LinkedHashMap<String, Object>();
            result.put("type", type);
            switch (type) {
                case "log_work" -> {
                    var entry = new HashMap<>(a);
                    entry.remove("id");
                    entry.put("contract_id", contractId);
                    entry.put(
                        "draft",
                        text(a, "work_date").isEmpty() ||
                            integer(a, "seconds") == 0 ||
                            integer(a, "project_id") == 0
                            ? 1
                            : integer(a, "draft")
                    );
                    entry.putIfAbsent("billable", 1);
                    entry.putIfAbsent("category", "Other");
                    result.put("entry", workspace.saveEntry(entry));
                }
                case "start_timer" -> {
                    var command = new HashMap<>(a);
                    command.put("contract_id", contractId);
                    result.put("timer", workspace.timer("start", command));
                }
                case "pause_timer", "resume_timer", "stop_timer" -> {
                    var timers = repository.findTimerOwner();
                    require(
                        !timers.isEmpty() &&
                            integer(timers.getFirst(), "contract_id") == contractId,
                        "Timer belongs to another contract or no timer exists"
                    );
                    result.put("timer", workspace.timer(type.replace("_timer", ""), Map.of()));
                }
                case "draft_invoice" -> {
                    var command = new HashMap<>(a);
                    command.put("contract_id", contractId);
                    command.put("adjustment", "0");
                    command.remove("replacement_for");
                    var p = workspace.draft(command);
                    result.put("invoice", p.get("invoice"));
                    result.put("warnings", p.get("warnings"));
                    result.put("total", p.get("total"));
                }
                case "report" -> result.put(
                    "report",
                    report(
                        contractId,
                        LocalDate.parse(text(a, "period_start")),
                        LocalDate.parse(text(a, "period_end"))
                    )
                );
                case "compliance" -> result.put(
                    "weeks",
                    workspace.compliance(
                        contractId,
                        LocalDate.parse(text(a, "period_start")),
                        LocalDate.parse(text(a, "period_end"))
                    )
                );
                case "prepare_email" -> {
                    var matches = repository.findInvoice(text(a, "number"), contractId);
                    require(matches.size() == 1, "Invoice number not found for this contract");
                    result.put("email", workspace.email(integer(matches.getFirst(), "id")));
                }
                default -> throw new IllegalArgumentException(
                    "Unsupported assistant action. Financial status changes must use the invoice review screen."
                );
            }
            results.add(result);
        }
        repository.completePlan(id, workspace.encode(Map.of("results", results)));
        workspace.audit(
            "assistant plan applied",
            "assistant:" + id,
            Map.of("count", actions.size(), "contract_id", contractId)
        );
        return unpack(planRow(id));
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
}
