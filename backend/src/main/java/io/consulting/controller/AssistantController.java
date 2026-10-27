package io.consulting.controller;

import static io.consulting.domain.DomainValues.integer;
import static io.consulting.domain.DomainValues.text;

import io.consulting.service.assistant.AssistantRevisionService;
import io.consulting.service.assistant.WorkAssistantService;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    final WorkAssistantService assistant;
    final AssistantRevisionService revisions;

    public AssistantController(WorkAssistantService assistant, AssistantRevisionService revisions) {
        this.assistant = assistant;
        this.revisions = revisions;
    }

    @GetMapping
    public Object state(@RequestParam int contract) {
        return assistant.history(contract);
    }

    @PostMapping("/draft")
    public void draft(@RequestBody Map<String, Object> request) {
        assistant.saveDraft(integer(request, "contract_id"), text(request, "source"));
    }

    @GetMapping("/plans/{id}")
    public Object plan(@PathVariable String id) {
        return assistant.getPlan(id);
    }

    @DeleteMapping("/plans/{id}")
    public void deleteConversation(
        @PathVariable String id,
        @RequestParam int version,
        @RequestParam(defaultValue = "false") boolean confirmed
    ) {
        assistant.deleteConversation(id, version, confirmed);
    }

    @PostMapping("/plan")
    public Object propose(@RequestBody Map<String, Object> request) {
        assistant.saveDraft(integer(request, "contract_id"), text(request, "source"));
        return assistant.propose(request);
    }

    @PutMapping("/plans/{id}")
    public Object edit(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return assistant.update(id, request);
    }

    @PostMapping("/plans/{id}/apply")
    public Object apply(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return assistant.apply(id, request);
    }

    @PostMapping("/plans/{id}/split")
    public Object split(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return assistant.split(id, integer(request, "version"), integer(request, "action_index"));
    }

    @PostMapping("/plans/{id}/invoice")
    public Object invoice(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return assistant.invoiceDraft(
            id,
            integer(request, "version"),
            text(request, "period_start"),
            text(request, "period_end")
        );
    }

    @PostMapping("/plans/{id}/revise")
    public Object revise(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return revisions.revise(id, request);
    }

    @PostMapping("/plans/{id}/reopen")
    public Object reopen(@PathVariable String id, @RequestBody Map<String, Object> request) {
        return assistant.reopen(id, integer(request, "version"));
    }
}
