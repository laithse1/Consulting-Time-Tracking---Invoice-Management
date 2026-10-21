package io.consulting.controller;

import static io.consulting.domain.DomainValues.text;

import io.consulting.service.WorkspaceService;
import io.consulting.service.export.InvoiceExportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ExportController {

    private final InvoiceExportService exports;
    private final WorkspaceService workspace;

    public ExportController(InvoiceExportService exports, WorkspaceService workspace) {
        this.exports = exports;
        this.workspace = workspace;
    }

    @GetMapping("/invoices/{id}/export/{format}")
    public ResponseEntity<byte[]> invoice(@PathVariable int id, @PathVariable String format)
        throws java.io.IOException {
        String mimeType = switch (format) {
            case "pdf" -> "application/pdf";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "csv" -> "text/csv;charset=UTF-8";
            default -> throw new IllegalArgumentException("Unknown format");
        };
        byte[] document = switch (format) {
            case "pdf" -> exports.pdf(id);
            case "xlsx" -> exports.xlsx(id);
            default -> exports.invoiceCsv(id);
        };
        String filename = text(workspace.one("invoices", id), "number") + "." + format;
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.parseMediaType(mimeType))
            .body(document);
    }

    @GetMapping("/history.csv")
    public ResponseEntity<byte[]> history() {
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=bookkeeping.csv")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .body(exports.history());
    }
}
