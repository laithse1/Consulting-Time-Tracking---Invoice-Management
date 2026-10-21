package io.consulting;

import static io.consulting.Ledger.*;

import java.io.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;

/** Paginated invoice layout. Every service line retains its date and full references. */
final class PdfInvoice implements AutoCloseable {

  final PDDocument document = new PDDocument();
  final PDType1Font regular = new PDType1Font(
    Standard14Fonts.FontName.HELVETICA
  );
  final PDType1Font bold = new PDType1Font(
    Standard14Fonts.FontName.HELVETICA_BOLD
  );
  PDPageContentStream content;
  float y;
  int pages;
  final String number;
  final boolean draft;

  PdfInvoice(String number, boolean draft) throws IOException {
    this.number = number;
    this.draft = draft;
    newPage();
  }

  static String printable(String value) {
    return value.replaceAll("[^\\x20-\\x7E]", " ");
  }

  void text(String value, float x, float baseline, int size, boolean strong)
    throws IOException {
    content.beginText();
    content.setFont(strong ? bold : regular, size);
    content.setNonStrokingColor(.12f, .22f, .19f);
    content.newLineAtOffset(x, baseline);
    content.showText(printable(value));
    content.endText();
  }

  void newPage() throws IOException {
    if (content != null) content.close();
    var page = new PDPage(PDRectangle.LETTER);
    document.addPage(page);
    content = new PDPageContentStream(document, page);
    content.setNonStrokingColor(.10f, .28f, .23f);
    content.addRect(44, 738, 524, 5);
    content.fill();
    text("CONSULTING INVOICE", 44, 707, 19, true);
    text(
      number + (draft ? "  |  DRAFT - NOT FOR SUBMISSION" : ""),
      44,
      686,
      10,
      false
    );
    text("USD  |  " + number + "  |  Page " + ++pages, 44, 34, 8, false);
    y = 651;
  }

  void ensure(float height) throws IOException {
    if (y - height < 65) newPage();
  }

  List<String> lines(String value, int width) throws IOException {
    return Exports.wrap(printable(value), regular, 9, width);
  }

  void paragraph(String value, boolean strong) throws IOException {
    for (String line : lines(value, 515)) {
      ensure(15);
      text(line, 44, y, 9, strong);
      y -= 14;
    }
    y -= 7;
  }

  void tableHeading() throws IOException {
    ensure(30);
    content.setNonStrokingColor(.93f, .96f, .94f);
    content.addRect(44, y - 10, 524, 26);
    content.fill();
    text("DATE", 50, y, 8, true);
    text("SERVICES / PROJECT", 124, y, 8, true);
    text("HOURS", 438, y, 8, true);
    text("USD", 516, y, 8, true);
    y -= 30;
  }

  void work(Map<String, Object> e) throws IOException {
    var detail = new ArrayList<String>();
    detail.addAll(lines(s(e, "description"), 300));
    detail.addAll(
      lines(
        s(e, "project") +
          (s(e, "repository").isEmpty() ? "" : " / " + s(e, "repository")),
        300
      )
    );
    if (!s(e, "reference").isEmpty()) detail.addAll(
      lines("Reference: " + s(e, "reference"), 300)
    );
    if (y - Math.min(detail.size() * 13 + 16, 500) < 65) {
      newPage();
      tableHeading();
    }
    text(s(e, "work_date"), 50, y, 8, false);
    text(hours(n(e, "seconds")).toPlainString(), 438, y, 8, false);
    text(s(e, "amount"), 516, y, 8, true);
    for (var line : detail) {
      if (y < 75) {
        newPage();
        tableHeading();
        text("(continued)", 50, y, 8, false);
      }
      text(line, 124, y, 9, false);
      y -= 13;
    }
    content.setStrokingColor(.87f, .91f, .88f);
    content.moveTo(44, y - 3);
    content.lineTo(568, y - 3);
    content.stroke();
    y -= 20;
  }

  void amount(String label, String value, boolean total) throws IOException {
    ensure(36);
    text(label, 310, y, total ? 12 : 9, total);
    float width =
      ((total ? bold : regular).getStringWidth(value) / 1000) *
      (total ? 15 : 10);
    text(value, 562 - width, y, total ? 15 : 10, true);
    y -= total ? 36 : 23;
  }

  static byte[] render(Map<String, Object> p) throws IOException {
    var i = (Map<String, Object>) p.get("invoice");
    var c = (Map<String, Object>) p.get("contract");
    var client = (Map<String, Object>) p.get("client");
    try (
      var pdf = new PdfInvoice(s(i, "number"), s(i, "snapshot").isEmpty());
      var out = new ByteArrayOutputStream()
    ) {
      pdf.paragraph("FROM  " + s(c, "contractor"), true);
      if (p.containsKey("replacementNumber")) pdf.paragraph(
        "Replaces withdrawn invoice " +
          p.get("replacementNumber") +
          ". Reason: " +
          s(i, "revision_reason"),
        true
      );
      if (!s(c, "contractor_address").isEmpty()) pdf.paragraph(
        s(c, "contractor_address"),
        false
      );
      pdf.paragraph("BILL TO  " + s(client, "name"), true);
      if (!s(client, "address").isEmpty()) pdf.paragraph(
        s(client, "address"),
        false
      );
      pdf.paragraph(s(client, "email"), false);
      pdf.paragraph(
        "Invoice date: " +
          s(i, "invoice_date") +
          "    Service period: " +
          s(i, "period_start") +
          " to " +
          s(i, "period_end"),
        false
      );
      pdf.paragraph(
        "Hourly rate: USD " +
          s(c, "rate") +
          "    Total billable hours: " +
          p.get("hours"),
        false
      );
      var entries = (List<Map<String, Object>>) p.get("entries");
      if (s(i, "presentation").equals("grouped")) {
        var groups = new LinkedHashMap<String, java.math.BigDecimal>();
        for (var e : entries)
          groups.merge(
            s(e, "project"),
            money(e.get("amount")),
            java.math.BigDecimal::add
          );
        for (var g : groups.entrySet())
          pdf.paragraph(
            "Project summary: " + g.getKey() + " - USD " + g.getValue(),
            true
          );
        pdf.paragraph("Dated service detail", true);
      }
      pdf.tableHeading();
      for (var e : entries) pdf.work(e);
      if (entries.isEmpty()) pdf.paragraph(
        "No confirmed billable work in this draft.",
        false
      );
      for (var e : (List<Map<String, Object>>) p.get("expenses"))
        pdf.paragraph(
          "Expense " +
            s(e, "expense_date") +
            ": " +
            s(e, "description") +
            " | USD " +
            s(e, "amount") +
            " | Prior approval: " +
            s(e, "approval_ref"),
          false
        );
      if (money(i.get("adjustment")).signum() != 0) pdf.paragraph(
        "Explicit approved adjustment: " +
          s(i, "adjustment_reason") +
          " | Manual approval: " +
          s(i, "adjustment_approval") +
          " | USD " +
          s(i, "adjustment"),
        false
      );
      pdf.y -= 16;
      pdf.amount("Professional services", p.get("subtotal").toString(), false);
      pdf.amount("Approved expenses", p.get("expenseTotal").toString(), false);
      if (money(i.get("adjustment")).signum() != 0) pdf.amount(
        "Approved adjustment",
        s(i, "adjustment"),
        false
      );
      pdf.amount("TOTAL DUE (USD)", p.get("total").toString(), true);
      pdf.paragraph(
        "Approved invoices payable within " +
          n(c, "payment_days") +
          " days of receipt. Dispute period: " +
          n(c, "dispute_days") +
          " days.",
        false
      );
      if (!s(c, "payment_instructions").isEmpty()) pdf.paragraph(
        "Payment instructions: " + s(c, "payment_instructions"),
        false
      );
      pdf.content.close();
      pdf.content = null;
      pdf.document.save(out);
      return out.toByteArray();
    }
  }

  public void close() throws IOException {
    if (content != null) content.close();
    document.close();
  }
}
