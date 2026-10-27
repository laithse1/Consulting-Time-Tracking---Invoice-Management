package io.consulting.service.assistant;

import static io.consulting.domain.DomainValues.*;

import io.consulting.config.ApplicationProperties;
import io.consulting.repository.AssistantRepository;
import io.consulting.service.WorkspaceService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reopens editable assistant receipts without replaying timers or duplicating ledger records. */
@Service
public class AssistantReceiptService {

    private final AssistantRepository repository;
    private final WorkspaceService workspace;
    private final ApplicationProperties properties;

    public AssistantReceiptService(
        AssistantRepository repository,
        WorkspaceService workspace,
        ApplicationProperties properties
    ) {
        this.repository = repository;
        this.workspace = workspace;
        this.properties = properties;
    }

    private Map<String, Object> planRow(String id) {
        var rows = repository.findPlan(id);
        require(!rows.isEmpty(), "Assistant plan not found");
        return rows.getFirst();
    }

    /** Reopens only editable receipts and binds subsequent application to their existing records. */
    @Transactional
    public Map<String, Object> reopen(String id, int version) {
        var row = planRow(id);
        require(
            !text(row, "results_json").isBlank() && integer(row, "version") == version,
            "Reload the applied plan before reopening it"
        );
        var actions = (List<Map<String, Object>>) workspace
            .decode(text(row, "plan_json"))
            .get("actions");
        require(
            actions.stream().noneMatch(a -> text(a, "type").endsWith("_timer")),
            "Timer receipts cannot be replayed. Correct the stopped work in Time Tracking."
        );
        var results = (List<Map<String, Object>>) workspace
            .decode(text(row, "results_json"))
            .get("results");
        int resultIndex = 0;
        boolean indexed = results.stream().allMatch(result -> result.containsKey("action_index"));
        for (int i = 0; i < actions.size(); i++) {
            var action = actions.get(i);
            Map<String, Object> result;
            if (indexed) {
                final int actionIndex = i;
                result = results
                    .stream()
                    .filter(item -> integer(item, "action_index") == actionIndex)
                    .findFirst()
                    .orElse(null);
                // A formerly excluded, unsaved task may now be pending inclusion.
                if (result == null) {
                    require(
                        integer(action, "_entry_id") == 0 && integer(action, "_invoice_id") == 0,
                        "Receipt is missing a saved record"
                    );
                    continue;
                }
            } else {
                if (
                    Boolean.TRUE.equals(action.get("skip")) && integer(action, "_entry_id") == 0
                ) continue;
                require(
                    resultIndex < results.size(),
                    "Receipt is incomplete. Correct saved work in Time Tracking."
                );
                result = results.get(resultIndex++);
            }
            require(
                text(result, "type").equals(text(action, "type")),
                "Receipt does not match its plan"
            );
            if (text(action, "type").equals("log_work")) {
                int entryId = integer((Map<String, Object>) result.get("entry"), "id");
                var current = workspace.one("entries", entryId);
                require(
                    integer(current, "contract_id") == integer(row, "contract_id") &&
                        !repository.isEntryAllocated(entryId),
                    "Issued work cannot be revised. Use the invoice correction workflow."
                );
                action.putAll(current);
                action.remove("id");
                action.remove("contract_id");
                action.put("_entry_id", entryId);
                action.put("_entry_baseline", fingerprint(current));
            }
            if (text(action, "type").equals("draft_invoice")) {
                int invoiceId = integer((Map<String, Object>) result.get("invoice"), "id");
                var invoice = workspace.one("invoices", invoiceId);
                if (text(invoice, "state").equals("Deleted")) {
                    action.remove("_invoice_id");
                    action.put("skip", true);
                    continue;
                }
                require(
                    integer(invoice, "contract_id") == integer(row, "contract_id") &&
                        text(invoice, "snapshot").isBlank() &&
                        !text(invoice, "state").equals("Deleted"),
                    "Only unfinalized invoices can be revised in this conversation"
                );
                action.put("_invoice_id", invoiceId);
            }
        }
        String document = workspace.encode(Map.of("actions", actions));
        require(
            repository.findRevisions(id).size() < properties.assistant().maxRevisions(),
            "Conversation limit reached. Correct saved work in Time Tracking."
        );
        require(
            repository.reopenPlan(id, version, document),
            "Plan changed. Reload it before reopening."
        );
        repository.saveRevision(
            id,
            version + 1,
            "Reopen saved draft",
            "local-rules",
            "Loaded current saved work for review. Applying will update the existing records.",
            text(row, "plan_json"),
            document
        );
        workspace.audit("assistant draft reopened", "assistant:" + id, Map.of("version", version));
        return planRow(id);
    }

    public void validateTargets(List<Map<String, Object>> actions) {
        for (var action : actions) {
            if (integer(action, "_entry_id") > 0) require(
                fingerprint(workspace.one("entries", integer(action, "_entry_id"))).equals(
                    text(action, "_entry_baseline")
                ),
                "Saved work changed outside this conversation. Reload saved work before correcting it."
            );
            if (integer(action, "_invoice_id") > 0) require(
                text(
                    workspace.one("invoices", integer(action, "_invoice_id")),
                    "snapshot"
                ).isBlank(),
                "The invoice was finalized. Issued work cannot be changed in the assistant."
            );
        }
    }

    private String fingerprint(Map<String, Object> record) {
        try {
            byte[] bytes = workspace
                .encode(new TreeMap<>(record))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
