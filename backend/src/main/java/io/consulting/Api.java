package io.consulting;

import static io.consulting.Ledger.*;

import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class Api {

  final Ledger l;
  final Exports x;

  public Api(Ledger l, Exports x) {
    this.l = l;
    this.x = x;
  }

  @GetMapping("/state")
  public Object state() {
    return l.state();
  }

  @PostMapping("/clients")
  public Object client(@RequestBody Map<String, Object> b) {
    return l.saveClient(b);
  }

  @PostMapping("/contracts")
  public Object contract(@RequestBody Map<String, Object> b) {
    return l.saveContract(b);
  }

  @PostMapping("/projects")
  public Object project(@RequestBody Map<String, Object> b) {
    return l.saveProject(b);
  }

  @PostMapping("/entries")
  public Object entry(@RequestBody Map<String, Object> b) {
    return l.saveEntry(b);
  }

  @DeleteMapping("/entries/{id}")
  public void delete(@PathVariable int id, @RequestParam boolean confirmed) {
    l.deleteEntry(id, confirmed);
  }

  @PostMapping("/timer/{action}")
  public Object timer(
    @PathVariable String action,
    @RequestBody Map<String, Object> b
  ) {
    return l.timer(action, b);
  }

  @GetMapping("/compliance")
  public Object compliance(
    @RequestParam int contract,
    @RequestParam LocalDate from,
    @RequestParam LocalDate to
  ) {
    return l.compliance(contract, from, to);
  }

  @PostMapping("/weekly")
  public void weekly(@RequestBody Map<String, Object> b) {
    l.saveWeek(b);
  }

  @PostMapping("/expenses")
  public Object expense(@RequestBody Map<String, Object> b) {
    return l.expense(b);
  }

  @PostMapping("/invoices")
  public Object draft(@RequestBody Map<String, Object> b) {
    return l.draft(b);
  }

  @GetMapping("/invoices/{id}")
  public Object invoice(@PathVariable int id) {
    return l.preview(id);
  }

  @PostMapping("/invoices/{id}/finalize")
  public Object finalizeInvoice(
    @PathVariable int id,
    @RequestBody Map<String, Object> b
  ) {
    return l.finalizeInvoice(id, Boolean.TRUE.equals(b.get("confirmed")));
  }

  @PostMapping("/invoices/{id}/status")
  public Object status(
    @PathVariable int id,
    @RequestBody Map<String, Object> b
  ) {
    return l.transition(id, b);
  }

  @PostMapping("/invoices/{id}/payments")
  public Object payment(
    @PathVariable int id,
    @RequestBody Map<String, Object> b
  ) {
    return l.payment(id, b);
  }

  @PostMapping("/historical")
  public void historical(@RequestBody Map<String, Object> b) {
    l.historical(b);
  }

  @GetMapping("/invoices/{id}/email")
  public Object email(@PathVariable int id) {
    return l.email(id);
  }

  @GetMapping("/invoices/{id}/export/{format}")
  public ResponseEntity<byte[]> export(
    @PathVariable int id,
    @PathVariable String format
  ) throws Exception {
    String mime = switch (format) {
      case "pdf" -> "application/pdf";
      case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
      case "csv" -> "text/csv;charset=UTF-8";
      default -> throw new IllegalArgumentException("Unknown format");
    };
    byte[] bytes = switch (format) {
      case "pdf" -> x.pdf(id);
      case "xlsx" -> x.xlsx(id);
      default -> x.invoiceCsv(id);
    };
    return ResponseEntity.ok()
      .header(
        "Content-Disposition",
        "attachment; filename=\"" +
          s(l.one("invoices", id), "number") +
          "." +
          format +
          "\""
      )
      .contentType(MediaType.parseMediaType(mime))
      .body(bytes);
  }

  @PostMapping("/invoices/{id}/withdraw")
  public Object withdraw(
    @PathVariable int id,
    @RequestBody Map<String, Object> b
  ) {
    return l.withdraw(id, b);
  }

  @GetMapping("/history.csv")
  public ResponseEntity<byte[]> history() {
    return ResponseEntity.ok()
      .header("Content-Disposition", "attachment; filename=bookkeeping.csv")
      .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
      .body(x.history());
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    java.time.DateTimeException.class,
    org.springframework.dao.DataIntegrityViolationException.class,
  })
  public ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.badRequest().body(
      Map.of(
        "message",
        e instanceof IllegalArgumentException ||
          e instanceof java.time.DateTimeException
          ? Objects.toString(e.getMessage(), "Invalid input")
          : "Record conflicts with existing financial data"
      )
    );
  }
}
