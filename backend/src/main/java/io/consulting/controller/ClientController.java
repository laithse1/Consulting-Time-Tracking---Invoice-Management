package io.consulting.controller;

import io.consulting.dto.request.ClientRequest;
import io.consulting.dto.request.ContractRequest;
import io.consulting.dto.request.ProjectRequest;
import io.consulting.service.ClientService;
import io.consulting.support.RequestMapper;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ClientController {

    private final ClientService clients;
    private final RequestMapper requests;

    public ClientController(ClientService clients, RequestMapper requests) {
        this.clients = clients;
        this.requests = requests;
    }

    @PostMapping("/clients")
    public Map<String, Object> saveClient(@Valid @RequestBody ClientRequest request) {
        return clients.saveClient(requests.toCommand(request));
    }

    @PostMapping("/projects")
    public Map<String, Object> saveProject(@Valid @RequestBody ProjectRequest request) {
        return clients.saveProject(requests.toCommand(request));
    }

    @PostMapping("/contracts")
    public Map<String, Object> saveContract(@Valid @RequestBody ContractRequest request) {
        return clients.saveContract(requests.toCommand(request));
    }
}
