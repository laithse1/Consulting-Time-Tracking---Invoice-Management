package io.consulting.controller;

import io.consulting.service.WorkspaceService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/state")
public class WorkspaceController {

    private final WorkspaceService workspace;

    public WorkspaceController(WorkspaceService workspace) {
        this.workspace = workspace;
    }

    @GetMapping
    public Map<String, Object> load() {
        return workspace.state();
    }
}
