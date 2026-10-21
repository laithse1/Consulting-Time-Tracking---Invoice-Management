package io.consulting.controller;

import io.consulting.service.ComplianceService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ComplianceController {

    private final ComplianceService compliance;

    public ComplianceController(ComplianceService compliance) {
        this.compliance = compliance;
    }

    @GetMapping("/compliance")
    public List<Map<String, Object>> review(
        @RequestParam int contract,
        @RequestParam LocalDate from,
        @RequestParam LocalDate to
    ) {
        return compliance.compliance(contract, from, to);
    }

    @PostMapping("/weekly")
    public void saveReview(@RequestBody Map<String, Object> request) {
        compliance.saveWeek(request);
    }
}
