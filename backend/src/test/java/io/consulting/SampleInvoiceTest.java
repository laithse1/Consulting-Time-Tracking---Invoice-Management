package io.consulting;

import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
  properties = {
    "spring.datasource.url=jdbc:sqlite::memory:",
    "app.token=test-token-at-least-24-characters",
  }
)
@Transactional
@EnabledIfSystemProperty(named = "samplePdf", matches = "true")
class SampleInvoiceTest {

  @Autowired
  Ledger ledger;

  @Autowired
  Exports exports;

  @Test
  void renderUnbilledDraft() throws Exception {
    var p = ledger.draft(
      Map.of(
        "contract_id",
        1,
        "period_start",
        "2026-09-01",
        "period_end",
        "2026-09-30"
      )
    );
    int id = Ledger.n((Map<String, Object>) p.get("invoice"), "id");
    Files.write(Path.of("../docs/sample-invoice.pdf"), exports.pdf(id));
  }
}
