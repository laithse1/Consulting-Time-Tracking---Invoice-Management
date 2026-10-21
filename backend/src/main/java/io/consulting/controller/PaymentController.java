package io.consulting.controller;

import io.consulting.dto.request.PaymentRequest;
import io.consulting.service.PaymentService;
import io.consulting.support.RequestMapper;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PaymentController {

    private final PaymentService payments;
    private final RequestMapper requests;

    public PaymentController(PaymentService payments, RequestMapper requests) {
        this.payments = payments;
        this.requests = requests;
    }

    @PostMapping("/invoices/{id}/payments")
    public Object recordPayment(@PathVariable int id, @Valid @RequestBody PaymentRequest request) {
        return payments.payment(id, requests.toCommand(request));
    }

    @PostMapping("/historical")
    public void confirmHistoricalReceipt(@RequestBody Map<String, Object> request) {
        payments.historical(request);
    }
}
