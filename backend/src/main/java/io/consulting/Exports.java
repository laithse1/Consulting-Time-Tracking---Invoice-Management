package io.consulting;

import static io.consulting.Ledger.*;

import java.io.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class Exports {

  final Ledger ledger;

  public Exports(Ledger ledger) {
    this.ledger = ledger;
  }

  List<String[]> rows(int id) {
    var p = ledger.preview(id);
    var i = (Map<String, Object>) p.get("invoice");
    var c = (Map<String, Object>) p.get("contract");
    var cl = (Map<String, Object>) p.get("client");
    var rows = new ArrayList<String[]>();
    rows.add(new String[] { "CONSULTING INVOICE", s(i, "number") });
    if (p.containsKey("replacementNumber")) rows.add(new String[] {
      "Replaces withdrawn invoice",
      p.get("replacementNumber").toString(),
      s(i, "revision_reason"),
    });
    if (s(i, "snapshot").isEmpty()) rows.add(new String[] {
      "DRAFT - NOT FOR SUBMISSION",
    });
    rows.add(new String[] {
      "From",
      s(c, "contractor"),
      s(c, "contractor_address"),
    });
    rows.add(new String[] {
      "Bill to",
      s(cl, "name"),
      s(cl, "address"),
      s(cl, "email"),
    });
    rows.add(new String[] { "Invoice date", s(i, "invoice_date") });
    rows.add(new String[] {
      "Service period",
      s(i, "period_start"),
      s(i, "period_end"),
    });
    rows.add(new String[] {
      "Date",
      "Project / repository",
      "Description",
      "Hours",
      "Rate USD",
      "Amount USD",
    });
    var entries = (List<Map<String, Object>>) p.get("entries");
    if (s(i, "presentation").equals("grouped")) {
      var grouped = new LinkedHashMap<String, java.math.BigDecimal>();
      for (var e : entries)
        grouped.merge(
          s(e, "project"),
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
        s(e, "work_date"),
        s(e, "project") + " / " + s(e, "repository"),
        s(e, "description") +
          (s(e, "reference").isEmpty() ? "" : " [" + s(e, "reference") + "]"),
        hours(n(e, "seconds")).toPlainString(),
        s(c, "rate"),
        s(e, "amount"),
      });
    rows.add(new String[] {
      "Total billable hours",
      p.get("hours").toString(),
    });
    rows.add(new String[] {
      "Professional services USD",
      p.get("subtotal").toString(),
    });
    for (var e : (List<Map<String, Object>>) p.get("expenses"))
      rows.add(new String[] {
        "Expense " + s(e, "expense_date"),
        s(e, "description"),
        "Approval: " + s(e, "approval_ref"),
        s(e, "amount"),
      });
    if (money(i.get("adjustment")).signum() != 0) rows.add(new String[] {
      "Explicit approved adjustment",
      s(i, "adjustment_reason"),
      s(i, "adjustment_approval"),
      s(i, "adjustment"),
    });
    rows.add(new String[] { "TOTAL DUE USD", p.get("total").toString() });
    rows.add(new String[] {
      "Payment terms",
      "Approved invoices payable within " +
        n(c, "payment_days") +
        " days of receipt.",
    });
    rows.add(new String[] { "Dispute period", n(c, "dispute_days") + " days" });
    rows.add(new String[] {
      "Payment instructions",
      s(c, "payment_instructions"),
    });
    return rows;
  }

  static String safe(String s) {
    return s.replaceAll("[\\r\\n]+", " ");
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
            String.join(",", Arrays.stream(r).map(Exports::csvCell).toList())
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
        s(i, "number"),
        s(i, "period_start") + " to " + s(i, "period_end"),
        s(i, "total"),
        s(i, "paid"),
        s(i, "balance"),
        s(i, "state"),
        s(i, "client_name"),
        s(i, "invoice_date"),
        s(i, "submitted"),
        s(i, "due"),
        s(i, "workflow_state"),
      });
    for (var p : ledger.historyRows("payments"))
      rows.add(new String[] {
        "Payment",
        s(p, "invoice_id"),
        s(p, "received"),
        s(p, "amount"),
        "",
        "",
        s(p, "method") + " / " + s(p, "reference"),
        "",
        "",
        "",
        "",
        "",
      });
    for (var h : ledger.historyRows("historical"))
      rows.add(new String[] {
        "Historical (separate)",
        s(h, "id"),
        s(h, "received"),
        s(h, "amount"),
        "",
        "",
        s(h, "reference"),
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
      body.setVerticalAlignment(
        org.apache.poi.ss.usermodel.VerticalAlignment.TOP
      );
      var font = book.createFont();
      font.setBold(true);
      heading.setFont(font);
      heading.setWrapText(true);
      heading.setFillForegroundColor(
        org.apache.poi.ss.usermodel.IndexedColors.LIGHT_GREEN.getIndex()
      );
      heading.setFillPattern(
        org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND
      );
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
            row == 1 ||
            values[0].equals("Date") ||
            values[0].startsWith("TOTAL")
          ) cell.setCellStyle(heading);
        }
      }
      for (int j = 0; j < 6; j++) sheet.setColumnWidth(
        j,
        j == 2 ? 18000 : 6000
      );
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
      if ((font.getStringWidth(line + " ") / 1000) * size <= width) line.append(
        ' '
      );
    }
    if (!line.isEmpty()) lines.add(line.toString());
    return lines;
  }
}
