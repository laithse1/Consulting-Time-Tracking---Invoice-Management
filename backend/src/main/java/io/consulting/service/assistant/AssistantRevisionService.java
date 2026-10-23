package io.consulting.service.assistant;

import static io.consulting.domain.DomainValues.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.consulting.config.ApplicationProperties;
import io.consulting.domain.assistant.WorkDescriptionParser;
import io.consulting.integration.ai.HttpJsonProviderClient;
import io.consulting.repository.AssistantRepository;
import io.consulting.service.WorkspaceService;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Conversational corrections propose edits; this class never writes time entries or invoices. */
@Service
public class AssistantRevisionService {

    private final WorkAssistantService assistant;
    private final AssistantRepository repository;
    private final AssistantRevisionStore store;
    private final WorkspaceService workspace;
    private final HttpJsonProviderClient providers;
    private final ApplicationProperties properties;
    private final ObjectMapper json;

    public AssistantRevisionService(
        WorkAssistantService assistant,
        AssistantRepository repository,
        AssistantRevisionStore store,
        WorkspaceService workspace,
        HttpJsonProviderClient providers,
        ApplicationProperties properties,
        ObjectMapper json
    ) {
        this.assistant = assistant;
        this.repository = repository;
        this.store = store;
        this.workspace = workspace;
        this.providers = providers;
        this.properties = properties;
        this.json = json;
    }

    public Map<String, Object> revise(String id, Map<String, Object> request) {
        var row = assistant.planRow(id);
        require(
            text(row, "status").equals("proposed"),
            "This plan is applied. Correct saved work in Time Tracking before finalizing the invoice."
        );
        int version = integer(request, "version");
        require(
            version == integer(row, "version"),
            "Plan changed. Reload it before requesting corrections."
        );
        String instruction = text(request, "instruction").strip();
        require(
            !instruction.isBlank() &&
                instruction.length() <= properties.assistant().maxSourceCharacters(),
            "Enter a correction within the configured source limit"
        );
        require(
            repository.findRevisions(id).size() < properties.assistant().maxRevisions(),
            "Conversation limit reached. Use the review fields or start a new plan."
        );
        var actions = (List<Map<String, Object>>) workspace
            .decode(text(row, "plan_json"))
            .get("actions");
        var contract = workspace.contract(integer(row, "contract_id"));
        var projects = repository.findProjects(integer(contract, "client_id"));
        String engine = text(request, "provider_id");
        JsonNode suggestion;
        if (engine.isBlank()) suggestion = local(instruction);
        else {
            require(
                Boolean.TRUE.equals(request.get("provider_consent")),
                "Confirm sending the plan and correction to the selected provider"
            );
            suggestion = providers.revise(
                engine,
                workspace.encode(
                    Map.of(
                        "original_source",
                        text(row, "source"),
                        "actions",
                        actions,
                        "correction",
                        instruction,
                        "reference_date",
                        text(row, "reference_date"),
                        "projects",
                        projects
                            .stream()
                            .map(p -> text(p, "name"))
                            .toList(),
                        "categories",
                        WorkDescriptionParser.CATEGORIES,
                        "conversation",
                        repository.findRevisions(id)
                    )
                )
            );
        }
        String reply = applyChanges(
            actions,
            suggestion,
            instruction,
            LocalDate.parse(text(row, "reference_date")),
            projects
        );
        String document = workspace.encode(Map.of("actions", actions));
        store.save(
            id,
            version,
            instruction,
            engine.isBlank() ? "local-rules" : engine,
            reply,
            text(row, "plan_json"),
            document,
            properties.assistant().maxRevisions()
        );
        return assistant.unpack(assistant.planRow(id));
    }

    private JsonNode local(String instruction) {
        var match = Pattern.compile(
            "(?is)^task\\s+(\\d+)\\s+(description|date|duration|project|category|billing|exclude|include)\\s*:\\s*(.+)$"
        ).matcher(instruction);
        var result = json.createObjectNode();
        var changes = result.putArray("changes");
        if (!match.matches()) {
            result.put(
                "clarification",
                "For local corrections use Task 1 description: …, Task 1 date: 2026-09-15, Task 1 duration: 90m, Task 1 project: Audiom, Task 1 billing: nonbillable, or Task 1 exclude: yes. Select a configured provider for conversational phrasing."
            );
            return result;
        }
        var change = changes.addObject().put("task", Integer.parseInt(match.group(1)));
        switch (match.group(2).toLowerCase(Locale.ROOT)) {
            case "date" -> change.put("update_date", true);
            case "duration" -> change.put("update_duration", true);
            case "exclude", "include" -> {
                require(
                    match.group(3).strip().equalsIgnoreCase("yes"),
                    "Confirm inclusion or exclusion with yes"
                );
                change.put("skip", match.group(2).equalsIgnoreCase("exclude"));
            }
            case "billing" -> {
                require(
                    Set.of("billable", "nonbillable").contains(
                        match.group(3).strip().toLowerCase(Locale.ROOT)
                    ),
                    "Use billable or nonbillable"
                );
                change.put("billable", match.group(3).strip().equalsIgnoreCase("billable"));
            }
            default -> change.put(match.group(2).toLowerCase(Locale.ROOT), match.group(3).strip());
        }
        return result;
    }

    private String applyChanges(
        List<Map<String, Object>> actions,
        JsonNode result,
        String instruction,
        LocalDate reference,
        List<Map<String, Object>> projects
    ) {
        require(result != null && result.isObject(), "Correction response must be a JSON object");
        result
            .fieldNames()
            .forEachRemaining(key ->
                require(
                    Set.of("changes", "clarification").contains(key),
                    "Unsupported correction response field: " + key
                )
            );
        require(
            !result.has("clarification") || result.path("clarification").isTextual(),
            "Clarification must be text"
        );
        var changes = result.path("changes");
        require(
            changes.isArray() && changes.size() <= properties.assistant().maxActions(),
            "Provider must return a bounded changes array"
        );
        String clarification = result.path("clarification").asText("");
        require(clarification.length() <= 2000, "Clarification is too long");
        if (!clarification.isBlank()) return clarification; // Questions never partially change the plan.
        require(
            !changes.isEmpty(),
            "No correction was proposed. Identify a task and the requested change."
        );
        var seen = new HashSet<Integer>();
        var facts = WorkDescriptionParser.work(instruction, reference, null, null, projects);
        for (var change : changes) {
            require(change.isObject(), "Each correction must be an object");
            change
                .fieldNames()
                .forEachRemaining(key ->
                    require(
                        Set.of(
                            "task",
                            "description",
                            "update_date",
                            "update_duration",
                            "project",
                            "category",
                            "billable",
                            "skip"
                        ).contains(key),
                        "Unsupported correction field: " + key
                    )
                );
            require(
                change.path("task").isIntegralNumber() && change.path("task").canConvertToInt(),
                "Task number must be an integer"
            );
            int index = change.path("task").intValue() - 1;
            require(
                index >= 0 && index < actions.size() && seen.add(index),
                "Identify each existing task once"
            );
            var action = actions.get(index);
            require(
                text(action, "type").equals("log_work"),
                "Only work tasks can be revised in conversation. Edit invoice periods directly."
            );
            if (change.has("description")) {
                require(change.path("description").isTextual(), "Description must be text");
                String description = change.path("description").asText().strip();
                require(
                    !description.isBlank() && description.length() <= 2000,
                    "Description must contain 1–2,000 characters"
                );
                action.put("description", description);
                action.put("wording_suggestion", true);
            }
            if (flag(change, "update_date")) {
                require(
                    !Pattern.compile("(?i)\\b(not|don't|do not|isn't|wasn't)\\b")
                        .matcher(instruction)
                        .find(),
                    "State the actual date positively. No date was changed."
                );
                require(
                    !text(facts, "work_date").isBlank(),
                    "Supply one explicit actual date, such as 2026-09-15. No date was changed."
                );
                action.put("work_date", text(facts, "work_date"));
            }
            if (flag(change, "update_duration")) {
                long durationParts = Pattern.compile(
                    "(?i)\\d+(?:\\.\\d+)?\\s*(?:hours?|hrs?|h|minutes?|mins?|m)\\b"
                )
                    .matcher(instruction)
                    .results()
                    .count();
                require(
                    durationParts < 2 ||
                        !Pattern.compile("(?i)\\b(from|instead of|rather than|previously|was)\\b")
                            .matcher(instruction)
                            .find(),
                    "State only the corrected actual duration, for example Task 1 duration: 90m."
                );
                require(
                    integer(facts, "seconds") > 0,
                    "Supply one unambiguous numeric actual duration, such as 90m. No time was changed."
                );
                action.put("seconds", integer(facts, "seconds"));
                // A duration correction must not silently retain a stale clock start.
                action.put("start_time", text(facts, "start_time"));
            }
            if (change.has("project")) {
                var project = projects
                    .stream()
                    .filter(p -> text(p, "name").equalsIgnoreCase(change.path("project").asText()))
                    .findFirst();
                require(project.isPresent(), "Choose an existing project for this client");
                action.put("project_id", integer(project.get(), "id"));
            }
            if (change.has("category")) {
                require(
                    WorkDescriptionParser.CATEGORIES.contains(change.path("category").asText()),
                    "Choose an existing work category"
                );
                action.put("category", change.path("category").asText());
            }
            if (change.has("billable")) action.put("billable", flag(change, "billable") ? 1 : 0);
            if (change.has("skip")) action.put("skip", flag(change, "skip"));
            action.put("draft", 1);
            action.put(
                "questions",
                List.of(
                    "Correction proposed. Review the wording, date, project, and actual duration, then confirm this task for billing."
                )
            );
        }
        return (
            "Updated " +
            changes.size() +
            (changes.size() == 1 ? " task." : " tasks.") +
            " Review the changes and confirm completed work before applying."
        );
    }

    private boolean flag(JsonNode node, String key) {
        if (!node.has(key)) return false;
        require(node.path(key).isBoolean(), key + " must be a boolean");
        return node.path(key).booleanValue();
    }
}
