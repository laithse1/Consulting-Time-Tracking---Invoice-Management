package io.consulting.service.export;

import static io.consulting.domain.DomainValues.hours;
import static io.consulting.domain.DomainValues.integer;
import static io.consulting.domain.DomainValues.money;
import static io.consulting.domain.DomainValues.text;

import io.consulting.service.WorkspaceService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class InvoiceExportService {

    final WorkspaceService ledger;

    public InvoiceExportService(WorkspaceService ledger) {
        this.ledger = ledger;
    }

    List<String[]> rows(int id) {
        var p = ledger.preview(id);
        var i = (Map<String, Object>) p.get("invoice");
        var c = (Map<String, Object>) p.get("contract");
        var cl = (Map<String, Object>) p.get("client");
        var rows = new ArrayList<String[]>();
        rows.add(new String[] { "CONSULTING INVOICE", text(i, "number") });
        if (p.containsKey("replacementNumber")) rows.add(new String[] {
            "Replaces withdrawn invoice",
            p.get("replacementNumber").toString(),
            text(i, "revision_reason"),
        });
        if (text(i, "snapshot").isEmpty()) rows.add(new String[] { "DRAFT - NOT FOR SUBMISSION" });
        rows.add(new String[] { "From", text(c, "contractor"), text(c, "contractor_address") });
        rows.add(new String[] {
            "Bill to",
            text(cl, "name"),
            text(cl, "address"),
            text(cl, "email"),
        });
        rows.add(new String[] { "Invoice date", text(i, "invoice_date") });
        rows.add(new String[] { "Service period", text(i, "period_start"), text(i, "period_end") });
        rows.add(new String[] {
            "Date",
            "Project / repository",
            "Description",
            "Hours",
            "Rate USD",
            "Amount USD",
        });
        var entries = (List<Map<String, Object>>) p.get("entries");
        if (text(i, "presentation").equals("grouped")) {
            var grouped = new LinkedHashMap<String, java.math.BigDecimal>();
            for (var e : entries)
                grouped.merge(
                    text(e, "project"),
                    money(e.get("amount")),
                    java.math.BigDecimal::add
                );
            grouped.forEach((k, v) ->
                rows.add(new String[] { "Project subtotal", k, v.toPlainString() })
            );
            rows.add(new String[] { "Required dated work detail follows" });
        }
        for (var e : entries)
            rows.add(new String[] {
                text(e, "work_date"),
                text(e, "project") + " / " + text(e, "repository"),
                text(e, "description") +
                    (text(e, "reference").isEmpty() ? "" : " [" + text(e, "reference") + "]"),
                hours(integer(e, "seconds")).toPlainString(),
                text(c, "rate"),
                text(e, "amount"),
            });
        rows.add(new String[] { "Total billable hours", p.get("hours").toString() });
        rows.add(new String[] { "Professional services USD", p.get("subtotal").toString() });
        for (var e : (List<Map<String, Object>>) p.get("expenses"))
            rows.add(new String[] {
                "Expense " + text(e, "expense_date"),
                text(e, "description"),
                "Approval: " + text(e, "approval_ref"),
                text(e, "amount"),
            });
        if (money(i.get("adjustment")).signum() != 0) rows.add(new String[] {
            "Explicit approved adjustment",
            text(i, "adjustment_reason"),
            text(i, "adjustment_approval"),
            text(i, "adjustment"),
        });
        rows.add(new String[] { "TOTAL DUE USD", p.get("total").toString() });
        rows.add(new String[] {
            "Payment terms",
            "Approved invoices payable within " + integer(c, "payment_days") + " days of receipt.",
        });
        rows.add(new String[] { "Dispute period", integer(c, "dispute_days") + " days" });
        rows.add(new String[] { "Payment instructions", text(c, "payment_instructions") });
        return rows;
    }

    static String safe(String text) {
        return text.replaceAll("[\\r\\n]+", " ");
    }

    static String csvCell(String v) {
        v = safe(v);
        if (v.matches("^[=+@\\-\\t].*")) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    byte[] csv(List<String[]> rows) {
        return (
            "\uFEFF" +
            String.join(
                "\r\n",
                rows
                    .stream()
                    .map(r ->
                        String.join(
                            ",",
                            Arrays.stream(r).map(InvoiceExportService::csvCell).toList()
                        )
                    )
                    .toList()
            )
        ).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] invoiceCsv(int id) {
        return csv(rows(id));
    }

    public byte[] history() {
        var rows = new ArrayList<String[]>();
        rows.add(new String[] {
            "Type",
            "Number / invoice ID",
            "Period / date",
            "Total / amount USD",
            "Paid USD",
            "Balance USD",
            "Status / reference",
            "Client",
            "Invoice date",
            "Submitted",
            "Due",
            "Review state",
        });
        for (var i : (List<Map<String, Object>>) ledger.state().get("invoices"))
            rows.add(new String[] {
                "Invoice",
                text(i, "number"),
                text(i, "period_start") + " to " + text(i, "period_end"),
                text(i, "total"),
                text(i, "paid"),
                text(i, "balance"),
                text(i, "state"),
                text(i, "client_name"),
                text(i, "invoice_date"),
                text(i, "submitted"),
                text(i, "due"),
                text(i, "workflow_state"),
            });
        for (var p : ledger.historyRows("payments"))
            rows.add(new String[] {
                "Payment",
                text(p, "invoice_id"),
                text(p, "received"),
                text(p, "amount"),
                "",
                "",
                text(p, "method") + " / " + text(p, "reference"),
                "",
                "",
                "",
                "",
                "",
            });
        for (var h : ledger.historyRows("historical"))
            rows.add(new String[] {
                "Historical (separate)",
                text(h, "id"),
                text(h, "received"),
                text(h, "amount"),
                "",
                "",
                text(h, "reference"),
                "",
                "",
                "",
                "",
                "",
            });
        return csv(rows);
    }

    public byte[] xlsx(int id) throws IOException {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("Invoice");
            var heading = book.createCellStyle();
            var body = book.createCellStyle();
            body.setWrapText(true);
            body.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.TOP);
            var font = book.createFont();
            font.setBold(true);
            heading.setFont(font);
            heading.setWrapText(true);
            heading.setFillForegroundColor(
                org.apache.poi.ss.usermodel.IndexedColors.LIGHT_GREEN.getIndex()
            );
            heading.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);
            int row = 0;
            for (var values : rows(id)) {
                var r = sheet.createRow(row++);
                int lines = 1;
                for (int j = 0; j < values.length; j++) lines = Math.max(
                    lines,
                    (int) Math.ceil(values[j].length() / (j == 2 ? 65.0 : 22.0))
                );
                r.setHeightInPoints(Math.min(400, Math.max(32, lines * 16)));
                for (int j = 0; j < values.length; j++) {
                    var cell = r.createCell(j);
                    cell.setCellValue(values[j]);
                    cell.setCellStyle(body);
                    if (
                        row == 1 || values[0].equals("Date") || values[0].startsWith("TOTAL")
                    ) cell.setCellStyle(heading);
                }
            }
            for (int j = 0; j < 6; j++) sheet.setColumnWidth(j, j == 2 ? 18000 : 6000);
            sheet.createFreezePane(0, 7);
            sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);
            sheet.setFitToPage(true);
            book.write(out);
            return out.toByteArray();
        }
    }

    public byte[] pdf(int id) throws IOException {
        return PdfInvoice.render(ledger.preview(id));
    }

    static List<String> wrap(String text, PDType1Font font, int size, int width)
        throws IOException {
        var lines = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            for (int k = 0; k < word.length(); k++) {
                String next = line.toString() + word.charAt(k);
                if ((font.getStringWidth(next) / 1000) * size > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(word.charAt(k));
            }
            if ((font.getStringWidth(line + " ") / 1000) * size <= width) line.append(' ');
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
