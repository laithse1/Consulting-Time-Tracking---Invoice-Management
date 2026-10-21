package io.consulting;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
  value = Api.class,
  properties = "app.token=test-token-at-least-24-characters"
)
class ApiTest {

  @Autowired
  MockMvc mvc;

  @MockitoBean
  Ledger ledger;

  @MockitoBean
  Exports exports;

  @Test
  void rejectsUnauthenticatedAccess() throws Exception {
    mvc.perform(get("/api/state")).andExpect(status().isUnauthorized());
    verifyNoInteractions(ledger);
  }

  @Test
  void acceptsBearerToken() throws Exception {
    when(ledger.state()).thenReturn(Map.of("entries", java.util.List.of()));
    mvc
      .perform(
        get("/api/state").header(
          "Authorization",
          "Bearer test-token-at-least-24-characters"
        )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.entries").isArray());
  }

  @Test
  void cannotInventConfirmation() throws Exception {
    mvc
      .perform(
        post("/api/invoices/1/finalize")
          .header("Authorization", "Bearer test-token-at-least-24-characters")
          .contentType("application/json")
          .content("{}")
      )
      .andExpect(status().isOk());
    verify(ledger).finalizeInvoice(1, false);
  }
}
