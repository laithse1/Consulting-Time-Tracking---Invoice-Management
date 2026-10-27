package io.consulting.service.assistant;

import static io.consulting.domain.DomainValues.integer;
import static io.consulting.domain.DomainValues.require;
import static io.consulting.domain.DomainValues.text;

import io.consulting.config.ApplicationProperties;
import io.consulting.domain.assistant.LocalWorkPlanParser;
import io.consulting.domain.assistant.StructuredWorkIntake;
import io.consulting.domain.assistant.WorkDescriptionParser;
import io.consulting.integration.ai.HttpJsonProviderClient;
import io.consulting.repository.AssistantRepository;
import io.consulting.service.WorkspaceService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkAssistantService {

    private final WorkspaceService workspace;
    private final AssistantRepository repository;
    private final HttpJsonProviderClient providers;
    private final ApplicationProperties properties;
    private final AssistantReceiptService receipts;
    private final AssistantReportingService reporting;

    public WorkAssistantService(
        WorkspaceService workspace,
        AssistantRepository repository,
        HttpJsonProviderClient providers,
        ApplicationProperties properties,
        AssistantReceiptService receipts,
        AssistantReportingService reporting
    ) {
        this.workspace = workspace;
        this.repository = repository;
        this.providers = providers;
        this.properties = properties;
        this.receipts = receipts;
        this.reporting = reporting;
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
            !text(row, "status").equals("applied") || text(row, "results_json").isEmpty()
                ? List.of()
                : workspace.decode(text(row, "results_json")).get("results")
        );
        out.remove("results_json");
        out.put("revisions", repository.findRevisions(text(row, "id")));
        out.put(
            "review",
            reporting.review(
                integer(row, "contract_id"),
                (List<Map<String, Object>>) out.get("actions")
            )
        );
        return out;
    }

    public Map<String, Object> getPlan(String id) {
        return unpack(planRow(id));
    }

    /** Remove from saved history without removing linked ledger facts or billing safeguards. */
    @Transactional
    public void deleteConversation(String id, int version, boolean confirmed) {
        require(confirmed, "Confirm deleting this conversation");
        var row = planRow(id);
        if (!text(row, "deleted_at").isBlank()) return;
        require(
            repository.deleteConversation(id, version),
            "Conversation changed; reload before deleting it"
        );
        workspace.audit(
            "assistant conversation deleted",
            "assistant:" + id,
            Map.of("version", version, "linked_records_preserved", true)
        );
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
        String inputMode = text(command, "input_mode");
        if (inputMode.isBlank()) inputMode = "auto";
        List<Map<String, Object>> actions;
        var structured = inputMode.equals("narrative")
            ? List.<Map<String, Object>>of()
            : StructuredWorkIntake.parse(source, reference, projects);
        if (!structured.isEmpty() && !inputMode.equals("lines")) {
            actions = structured;
            engine = ""; // Explicit tables need no model request or network consent.
            var invoice = StructuredWorkIntake.invoice(source, reference);
            if (invoice != null) actions.add(invoice);
        } else if (engine.isBlank()) actions = LocalWorkPlanParser.parse(
            source,
            reference,
            projects,
            inputMode,
            properties.assistant().maxActions()
        );
        else {
            require(
                Boolean.TRUE.equals(command.get("provider_consent")),
                "Confirm sending this text to the selected provider"
            );
            actions = new ArrayList<>();
            for (var segment : providers.extract(engine, source, projects)) {
                if (segment.getOrDefault("kind", "work").equals("context")) {
                    actions.add(
                        new LinkedHashMap<>(
                            Map.of(
                                "type",
                                "save_note",
                                "description",
                                "Retain references, instructions, or unperformed work as nonbillable context.",
                                "source_text",
                                segment.get("source"),
                                "questions",
                                List.of(
                                    "These notes are context, not completed work. They do not generate time entries or charges."
                                )
                            )
                        )
                    );
                    continue;
                }
                String span = segment.get("source");
                // Legacy profiles may omit kind for a command. Recognize only a single explicit
                // command line locally; multiline work evidence remains one proposed work item.
                List<Map<String, Object>> parsed;
                String kind = segment.getOrDefault("kind", "auto");
                if (
                    kind.equals("command") ||
                    (kind.equals("auto") && span.strip().lines().count() == 1)
                ) {
                    parsed = WorkDescriptionParser.parse(
                        span,
                        reference,
                        projects,
                        properties.assistant().maxActions()
                    );
                } else parsed = List.of(
                    WorkDescriptionParser.providerWork(span, reference, projects)
                );
                if (kind.equals("command")) require(
                    parsed.stream().noneMatch(action -> text(action, "type").equals("log_work")),
                    "Provider returned an unsupported command. No actions were applied."
                );
                for (var action : parsed) {
                    if (!text(action, "type").equals("log_work")) {
                        actions.add(action);
                        continue;
                    }
                    if (integer(action, "project_id") == 0) projects
                        .stream()
                        .filter(p -> text(p, "name").equalsIgnoreCase(segment.get("project")))
                        .findFirst()
                        .ifPresent(p -> action.put("project_id", integer(p, "id")));
                    if (
                        WorkDescriptionParser.CATEGORIES.contains(segment.get("category"))
                    ) action.put("category", segment.get("category"));
                    action.put("draft", 1);
                    action.put("provider_suggestion", true);
                    if (parsed.size() == 1 && !segment.get("description").isBlank()) {
                        action.put("description", segment.get("description"));
                        action.put("wording_suggestion", true);
                    }
                    actions.add(action);
                }
            }
        }
        if (
            Boolean.TRUE.equals(command.get("prepare_invoice")) &&
            actions.stream().anyMatch(a -> text(a, "type").equals("log_work")) &&
            actions.stream().noneMatch(a -> text(a, "type").equals("draft_invoice"))
        ) {
            var from = LocalDate.parse(text(command, "period_start"));
            var to = LocalDate.parse(text(command, "period_end"));
            require(
                !to.isBefore(from) && to.toEpochDay() - from.toEpochDay() <= 366,
                "Invalid invoice period"
            );
            actions.add(
                new LinkedHashMap<>(
                    Map.of(
                        "type",
                        "draft_invoice",
                        "description",
                        "Prepare invoice from reviewed eligible work",
                        "period_start",
                        from.toString(),
                        "period_end",
                        to.toString(),
                        "require_complete_work",
                        true,
                        "skip",
                        !structured.isEmpty() && !inputMode.equals("lines")
                    )
                )
            );
        }
        // Draft/report operations run after work entries even when mentioned first in the notes.
        actions.sort(
            java.util.Comparator.comparingInt(a ->
                java.util.Set.of("draft_invoice", "report", "compliance").contains(text(a, "type"))
                    ? 1
                    : 0
            )
        );
        if (actions.size() > properties.assistant().maxActions()) {
            if (engine.isBlank() && inputMode.equals("auto")) actions =
                LocalWorkPlanParser.narrative(source);
            else throw new WorkDescriptionParser.ActionLimitExceeded(
                properties.assistant().maxActions()
            );
        }
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
                !a.containsKey("skip") || a.get("skip") instanceof Boolean,
                "Task inclusion must be a boolean"
            );
            for (String field : List.of(
                "_entry_id",
                "_entry_baseline",
                "_invoice_id",
                "source_text",
                "requires_resolution",
                "proposed_hours",
                "allocation_start",
                "allocation_end",
                "work_state"
            ))
                require(
                    java.util.Objects.equals(a.get(field), previous.get(i).get(field)),
                    "Saved record references cannot be changed"
                );
            if (integer(a, "_invoice_id") > 0) for (String field : List.of(
                "period_start",
                "period_end"
            ))
                require(
                    text(a, field).equals(text(previous.get(i), field)),
                    "The saved invoice period cannot change. Create a new invoice draft for a different period."
                );
            require(
                text(a, "type").equals(text(previous.get(i), "type")),
                "Action type cannot be changed"
            );
            require(workspace.encode(a).length() <= 40000, "Action is too large");
            if (Boolean.TRUE.equals(a.get("requires_resolution"))) require(
                a.get("resolved") instanceof Boolean,
                "Question resolution must be a boolean"
            );
            if (text(a, "type").equals("log_work")) {
                if (a.get("seconds") == null) a.put("seconds", 0);
                require(
                    java.util.Set.of("0", "1").contains(text(a, "draft")) &&
                        java.util.Set.of("0", "1").contains(text(a, "billable")),
                    "Billing and draft flags must be 0 or 1"
                );
                require(
                    text(a, "seconds").matches("\\d{1,5}"),
                    "Actual duration must be an integer number of seconds"
                );
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
        require(
            repository.updateProposedPlan(
                id,
                integer(command, "version"),
                workspace.encode(Map.of("actions", actions))
            ),
            "Plan changed in another window; reload it before applying"
        );
        return unpack(planRow(id));
    }

    /** Creates the review document independently of logging or confirming work. */
    @Transactional
    public Map<String, Object> invoiceDraft(String id, int version, String from, String to) {
        var row = planRow(id);
        require(
            integer(row, "version") == version &&
                java.util.Set.of("proposed", "applied").contains(text(row, "status")),
            "Reload the saved plan before creating its invoice draft"
        );
        var actions = (List<Map<String, Object>>) workspace
            .decode(text(row, "plan_json"))
            .get("actions");
        var action = actions
            .stream()
            .filter(a -> text(a, "type").equals("draft_invoice"))
            .findFirst()
            .orElse(null);
        if (action != null && integer(action, "_invoice_id") > 0) return Map.of(
            "plan",
            unpack(row),
            "preview",
            workspace.preview(integer(action, "_invoice_id"))
        );
        if (action == null) {
            require(
                actions.size() < properties.assistant().maxActions(),
                "Action limit reached; create a draft from Invoices instead"
            );
            action = new LinkedHashMap<>(
                Map.of(
                    "type",
                    "draft_invoice",
                    "description",
                    "Prepare invoice from reviewed work",
                    "skip",
                    true
                )
            );
            actions.add(action);
        }
        boolean applied = text(row, "status").equals("applied");
        var results = text(row, "results_json").isBlank()
            ? new ArrayList<Map<String, Object>>()
            : new ArrayList<>(
                  (List<Map<String, Object>>) workspace
                      .decode(text(row, "results_json"))
                      .get("results")
              );
        int cursor = 0;
        for (var result : results) {
            if (!result.containsKey("action_index")) {
                while (
                        cursor < actions.size() &&
                        (!text(actions.get(cursor), "type").equals(text(result, "type")) ||
                            (Boolean.TRUE.equals(actions.get(cursor).get("skip")) &&
                                integer(actions.get(cursor), "_entry_id") == 0))
                    )
                    cursor++;
                require(
                    cursor < actions.size(),
                    "Saved receipt does not match its plan; review the records in Invoices"
                );
                result.put("action_index", cursor);
            }
            cursor = integer(result, "action_index") + 1;
        }
        int invoiceIndex = actions.indexOf(action);
        int existingInvoice = results
            .stream()
            .filter(
                result ->
                    text(result, "type").equals("draft_invoice") &&
                    integer(result, "action_index") == invoiceIndex
            )
            .map(result -> integer((Map<String, Object>) result.get("invoice"), "id"))
            .findFirst()
            .orElse(0);
        if (
            existingInvoice > 0 &&
            text(workspace.one("invoices", existingInvoice), "state").equals("Deleted")
        ) existingInvoice = 0;
        var preview =
            existingInvoice > 0
                ? workspace.preview(existingInvoice)
                : workspace.draft(
                      Map.of(
                          "contract_id",
                          integer(row, "contract_id"),
                          "period_start",
                          from,
                          "period_end",
                          to,
                          "adjustment",
                          "0"
                      )
                  );
        var invoice = (Map<String, Object>) preview.get("invoice");
        action.put("period_start", text(invoice, "period_start"));
        action.put("period_end", text(invoice, "period_end"));
        action.put("_invoice_id", integer((Map<String, Object>) preview.get("invoice"), "id"));
        if (applied) {
            results.removeIf(
                result ->
                    text(result, "type").equals("draft_invoice") &&
                    integer(result, "action_index") == invoiceIndex
            );
            results.add(
                new LinkedHashMap<>(
                    Map.of(
                        "type",
                        "draft_invoice",
                        "action_index",
                        invoiceIndex,
                        "invoice",
                        invoice,
                        "preview",
                        preview,
                        "warnings",
                        preview.get("warnings"),
                        "total",
                        preview.get("total")
                    )
                )
            );
        }
        require(
            repository.bindInvoiceDraft(
                id,
                version,
                workspace.encode(Map.of("actions", actions)),
                applied ? workspace.encode(Map.of("results", results)) : text(row, "results_json")
            ),
            "Plan changed; reload before creating its invoice"
        );
        workspace.audit(
            "assistant invoice draft created",
            "assistant:" + id,
            Map.of("invoice_id", action.get("_invoice_id"))
        );
        return Map.of(
            "plan",
            unpack(planRow(id)),
            "preview",
            workspace.preview(integer(action, "_invoice_id"))
        );
    }

    /** Adds a blank daily portion without duplicating allocated hours or saved ledger targets. */
    @Transactional
    public Map<String, Object> split(String id, int version, int index) {
        var row = planRow(id);
        require(
            text(row, "status").equals("proposed") && integer(row, "version") == version,
            "Plan changed or is already applied; reload before splitting a task"
        );
        var actions = (List<Map<String, Object>>) workspace
            .decode(text(row, "plan_json"))
            .get("actions");
        require(index >= 0 && index < actions.size(), "Choose an existing task");
        require(
            actions.size() < properties.assistant().maxActions(),
            "The action limit is reached; use another message for additional daily entries"
        );
        var original = actions.get(index);
        require(
            text(original, "type").equals("log_work") && integer(original, "_entry_id") == 0,
            "Split only work that has not been saved to the time ledger"
        );
        var portion = new LinkedHashMap<>(original);
        for (String field : List.of(
            "proposed_hours",
            "_entry_id",
            "_entry_baseline",
            "_invoice_id"
        ))
            portion.remove(field);
        portion.put("seconds", 0);
        portion.put("work_date", "");
        portion.put("start_time", "");
        portion.put("draft", 1);
        portion.put("skip", false);
        portion.put(
            "questions",
            List.of(
                "Additional daily portion of the original task. Enter this day's actual date and duration; the allocation has not been duplicated. Reconcile all portions against actual work."
            )
        );
        actions.add(portion); // Existing action indices and receipt bindings remain stable.
        require(
            repository.updateProposedPlan(
                id,
                version,
                workspace.encode(Map.of("actions", actions))
            ),
            "Plan changed in another window; reload before splitting"
        );
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
        // Check saved targets before any mutations. All writes still share this transaction.
        receipts.validateTargets(actions);
        boolean preparingInvoice = actions
            .stream()
            .anyMatch(
                a -> text(a, "type").equals("draft_invoice") && !Boolean.TRUE.equals(a.get("skip"))
            );
        if (preparingInvoice) for (var action : actions)
            require(
                !Boolean.TRUE.equals(action.get("requires_resolution")) ||
                    Boolean.TRUE.equals(action.get("resolved")),
                "Resolve the allocation and billing questions before preparing an invoice."
            );
        for (var action : actions)
            if (
                text(action, "type").equals("log_work") &&
                !Boolean.TRUE.equals(action.get("skip")) &&
                integer(action, "draft") == 0 &&
                !text(action, "work_date").isBlank()
            ) require(
                !LocalDate.parse(text(action, "work_date")).isAfter(
                    LocalDate.parse(text(row, "reference_date"))
                ),
                "Future work cannot be confirmed as performed at this plan's reference date. Create a new plan after the work is performed."
            );
        if (preparingInvoice) for (var action : actions) {
            if (
                text(action, "type").equals("log_work") && !Boolean.TRUE.equals(action.get("skip"))
            ) {
                require(
                    integer(action, "draft") == 0 &&
                        !text(action, "work_date").isBlank() &&
                        integer(action, "seconds") > 0 &&
                        integer(action, "project_id") > 0,
                    "Confirm the actual date, duration, and project for each task before preparing its invoice, or exclude that task."
                );
                if (integer(action, "billable") == 1) require(
                    actions
                        .stream()
                        .anyMatch(
                            invoice ->
                                text(invoice, "type").equals("draft_invoice") &&
                                !Boolean.TRUE.equals(invoice.get("skip")) &&
                                !text(invoice, "period_start").isBlank() &&
                                !text(invoice, "period_end").isBlank() &&
                                text(action, "work_date").compareTo(
                                    text(invoice, "period_start")
                                ) >= 0 &&
                                text(action, "work_date").compareTo(text(invoice, "period_end")) <=
                                    0
                        ),
                    "A billable task falls outside the selected invoice period. Correct the period or exclude that task from this plan."
                );
            }
        }
        // Splitting appends to preserve receipt identities; execute all work before invoice/report snapshots.
        var executionOrder = java.util.stream.IntStream.range(0, actions.size())
            .boxed()
            .sorted(
                java.util.Comparator.comparingInt(index ->
                    java.util.Set.of("draft_invoice", "report", "compliance").contains(
                        text(actions.get(index), "type")
                    )
                        ? 1
                        : 0
                )
            )
            .toList();
        for (int actionIndex : executionOrder) {
            var a = actions.get(actionIndex);
            if (
                Boolean.TRUE.equals(a.get("skip")) &&
                integer(a, "_entry_id") == 0 &&
                integer(a, "_invoice_id") == 0
            ) continue;
            String type = text(a, "type");
            var result = new LinkedHashMap<String, Object>();
            result.put("type", type);
            result.put("action_index", actionIndex);
            switch (type) {
                case "save_note" -> result.put(
                    "note",
                    "Original notes retained in this conversation. This note action did not create time entries or charges."
                );
                case "log_work" -> {
                    var entry = new HashMap<>(a);
                    entry.remove("id");
                    if (integer(a, "_entry_id") > 0) entry.put("id", integer(a, "_entry_id"));
                    entry.put("contract_id", contractId);
                    entry.put(
                        "draft",
                        Boolean.TRUE.equals(a.get("skip")) ||
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
                    var p =
                        integer(a, "_invoice_id") > 0
                            ? workspace.preview(integer(a, "_invoice_id"))
                            : workspace.draft(command);
                    result.put("invoice", p.get("invoice"));
                    result.put("warnings", p.get("warnings"));
                    result.put("total", p.get("total"));
                    result.put("preview", p);
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
        return reporting.report(contractId, from, to);
    }

    public Map<String, Object> reopen(String id, int version) {
        return unpack(receipts.reopen(id, version));
    }
}
