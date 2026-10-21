package io.consulting.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.consulting.service.WorkspaceService;
import io.consulting.service.export.InvoiceExportService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = {
        WorkspaceController.class,
        InvoiceController.class,
        ClientController.class,
        TimeTrackingController.class,
        AssistantController.class,
    },
    properties = {
        "app.token=test-token-at-least-24-characters",
        "app.allowed-origins=https://frontend.example.test",
    }
)
@org.springframework.boot.context.properties.EnableConfigurationProperties(
    io.consulting.config.ApplicationProperties.class
)
@org.springframework.context.annotation.Import(io.consulting.support.RequestMapper.class)
class ApiTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    WorkspaceService ledger;

    @MockitoBean
    io.consulting.service.InvoiceService invoices;

    @MockitoBean
    io.consulting.service.InvoiceQueryService queries;

    @MockitoBean
    io.consulting.service.ClientService clients;

    @MockitoBean
    io.consulting.service.TimeTrackingService timeTracking;

    @MockitoBean
    io.consulting.service.assistant.WorkAssistantService assistant;

    @Test
    void rejectsMalformedAssistantActionsBeforeApplication() throws Exception {
        mvc.perform(
            put("/api/assistant/plans/fixture")
                .header("Authorization", AUTHORIZATION)
                .contentType("application/json")
                .content("{\"version\":1,\"actions\":[null]}")
        ).andExpect(status().isBadRequest());
        verifyNoInteractions(assistant);
    }

    private static final String AUTHORIZATION = "Bearer test-token-at-least-24-characters";

    @Test
    void rejectsFractionalSecondsRatherThanTruncatingFacts() throws Exception {
        mvc.perform(
            post("/api/entries")
                .header("Authorization", AUTHORIZATION)
                .contentType("application/json")
                .content(
                    "{\"contract_id\":1,\"seconds\":3600.5,\"description\":\"Actual research\",\"category\":\"Research\",\"billable\":1,\"draft\":0}"
                )
        ).andExpect(status().isBadRequest());
        verifyNoInteractions(timeTracking);
    }

    @Test
    void validatesTypedClientRequestsBeforeDomainCalls() throws Exception {
        mvc.perform(
            post("/api/clients")
                .header("Authorization", AUTHORIZATION)
                .contentType("application/json")
                .content("{\"name\":\"Fixture\",\"email\":\"invalid\"}")
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fields.email").exists());
        verifyNoInteractions(clients);
    }

    @Test
    void preservesDurationOnlyFactsThroughTypedRequestMapping() throws Exception {
        mvc.perform(
            post("/api/entries")
                .header("Authorization", AUTHORIZATION)
                .contentType("application/json")
                .content(
                    "{\"contract_id\":1,\"project_id\":2,\"work_date\":\"2026-09-15\",\"seconds\":3600,\"category\":\"Research\",\"description\":\"Actual research\",\"billable\":1,\"draft\":0}"
                )
        ).andExpect(status().isOk());
        verify(timeTracking).saveEntry(
            argThat(
                command ->
                    command.get("seconds").equals(3600) &&
                    command.get("project_id").equals(2) &&
                    !command.containsKey("start_time")
            )
        );
    }

    @Test
    void rejectsStructuredScalarFields() throws Exception {
        mvc.perform(
            post("/api/invoices")
                .header("Authorization", AUTHORIZATION)
                .contentType("application/json")
                .content(
                    "{\"contract_id\":1,\"period_start\":\"2026-09-01\",\"period_end\":\"2026-09-30\",\"presentation\":{}}"
                )
        ).andExpect(status().isBadRequest());
        verifyNoInteractions(invoices);
    }

    @Test
    void allowsOnlyConfiguredCrossOriginPreflights() throws Exception {
        mvc.perform(
            options("/api/state")
                .header("Origin", "https://frontend.example.test")
                .header("Access-Control-Request-Method", "GET")
        )
            .andExpect(status().isNoContent())
            .andExpect(
                header().string("Access-Control-Allow-Origin", "https://frontend.example.test")
            );
        mvc.perform(
            get("/api/state")
                .header("Origin", "https://untrusted.example.test")
                .header("Authorization", AUTHORIZATION)
        ).andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnauthenticatedAccess() throws Exception {
        mvc.perform(get("/api/state")).andExpect(status().isUnauthorized());
        verifyNoInteractions(ledger);
    }

    @Test
    void acceptsBearerToken() throws Exception {
        when(ledger.state()).thenReturn(Map.of("entries", java.util.List.of()));
        mvc.perform(
            get("/api/state").header("Authorization", "Bearer test-token-at-least-24-characters")
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries").isArray());
    }

    @Test
    void cannotInventConfirmation() throws Exception {
        mvc.perform(
            post("/api/invoices/1/finalize")
                .header("Authorization", "Bearer test-token-at-least-24-characters")
                .contentType("application/json")
                .content("{}")
        ).andExpect(status().isOk());
        verify(invoices).finalizeInvoice(1, false);
    }
}
