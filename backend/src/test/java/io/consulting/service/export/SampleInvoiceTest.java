package io.consulting.service.export;

import io.consulting.service.WorkspaceService;
import io.consulting.service.export.InvoiceExportService;
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
    WorkspaceService ledger;

    @Autowired
    InvoiceExportService exports;

    @Test
    void renderUnbilledDraft() throws Exception {
        var p = ledger.draft(
            Map.of("contract_id", 1, "period_start", "2026-09-01", "period_end", "2026-09-30")
        );
        int id = io.consulting.domain.DomainValues.integer(
            (Map<String, Object>) p.get("invoice"),
            "id"
        );
        Files.write(Path.of("../docs/sample-invoice.pdf"), exports.pdf(id));
    }
}
