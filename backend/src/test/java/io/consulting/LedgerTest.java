package io.consulting;

import static io.consulting.Ledger.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
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
class LedgerTest {

  @Autowired
  org.springframework.jdbc.core.JdbcTemplate db;

  @Autowired
  Ledger l;

  @Autowired
  Exports exports;

  Map<String, Object> entry(String date, String time, int seconds) {
    return new HashMap<>(
      Map.of(
        "contract_id",
        1,
        "project_id",
        2,
        "work_date",
        date,
        "start_time",
        time,
        "seconds",
        seconds,
        "category",
        "Research",
        "description",
        "Confirmed actual services",
        "billable",
        1,
        "draft",
        0
      )
    );
  }

  void acknowledge() {
    for (var w : l.compliance(
      1,
      LocalDate.parse("2026-09-01"),
      LocalDate.parse("2026-09-30")
    ))
      l.saveWeek(
        Map.of(
          "contract_id",
          1,
          "week",
          w.get("week"),
          "available",
          1,
          "acknowledgement",
          "Assignment shortfall reviewed; actual time only"
        )
      );
  }

  int draft() {
    var p = l.draft(
      Map.of(
        "contract_id",
        1,
        "period_start",
        "2026-09-01",
        "period_end",
        "2026-09-30"
      )
    );
    return n((Map<String, Object>) p.get("invoice"), "id");
  }

  @Test
  void initialActivitiesNeverBill() {
    assertEquals(
      7,
      db.queryForObject(
        "SELECT count(*) FROM entries WHERE draft=1 AND work_date IS NULL AND seconds IS NULL",
        Integer.class
      )
    );
    var p = l.preview(draft());
    assertEquals(new BigDecimal("0.00"), p.get("total"));
    assertFalse(((List<?>) p.get("warnings")).isEmpty());
    assertThrows(Exception.class, () -> l.finalizeInvoice(draft(), true));
  }

  @Test
  void arithmeticAndWeeks() {
    assertEquals(new BigDecimal("1.02"), charge(49, new BigDecimal("75")));
    assertEquals(
      LocalDate.parse("2026-08-31"),
      week(LocalDate.parse("2026-09-01"), 1)
    );
    assertEquals(
      LocalDate.parse("2026-08-30"),
      week(LocalDate.parse("2026-09-01"), 7)
    );
  }

  @Test
  void overlapAndDuplicate() {
    l.saveEntry(entry("2026-09-02", "09:00", 3600));
    assertThrows(Exception.class, () ->
      l.saveEntry(entry("2026-09-02", "09:30", 3600))
    );
    assertThrows(Exception.class, () ->
      l.saveEntry(entry("2026-09-02", "09:00", 3600))
    );
    l.saveEntry(entry("2026-09-02", "10:00", 3600));
  }

  @Test
  void monthBoundaryAndPartialWeek() {
    l.saveEntry(entry("2026-09-30", "09:00", 3600));
    l.saveEntry(entry("2026-10-01", "09:00", 7200));
    var weeks = l.compliance(
      1,
      LocalDate.parse("2026-09-01"),
      LocalDate.parse("2026-09-30")
    );
    assertTrue((Boolean) weeks.getFirst().get("partial"));
    assertEquals(new BigDecimal("3.0000"), weeks.getLast().get("hours"));
    assertEquals(new BigDecimal("75.00"), l.preview(draft()).get("total"));
  }

  @Test
  void overtimeRequiresWrittenApproval() {
    for (int d = 2; d <= 4; d++) l.saveEntry(
      entry("2026-09-0" + d, "09:00", 6 * 3600)
    );
    acknowledge();
    int id = draft();
    assertTrue(
      ((List<?>) l.preview(id).get("warnings"))
        .stream()
        .anyMatch(x -> x.toString().contains("approval required"))
    );
    l.saveWeek(
      Map.of(
        "contract_id",
        1,
        "week",
        "2026-08-31",
        "available",
        1,
        "overtime_ref",
        "Written approval 2026-09-01",
        "acknowledgement",
        "Partial week reviewed"
      )
    );
    assertTrue(((List<?>) l.preview(id).get("warnings")).isEmpty());
  }

  @Test
  void snapshotAllocationPaymentAndExports() throws Exception {
    var e = l.saveEntry(entry("2026-09-02", "09:00", 3600));
    acknowledge();
    int id = draft();
    assertThrows(Exception.class, () -> l.finalizeInvoice(id, false));
    l.finalizeInvoice(id, true);
    assertThrows(Exception.class, () -> l.saveEntry(e));
    assertEquals(0, ((List<?>) l.preview(draft()).get("entries")).size());
    var c = new HashMap<>(l.contract(1));
    c.put("rate", "99");
    l.saveContract(c);
    assertEquals(
      "75.00",
      s((Map<String, Object>) l.preview(id).get("contract"), "rate")
    );
    l.transition(id, Map.of("state", "Sent", "submitted", "2026-09-30"));
    l.payment(
      id,
      Map.of(
        "amount",
        "25",
        "received",
        "2026-10-01",
        "method",
        "ACH",
        "reference",
        "receipt-1"
      )
    );
    var i = l.invoiceState(l.one("invoices", id));
    assertEquals(new BigDecimal("50.00"), i.get("balance"));
    assertThrows(Exception.class, () ->
      l.payment(
        id,
        Map.of(
          "amount",
          "51",
          "received",
          "2026-10-01",
          "method",
          "ACH",
          "reference",
          "overpay"
        )
      )
    );
    assertTrue(exports.pdf(id).length > 1000);
    assertTrue(exports.xlsx(id).length > 1000);
    assertTrue(
      new String(
        exports.invoiceCsv(id),
        java.nio.charset.StandardCharsets.UTF_8
      ).contains("Confirmed actual services")
    );
  }

  @Test
  void unapprovedExpenseAndAdjustments() {
    l.expense(
      Map.of(
        "contract_id",
        1,
        "expense_date",
        "2026-09-10",
        "description",
        "Hosting",
        "amount",
        "12.34"
      )
    );
    acknowledge();
    int id = draft();
    assertTrue(
      ((List<?>) l.preview(id).get("warnings"))
        .stream()
        .anyMatch(x -> x.toString().contains("prior approval missing"))
    );
    assertThrows(Exception.class, () -> l.finalizeInvoice(id, true));
    assertThrows(Exception.class, () ->
      l.draft(
        Map.of(
          "contract_id",
          1,
          "period_start",
          "2026-09-01",
          "period_end",
          "2026-09-30",
          "adjustment",
          "75"
        )
      )
    );
  }

  @Test
  void singletonTimer() {
    assertThrows(Exception.class, () ->
      l.timer(
        "start",
        Map.of(
          "contract_id",
          1,
          "project_id",
          2,
          "description",
          "Invalid date fixture",
          "category",
          "Research",
          "work_date",
          "2026-08-01"
        )
      )
    );
    l.timer(
      "start",
      Map.of(
        "contract_id",
        1,
        "project_id",
        2,
        "description",
        "Timer test",
        "category",
        "Research",
        "work_date",
        "2026-09-02"
      )
    );
    assertThrows(Exception.class, () -> l.timer("start", Map.of()));
    l.timer("pause", Map.of());
    l.timer("resume", Map.of());
    l.timer("stop", Map.of());
    assertEquals(
      0,
      db.queryForObject("SELECT count(*) FROM timer", Integer.class)
    );
    assertEquals(
      8,
      db.queryForObject(
        "SELECT count(*) FROM entries WHERE draft=1",
        Integer.class
      )
    );
  }

  @Test
  void replacementRetainsOriginal() {
    var e = l.saveEntry(entry("2026-09-02", "09:00", 3600));
    acknowledge();
    int old = draft();
    l.finalizeInvoice(old, true);
    var snapshot = s(l.one("invoices", old), "snapshot");
    assertThrows(Exception.class, () ->
      l.withdraw(
        old,
        Map.of("confirmed", false, "reason", "Correct actual duration")
      )
    );
    l.withdraw(
      old,
      Map.of("confirmed", true, "reason", "Correct actual duration")
    );
    assertEquals(
      new BigDecimal("0.00"),
      l.invoiceState(l.one("invoices", old)).get("balance")
    );
    assertThrows(Exception.class, () ->
      l.transition(
        old,
        Map.of("state", "Sent", "submitted", LocalDate.now().toString())
      )
    );
    e.put("seconds", 7200);
    l.saveEntry(e);
    var p = l.draft(
      Map.of(
        "contract_id",
        1,
        "period_start",
        "2026-09-01",
        "period_end",
        "2026-09-30",
        "replacement_for",
        old,
        "revision_reason",
        "Correct actual duration after withdrawal"
      )
    );
    int next = n((Map<String, Object>) p.get("invoice"), "id");
    l.finalizeInvoice(next, true);
    assertEquals(new BigDecimal("150.00"), l.preview(next).get("total"));
    assertEquals(snapshot, s(l.one("invoices", old), "snapshot"));
    assertEquals("Replaced", s(l.one("invoices", old), "state"));
    assertEquals(
      next,
      db.queryForObject("SELECT invoice_id FROM allocations", Integer.class)
    );
    l.transition(
      next,
      Map.of("state", "Sent", "submitted", LocalDate.now().toString())
    );
    l.payment(
      next,
      Map.of(
        "amount",
        "1",
        "received",
        LocalDate.now().toString(),
        "method",
        "Test",
        "reference",
        "Test receipt"
      )
    );
    assertThrows(Exception.class, () ->
      l.withdraw(
        next,
        Map.of("confirmed", true, "reason", "Cannot erase payment")
      )
    );
  }

  @Test
  void overtimeAvailabilityAndExceptionAreSeparateFromBilling() {
    l.saveWeek(
      Map.of(
        "contract_id",
        1,
        "week",
        "2026-09-07",
        "available",
        0,
        "acknowledgement",
        "Unavailable this week"
      )
    );
    var w = l
      .compliance(
        1,
        LocalDate.parse("2026-09-07"),
        LocalDate.parse("2026-09-13")
      )
      .getFirst();
    assertEquals("Below minimum â€” contractor unavailable", w.get("status"));
    assertEquals(false, w.get("unresolved"));
    assertEquals(new BigDecimal("0.00"), l.preview(draft()).get("total"));
  }

  @Test
  void expenseApprovalCanBeCorrectedUntilIssued() {
    var e = (Map<String, Object>) l.expense(
      Map.of(
        "contract_id",
        1,
        "expense_date",
        "2026-09-10",
        "description",
        "Test reimbursable expense",
        "amount",
        "12.34"
      )
    );
    e.put("approval_ref", "Written prior approval fixture");
    l.expense(e);
    acknowledge();
    int id = draft();
    l.finalizeInvoice(id, true);
    assertThrows(Exception.class, () -> l.expense(e));
    assertTrue(
      new String(
        exports.history(),
        java.nio.charset.StandardCharsets.UTF_8
      ).contains("Historical (separate)")
    );
  }

  @Test
  void overdueAndFullPaymentUseReceiptDate() {
    l.saveEntry(entry("2026-09-02", "09:00", 3600));
    acknowledge();
    int id = draft();
    l.finalizeInvoice(id, true);
    l.transition(
      id,
      Map.of(
        "state",
        "Sent",
        "submitted",
        LocalDate.now().minusDays(31).toString()
      )
    );
    assertEquals("Overdue", l.invoiceState(l.one("invoices", id)).get("state"));
    l.payment(
      id,
      Map.of(
        "amount",
        "75.00",
        "received",
        LocalDate.now().toString(),
        "method",
        "Test transfer",
        "reference",
        "Full payment fixture"
      )
    );
    assertEquals("Paid", l.invoiceState(l.one("invoices", id)).get("state"));
    assertEquals(
      new BigDecimal("0.00"),
      l.invoiceState(l.one("invoices", id)).get("balance")
    );
  }

  @Test
  void abandonedMultiDayTimerCanStillBeStopped() {
    l.timer(
      "start",
      Map.of(
        "contract_id",
        1,
        "project_id",
        2,
        "description",
        "Long timer fixture",
        "category",
        "Research",
        "work_date",
        "2026-09-02"
      )
    );
    db.update(
      "UPDATE timer SET started=?,elapsed=0",
      Instant.now().minusSeconds(90000).toString()
    );
    l.timer("stop", Map.of());
    assertEquals(
      0,
      db.queryForObject("SELECT count(*) FROM timer", Integer.class)
    );
    assertEquals(
      1,
      db.queryForObject(
        "SELECT count(*) FROM entries WHERE description='Long timer fixture' AND draft=1 AND seconds IS NULL",
        Integer.class
      )
    );
  }
}
