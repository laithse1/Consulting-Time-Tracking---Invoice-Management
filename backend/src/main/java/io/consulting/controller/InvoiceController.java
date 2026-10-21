package io.consulting.controller;

import io.consulting.dto.request.InvoiceDraftRequest;
import io.consulting.service.InvoiceQueryService;
import io.consulting.service.InvoiceService;
import io.consulting.support.RequestMapper;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoices;
    private final InvoiceQueryService queries;
    private final RequestMapper requests;

    public InvoiceController(
        InvoiceService invoices,
        InvoiceQueryService queries,
        RequestMapper requests
    ) {
        this.invoices = invoices;
        this.queries = queries;
        this.requests = requests;
    }

    @PostMapping
    public Map<String, Object> createDraft(@Valid @RequestBody InvoiceDraftRequest request) {
        return invoices.draft(requests.toCommand(request));
    }

    @GetMapping("/{id}")
    public Map<String, Object> preview(@PathVariable int id) {
        return queries.preview(id);
    }

    @PostMapping("/{id}/finalize")
    public Map<String, Object> finalizeInvoice(
        @PathVariable int id,
        @RequestBody Map<String, Object> request
    ) {
        return invoices.finalizeInvoice(id, Boolean.TRUE.equals(request.get("confirmed")));
    }

    @PostMapping("/{id}/status")
    public Object changeStatus(@PathVariable int id, @RequestBody Map<String, Object> request) {
        return invoices.transition(id, request);
    }

    @PostMapping("/{id}/withdraw")
    public Object withdraw(@PathVariable int id, @RequestBody Map<String, Object> request) {
        return invoices.withdraw(id, request);
    }

    @GetMapping("/{id}/email")
    public Map<String, Object> prepareEmail(@PathVariable int id) {
        return queries.email(id);
    }
}
