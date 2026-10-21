package io.consulting.controller;

import io.consulting.dto.request.TimeEntryRequest;
import io.consulting.service.TimeTrackingService;
import io.consulting.support.RequestMapper;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TimeTrackingController {

    private final TimeTrackingService timeTracking;
    private final RequestMapper requests;

    public TimeTrackingController(TimeTrackingService timeTracking, RequestMapper requests) {
        this.timeTracking = timeTracking;
        this.requests = requests;
    }

    @PostMapping("/entries")
    public Map<String, Object> saveEntry(@Valid @RequestBody TimeEntryRequest request) {
        return timeTracking.saveEntry(requests.toCommand(request));
    }

    @DeleteMapping("/entries/{id}")
    public void deleteEntry(@PathVariable int id, @RequestParam boolean confirmed) {
        timeTracking.deleteEntry(id, confirmed);
    }

    @PostMapping("/timer/{action}")
    public Object controlTimer(
        @PathVariable String action,
        @RequestBody Map<String, Object> request
    ) {
        return timeTracking.timer(action, request);
    }
}
