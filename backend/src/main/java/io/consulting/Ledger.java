package io.consulting;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class Ledger {

  final JdbcTemplate db;
  final ObjectMapper json;

  public Ledger(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  public List<Map<String, Object>> historyRows(String table) {
    require(
      List.of("payments", "historical").contains(table),
      "Invalid history table"
    );
    return db.queryForList("SELECT * FROM " + table);
  }

  static void require(boolean ok, String message) {
    if (!ok) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  static String s(Map<String, Object> m, String k) {
    return Objects.toString(m.get(k), "").trim();
  }

  static int n(Map<String, Object> m, String k) {
    try {
      return Integer.parseInt(s(m, k));
    } catch (Exception e) {
      return 0;
    }
  }

  static BigDecimal money(Object x) {
    try {
      return new BigDecimal(x.toString()).setScale(2, RoundingMode.HALF_UP);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid monetary amount");
    }
  }

  static BigDecimal hours(long seconds) {
    return BigDecimal.valueOf(seconds).divide(
      BigDecimal.valueOf(3600),
      4,
      RoundingMode.HALF_UP
    );
  }

  static BigDecimal charge(long seconds, BigDecimal rate) {
    return BigDecimal.valueOf(seconds)
      .multiply(rate)
      .divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
  }

  String encode(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  Map<String, Object> decode(String s) {
    try {
      return json
        .readerFor(new TypeReference<Map<String, Object>>() {})
        .with(
          com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS
        )
        .readValue(s);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  Map<String, Object> one(String table, int id) {
    var rows = db.queryForList("SELECT * FROM " + table + " WHERE id=?", id);
    require(!rows.isEmpty(), "Record not found");
    return rows.getFirst();
  }

  int insert(String sql, Object... args) {
    db.update(sql, args);
    return db.queryForObject("SELECT last_insert_rowid()", Integer.class);
  }

  void audit(String action, String entity, Object detail) {
    db.update(
      "INSERT INTO audit(occurred,action,entity,detail) VALUES(?,?,?,?)",
      Instant.now().toString(),
      action,
      entity,
      encode(detail)
    );
  }

  Map<String, Object> contract(int id) {
    return one("contracts", id);
  }

  @Transactional
  public Map<String, Object> state() {
    var result = new LinkedHashMap<String, Object>();
    for (String t : List.of(
      "clients",
      "contracts",
      "projects",
      "entries",
      "weekly",
      "expenses",
      "historical",
      "audit"
    ))
      result.put(
        t,
        db.queryForList("SELECT * FROM " + t + " ORDER BY id DESC")
      );
    result.put(
      "invoices",
      db
        .queryForList("SELECT * FROM invoices ORDER BY id DESC")
        .stream()
        .map(this::invoiceState)
        .toList()
    );
    result.put(
      "payments",
      db.queryForList("SELECT * FROM payments ORDER BY id DESC")
    );
    result.put("timer", db.queryForList("SELECT * FROM timer"));
    return result;
  }

  @Transactional
  public Map<String, Object> saveClient(Map<String, Object> b) {
    require(!s(b, "name").isEmpty(), "Client name required");
    require(
      s(b, "email").matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"),
      "Valid submission email required"
    );
    int id = n(b, "id");
    if (id == 0) id = insert(
      "INSERT INTO clients(name,email,address) VALUES(?,?,?)",
      s(b, "name"),
      s(b, "email"),
      s(b, "address")
    );
    else db.update(
      "UPDATE clients SET name=?,email=?,address=? WHERE id=?",
      s(b, "name"),
      s(b, "email"),
      s(b, "address"),
      id
    );
    audit("client saved", "client:" + id, b);
    return one("clients", id);
  }

  @Transactional
  public Map<String, Object> saveProject(Map<String, Object> b) {
    one("clients", n(b, "client_id"));
    require(!s(b, "name").isEmpty(), "Project name required");
    int id = n(b, "id");
    if (id == 0) id = insert(
      "INSERT INTO projects(client_id,name,repository) VALUES(?,?,?)",
      n(b, "client_id"),
      s(b, "name"),
      s(b, "repository")
    );
    else db.update(
      "UPDATE projects SET name=?,repository=? WHERE id=?",
      s(b, "name"),
      s(b, "repository"),
      id
    );
    audit("project saved", "project:" + id, b);
    return one("projects", id);
  }

  @Transactional
  public Map<String, Object> saveContract(Map<String, Object> b) {
    one("clients", n(b, "client_id"));
    LocalDate.parse(s(b, "start_date"));
    require(
      s(b, "end_date").isEmpty() ||
        !LocalDate.parse(s(b, "end_date")).isBefore(
          LocalDate.parse(s(b, "start_date"))
        ),
      "End date precedes start"
    );
    require(
      money(b.get("rate")).signum() > 0 &&
        n(b, "minimum_hours") >= 0 &&
        n(b, "maximum_hours") >= n(b, "minimum_hours") &&
        n(b, "week_start") >= 1 &&
        n(b, "week_start") <= 7 &&
        n(b, "payment_days") > 0 &&
        n(b, "dispute_days") > 0 &&
        !s(b, "contractor").isEmpty(),
      "Invalid contract settings"
    );
    int id = n(b, "id");
    Object[] args = {
      n(b, "client_id"),
      s(b, "contractor"),
      s(b, "start_date"),
      s(b, "end_date").isEmpty() ? null : s(b, "end_date"),
      money(b.get("rate")).toPlainString(),
      n(b, "minimum_hours"),
      n(b, "maximum_hours"),
      n(b, "payment_days"),
      n(b, "dispute_days"),
      n(b, "week_start"),
      s(b, "payment_instructions"),
      s(b, "contractor_address"),
    };
    if (id == 0) id = insert(
      "INSERT INTO contracts(client_id,contractor,start_date,end_date,rate,minimum_hours,maximum_hours,payment_days,dispute_days,week_start,payment_instructions,contractor_address) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
      args
    );
    else {
      var a = new ArrayList<>(Arrays.asList(args));
      a.add(id);
      db.update(
        "UPDATE contracts SET client_id=?,contractor=?,start_date=?,end_date=?,rate=?,minimum_hours=?,maximum_hours=?,payment_days=?,dispute_days=?,week_start=?,payment_instructions=?,contractor_address=? WHERE id=?",
        a.toArray()
      );
    }
    audit("contract saved", "contract:" + id, b);
    return contract(id);
  }

  @Transactional
  public Map<String, Object> saveEntry(Map<String, Object> b) {
    int id = n(b, "id"),
      cid = n(b, "contract_id");
    var c = contract(cid);
    require(
      id == 0 ||
        db.queryForObject(
          "SELECT count(*) FROM allocations WHERE entry_id=?",
          Integer.class,
          id
        ) == 0,
      "Issued time is immutable; use a replacement invoice"
    );
    if (id > 0) audit("entry before edit", "entry:" + id, one("entries", id));
    boolean draft = n(b, "draft") == 1;
    int seconds = n(b, "seconds");
    require(
      Set.of("0", "1").contains(s(b, "draft")) &&
        Set.of("0", "1").contains(s(b, "billable")),
      "Billing and draft flags must be 0 or 1"
    );
    require(
      seconds >= 0 && seconds <= 86400,
      "Duration must be between 0 and 24 hours"
    );
    require(!s(b, "description").isEmpty(), "Task description required");
    require(!s(b, "category").isEmpty(), "Category required");
    Integer pid = n(b, "project_id") == 0 ? null : n(b, "project_id");
    if (pid != null) require(
      n(one("projects", pid), "client_id") == n(c, "client_id"),
      "Project belongs to another client"
    );
    String date = s(b, "work_date"),
      start = s(b, "start_time");
    if (!date.isEmpty()) {
      var d = LocalDate.parse(date);
      require(
        !d.isBefore(LocalDate.parse(s(c, "start_date"))) &&
          (s(c, "end_date").isEmpty() ||
            !d.isAfter(LocalDate.parse(s(c, "end_date")))),
        "Work date outside contract"
      );
    }
    if (!draft) {
      require(
        !date.isEmpty() && seconds > 0 && seconds <= 86400 && !start.isEmpty(),
        "Confirmed work needs date, start time and actual duration (at most 24 hours)"
      );
      int from = LocalTime.parse(start).toSecondOfDay();
      require(
        from + seconds <= 86400,
        "Split work crossing midnight into separate entries"
      );
      for (var other : db.queryForList(
        "SELECT * FROM entries WHERE draft=0 AND work_date=? AND id<>?",
        date,
        id
      )) {
        int a = LocalTime.parse(s(other, "start_time")).toSecondOfDay();
        require(
          from + seconds <= a || from >= a + n(other, "seconds"),
          "Work overlaps an existing entry"
        );
      }
    }
    Object[] a = {
      cid,
      pid,
      date.isEmpty() ? null : date,
      start.isEmpty() ? null : start,
      draft && seconds == 0 ? null : seconds,
      s(b, "category"),
      s(b, "description"),
      s(b, "repository"),
      s(b, "reference"),
      s(b, "notes"),
      n(b, "billable"),
      draft ? 1 : 0,
    };
    if (id == 0) id = insert(
      "INSERT INTO entries(contract_id,project_id,work_date,start_time,seconds,category,description,repository,reference,notes,billable,draft) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
      a
    );
    else {
      var list = new ArrayList<>(Arrays.asList(a));
      list.add(id);
      db.update(
        "UPDATE entries SET contract_id=?,project_id=?,work_date=?,start_time=?,seconds=?,category=?,description=?,repository=?,reference=?,notes=?,billable=?,draft=? WHERE id=?",
        list.toArray()
      );
    }
    audit("entry saved", "entry:" + id, b);
    return one("entries", id);
  }

  @Transactional
  public void deleteEntry(int id, boolean confirmed) {
    require(confirmed, "Deletion confirmation required");
    require(
      db.queryForObject(
        "SELECT count(*) FROM allocations WHERE entry_id=?",
        Integer.class,
        id
      ) == 0,
      "Issued work cannot be deleted"
    );
    audit("entry deleted", "entry:" + id, one("entries", id));
    db.update("DELETE FROM entries WHERE id=?", id);
  }

  @Transactional
  public Object timer(String action, Map<String, Object> b) {
    var ts = db.queryForList("SELECT * FROM timer");
    if (action.equals("start")) {
      require(ts.isEmpty(), "A timer already exists");
      var timerContract = contract(n(b, "contract_id"));
      var workDate = LocalDate.parse(s(b, "work_date"));
      require(
        !workDate.isBefore(LocalDate.parse(s(timerContract, "start_date"))) &&
          (s(timerContract, "end_date").isEmpty() ||
            !workDate.isAfter(LocalDate.parse(s(timerContract, "end_date")))),
        "Timer date outside contract"
      );
      require(
        !s(b, "category").isEmpty() && n(b, "project_id") >= 0,
        "Timer category and valid project selection required"
      );
      if (n(b, "project_id") > 0) require(
        n(one("projects", n(b, "project_id")), "client_id") ==
          n(contract(n(b, "contract_id")), "client_id"),
        "Project belongs to another client"
      );
      require(!s(b, "description").isEmpty(), "Description required");
      LocalDate.parse(s(b, "work_date"));
      db.update(
        "INSERT INTO timer VALUES(1,?,?,?,?,?,?,0)",
        n(b, "contract_id"),
        n(b, "project_id") == 0 ? null : n(b, "project_id"),
        s(b, "description"),
        s(b, "category"),
        s(b, "work_date"),
        Instant.now().toString()
      );
    } else {
      require(!ts.isEmpty(), "No timer exists");
      var t = ts.getFirst();
      long elapsed =
        n(t, "elapsed") +
        (s(t, "started").isEmpty()
          ? 0
          : Duration.between(
              Instant.parse(s(t, "started")),
              Instant.now()
            ).getSeconds());
      switch (action) {
        case "pause" -> db.update(
          "UPDATE timer SET started=NULL,elapsed=? WHERE id=1",
          elapsed
        );
        case "resume" -> {
          require(s(t, "started").isEmpty(), "Timer already running");
          db.update(
            "UPDATE timer SET started=? WHERE id=1",
            Instant.now().toString()
          );
        }
        case "stop" -> {
          var entry = new HashMap<>(t);
          entry.remove("id");
          entry.put("seconds", elapsed <= 86400 ? elapsed : 0);
          entry.put("draft", 1);
          entry.put("billable", 1);
          entry.put(
            "notes",
            "Timer result: " +
              elapsed +
              " elapsed seconds. Confirm local start time, date and duration before billing. Split sessions over 24 hours into dated entries; their duration is left incomplete."
          );
          saveEntry(entry);
          db.update("DELETE FROM timer");
        }
        default -> throw new IllegalArgumentException("Unknown timer action");
      }
    }
    audit("timer " + action, "timer", b);
    return db.queryForList("SELECT * FROM timer");
  }

  static LocalDate week(LocalDate d, int start) {
    return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(start)));
  }

  public List<Map<String, Object>> compliance(
    int cid,
    LocalDate from,
    LocalDate to
  ) {
    var c = contract(cid);
    require(
      !to.isBefore(from) &&
        Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays() <=
          370,
      "Choose at most 370 days"
    );
    var result = new ArrayList<Map<String, Object>>();
    var first = LocalDate.parse(s(c, "start_date"));
    LocalDate last = s(c, "end_date").isEmpty()
      ? to.plusDays(7)
      : LocalDate.parse(s(c, "end_date"));
    for (
      var w = week(from, n(c, "week_start"));
      !w.isAfter(to);
      w = w.plusWeeks(1)
    ) {
      if (w.plusDays(6).isBefore(first) || w.isAfter(last)) continue;
      var policies = db.queryForList(
        "SELECT * FROM weekly WHERE contract_id=? AND week=?",
        cid,
        w.toString()
      );
      var p = policies.isEmpty()
        ? new HashMap<String, Object>()
        : policies.getFirst();
      long sec = db.queryForObject(
        "SELECT coalesce(sum(seconds),0) FROM entries WHERE contract_id=? AND draft=0 AND billable=1 AND work_date BETWEEN ? AND ?",
        Long.class,
        cid,
        w.toString(),
        w.plusDays(6).toString()
      );
      var h = hours(sec);
      boolean shortfall =
          h.compareTo(BigDecimal.valueOf(n(c, "minimum_hours"))) < 0,
        over = h.compareTo(BigDecimal.valueOf(n(c, "maximum_hours"))) > 0,
        partial = w.isBefore(first) || w.plusDays(6).isAfter(last);
      String status = !s(p, "exception_ref").isEmpty()
        ? "Exception agreed in writing"
        : over
          ? s(p, "overtime_ref").isEmpty()
            ? "Above maximum â€” written approval required"
            : "Above maximum â€” approval recorded"
          : shortfall
            ? p.containsKey("available") && n(p, "available") == 0
              ? "Below minimum â€” contractor unavailable"
              : "Below minimum â€” work not assigned"
            : "Within expected range";
      var r = new LinkedHashMap<String, Object>(p);
      r.put("week", w.toString());
      r.put("hours", h);
      r.put("minimum", n(c, "minimum_hours"));
      r.put("maximum", n(c, "maximum_hours"));
      r.put(
        "difference",
        h.subtract(BigDecimal.valueOf(n(c, "minimum_hours")))
      );
      r.put("status", status);
      r.put("partial", partial);
      r.put(
        "unresolved",
        (over &&
          s(p, "overtime_ref").isEmpty() &&
          s(p, "exception_ref").isEmpty()) ||
          ((shortfall || partial) &&
            s(p, "exception_ref").isEmpty() &&
            s(p, "acknowledgement").isEmpty())
      );
      result.add(r);
    }
    return result;
  }

  @Transactional
  public void saveWeek(Map<String, Object> b) {
    require(
      Set.of("0", "1").contains(s(b, "available")),
      "Availability must be 0 or 1"
    );
    var c = contract(n(b, "contract_id"));
    require(
      week(LocalDate.parse(s(b, "week")), n(c, "week_start"))
        .toString()
        .equals(s(b, "week")),
      "Date must be the configured week start"
    );
    db.update(
      "INSERT INTO weekly(contract_id,week,available,exception_ref,overtime_ref,acknowledgement) VALUES(?,?,?,?,?,?) ON CONFLICT(contract_id,week) DO UPDATE SET available=excluded.available,exception_ref=excluded.exception_ref,overtime_ref=excluded.overtime_ref,acknowledgement=excluded.acknowledgement",
      n(b, "contract_id"),
      s(b, "week"),
      n(b, "available"),
      s(b, "exception_ref"),
      s(b, "overtime_ref"),
      s(b, "acknowledgement")
    );
    audit("weekly policy saved", "contract:" + n(b, "contract_id"), b);
  }

  @Transactional
  public Object expense(Map<String, Object> b) {
    var c = contract(n(b, "contract_id"));
    LocalDate.parse(s(b, "expense_date"));
    require(
      money(b.get("amount")).signum() > 0 && !s(b, "description").isEmpty(),
      "Positive expense and description required"
    );
    int id = n(b, "id");
    if (id == 0) id = insert(
      "INSERT INTO expenses(contract_id,expense_date,description,amount,approval_ref) VALUES(?,?,?,?,?)",
      n(c, "id"),
      s(b, "expense_date"),
      s(b, "description"),
      money(b.get("amount")).toPlainString(),
      s(b, "approval_ref")
    );
    else {
      require(
        db.queryForObject(
          "SELECT count(*) FROM expense_allocations WHERE expense_id=?",
          Integer.class,
          id
        ) == 0,
        "Issued expenses are immutable"
      );
      require(
        n(one("expenses", id), "contract_id") == n(c, "id"),
        "Expense belongs to another contract"
      );
      audit("expense before edit", "expense:" + id, one("expenses", id));
      db.update(
        "UPDATE expenses SET expense_date=?,description=?,amount=?,approval_ref=? WHERE id=?",
        s(b, "expense_date"),
        s(b, "description"),
        money(b.get("amount")).toPlainString(),
        s(b, "approval_ref"),
        id
      );
    }
    audit("expense recorded", "expense:" + id, b);
    return one("expenses", id);
  }

  @Transactional
  public Map<String, Object> draft(Map<String, Object> b) {
    int cid = n(b, "contract_id");
    contract(cid);
    var from = LocalDate.parse(s(b, "period_start"));
    var to = LocalDate.parse(s(b, "period_end"));
    require(
      !to.isBefore(from) && to.toEpochDay() - from.toEpochDay() <= 366,
      "Invalid service period"
    );
    String prefix = "LA-" + from.toString().substring(0, 7) + "-";
    int sequence = db.queryForObject(
      "SELECT count(*)+1 FROM invoices WHERE number LIKE ?",
      Integer.class,
      prefix + "%"
    );
    var adjustment = money(b.getOrDefault("adjustment", "0"));
    require(adjustment.signum() >= 0, "Adjustment cannot be negative");
    require(
      adjustment.signum() == 0 ||
        (!s(b, "adjustment_reason").isEmpty() &&
          !s(b, "adjustment_approval").isEmpty()),
      "Adjustment needs documented reason and explicit manual approval reference"
    );
    int replacement = n(b, "replacement_for");
    if (replacement > 0) {
      require(
        !s(b, "revision_reason").isEmpty(),
        "Replacement reason required"
      );
      var old = one("invoices", replacement);
      require(
        n(old, "contract_id") == cid &&
          !s(old, "snapshot").isEmpty() &&
          s(old, "state").equals("Withdrawn"),
        "Invalid replacement"
      );
      require(
        db.queryForObject(
          "SELECT count(*) FROM payments WHERE invoice_id=?",
          Integer.class,
          replacement
        ) == 0,
        "Paid invoices require an accounting credit/refund process; replacement is blocked"
      );
    }
    int id = insert(
      "INSERT INTO invoices(number,contract_id,period_start,period_end,invoice_date,adjustment,adjustment_reason,adjustment_approval,presentation,replacement_for,revision_reason) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
      prefix + String.format("%03d", sequence),
      cid,
      from.toString(),
      to.toString(),
      LocalDate.now().toString(),
      adjustment.toPlainString(),
      s(b, "adjustment_reason"),
      s(b, "adjustment_approval"),
      s(b, "presentation").equals("grouped") ? "grouped" : "itemized",
      replacement == 0 ? null : replacement,
      s(b, "revision_reason")
    );
    audit("draft created", "invoice:" + id, b);
    return preview(id);
  }

  public Map<String, Object> preview(int id) {
    var i = one("invoices", id);
    if (!s(i, "snapshot").isEmpty()) {
      var r = decode(s(i, "snapshot"));
      r.put("invoice", invoiceState(i));
      return r;
    }
    int cid = n(i, "contract_id"),
      replacement = n(i, "replacement_for");
    var c = contract(cid);
    var entries = db.queryForList(
      "SELECT e.*,p.name AS project FROM entries e LEFT JOIN projects p ON p.id=e.project_id LEFT JOIN allocations a ON a.entry_id=e.id WHERE e.contract_id=? AND e.draft=0 AND e.billable=1 AND e.work_date BETWEEN ? AND ? AND (a.invoice_id IS NULL OR a.invoice_id=?) ORDER BY e.work_date,e.start_time",
      cid,
      s(i, "period_start"),
      s(i, "period_end"),
      replacement
    );
    var expenses = db.queryForList(
      "SELECT e.* FROM expenses e LEFT JOIN expense_allocations a ON a.expense_id=e.id WHERE e.contract_id=? AND e.expense_date BETWEEN ? AND ? AND (a.invoice_id IS NULL OR a.invoice_id=?)",
      cid,
      s(i, "period_start"),
      s(i, "period_end"),
      replacement
    );
    var warnings = new ArrayList<String>();
    long seconds = 0;
    BigDecimal subtotal = BigDecimal.ZERO;
    for (var e : entries) {
      seconds += n(e, "seconds");
      var amount = charge(n(e, "seconds"), money(c.get("rate")));
      e.put("amount", amount);
      subtotal = subtotal.add(amount);
      if (s(e, "description").isEmpty()) warnings.add(
        "Entry " + n(e, "id") + ": description missing"
      );
      if (
        s(e, "project").isEmpty() && s(e, "repository").isEmpty()
      ) warnings.add("Entry " + n(e, "id") + ": project reference missing");
    }
    BigDecimal exp = BigDecimal.ZERO;
    for (var e : expenses) {
      if (s(e, "approval_ref").isEmpty()) warnings.add(
        "Expense " + n(e, "id") + ": prior approval missing"
      );
      exp = exp.add(money(e.get("amount")));
    }
    if (
      entries.isEmpty() &&
      expenses.isEmpty() &&
      money(i.get("adjustment")).signum() == 0
    ) warnings.add("No eligible billable work or approved charges");
    for (var w : compliance(
      cid,
      LocalDate.parse(s(i, "period_start")),
      LocalDate.parse(s(i, "period_end"))
    ))
      if (Boolean.TRUE.equals(w.get("unresolved"))) warnings.add(
        w.get("week") +
          ": " +
          w.get("status") +
          (Boolean.TRUE.equals(w.get("partial"))
            ? "; partial contract week needs clarification"
            : "")
      );
    var r = new LinkedHashMap<String, Object>();
    r.put("invoice", i);
    if (replacement > 0) r.put(
      "replacementNumber",
      s(one("invoices", replacement), "number")
    );
    r.put("contract", c);
    r.put("client", one("clients", n(c, "client_id")));
    r.put("entries", entries);
    r.put("expenses", expenses);
    r.put("hours", hours(seconds));
    r.put("subtotal", subtotal.setScale(2));
    r.put("expenseTotal", exp.setScale(2));
    r.put(
      "total",
      subtotal
        .add(exp)
        .add(money(i.get("adjustment")))
        .setScale(2)
    );
    r.put("warnings", warnings);
    return r;
  }

  @Transactional
  public Map<String, Object> finalizeInvoice(int id, boolean confirmed) {
    require(confirmed, "Explicit confirmation required");
    var i = one("invoices", id);
    require(s(i, "snapshot").isEmpty(), "Invoice already finalized");
    var p = preview(id);
    require(
      ((List<?>) p.get("warnings")).isEmpty(),
      "Resolve validation warnings before finalization"
    );
    int replacement = n(i, "replacement_for");
    if (replacement > 0) {
      var old = one("invoices", replacement);
      require(
        s(old, "state").equals("Withdrawn") &&
          db.queryForObject(
            "SELECT count(*) FROM payments WHERE invoice_id=?",
            Integer.class,
            replacement
          ) == 0,
        "Replacement source changed"
      );
      db.update("DELETE FROM allocations WHERE invoice_id=?", replacement);
      db.update(
        "DELETE FROM expense_allocations WHERE invoice_id=?",
        replacement
      );
      db.update("UPDATE invoices SET state='Replaced' WHERE id=?", replacement);
      audit(
        "invoice replaced",
        "invoice:" + replacement,
        s(i, "revision_reason")
      );
    }
    for (var e : (List<Map<String, Object>>) p.get("entries"))
      db.update("INSERT INTO allocations VALUES(?,?)", n(e, "id"), id);
    for (var e : (List<Map<String, Object>>) p.get("expenses"))
      db.update("INSERT INTO expense_allocations VALUES(?,?)", n(e, "id"), id);
    db.update(
      "UPDATE invoices SET snapshot=?,total=?,state='Ready for Review' WHERE id=?",
      encode(p),
      p.get("total").toString(),
      id
    );
    audit("invoice finalized", "invoice:" + id, p);
    return preview(id);
  }

  Map<String, Object> invoiceState(Map<String, Object> raw) {
    var i = new LinkedHashMap<>(raw);
    BigDecimal paid = db
      .queryForList(
        "SELECT amount FROM payments WHERE invoice_id=?",
        n(i, "id")
      )
      .stream()
      .map(x -> money(x.get("amount")))
      .reduce(BigDecimal.ZERO, BigDecimal::add);
    var total = money(i.get("total"));
    i.put("paid", paid);
    i.put("balance", total.subtract(paid));
    String state = s(i, "state");
    i.put("workflow_state", state);
    var invoiceClient = s(i, "snapshot").isEmpty()
      ? one("clients", n(contract(n(i, "contract_id")), "client_id"))
      : (Map<String, Object>) decode(s(i, "snapshot")).get("client");
    i.put("client_name", s(invoiceClient, "name"));
    if (!s(i, "submitted").isEmpty()) {
      var due = LocalDate.parse(s(i, "submitted")).plusDays(
        n(
          s(i, "snapshot").isEmpty()
            ? contract(n(i, "contract_id"))
            : (Map<String, Object>) decode(s(i, "snapshot")).get("contract"),
          "payment_days"
        )
      );
      i.put("due", due.toString());
      if (
        !Set.of("Replaced", "Withdrawn").contains(state) &&
        !state.equals("Disputed")
      ) {
        if (paid.compareTo(total) >= 0) state = "Paid";
        else if (due.isBefore(LocalDate.now())) state = "Overdue";
        else if (paid.signum() > 0) state = "Partially Paid";
      }
    }
    if (Set.of("Replaced", "Withdrawn").contains(state)) i.put(
      "balance",
      new BigDecimal("0.00")
    );
    i.put("state", state);
    i.put(
      "overdue",
      i.containsKey("due") &&
        LocalDate.parse(s(i, "due")).isBefore(LocalDate.now()) &&
        total.compareTo(paid) > 0 &&
        !Set.of("Replaced", "Withdrawn").contains(state)
    );
    return i;
  }

  @Transactional
  public Object transition(int id, Map<String, Object> b) {
    var i = one("invoices", id);
    String target = s(b, "state");
    require(
      !s(i, "snapshot").isEmpty() &&
        !Set.of("Replaced", "Withdrawn").contains(s(i, "state")),
      "Finalize the invoice first"
    );
    switch (target) {
      case "Sent" -> {
        require(
          s(i, "submitted").isEmpty() &&
            s(i, "state").equals("Ready for Review"),
          "Already submitted"
        );
        var date = LocalDate.parse(s(b, "submitted"));
        require(
          !date.isAfter(LocalDate.now()),
          "Submission date cannot be in the future"
        );
        db.update(
          "UPDATE invoices SET state='Sent',submitted=? WHERE id=?",
          date.toString(),
          id
        );
      }
      case "Approved", "Disputed" -> {
        require(!s(i, "submitted").isEmpty(), "Submit invoice first");
        require(
          !target.equals("Disputed") || !s(b, "notes").isEmpty(),
          "Document the dispute"
        );
        db.update(
          "UPDATE invoices SET state=?,notes=? WHERE id=?",
          target,
          s(b, "notes"),
          id
        );
      }
      default -> throw new IllegalArgumentException(
        "Unsupported state transition"
      );
    }
    audit("invoice " + target, "invoice:" + id, b);
    return invoiceState(one("invoices", id));
  }

  @Transactional
  public Object payment(int id, Map<String, Object> b) {
    var i = invoiceState(one("invoices", id));
    require(
      !s(i, "submitted").isEmpty() &&
        !Set.of("Replaced", "Withdrawn").contains(s(i, "state")),
      "Invoice must be submitted"
    );
    var amount = money(b.get("amount"));
    var date = LocalDate.parse(s(b, "received"));
    require(
      !date.isAfter(LocalDate.now()) &&
        !date.isBefore(LocalDate.parse(s(i, "submitted"))),
      "Invalid receipt date"
    );
    require(
      amount.signum() > 0 && amount.compareTo(money(i.get("balance"))) <= 0,
      "Payment must be positive and cannot exceed balance"
    );
    require(
      !s(b, "method").isEmpty() && !s(b, "reference").isEmpty(),
      "Payment method and reference required"
    );
    insert(
      "INSERT INTO payments(invoice_id,amount,received,method,reference) VALUES(?,?,?,?,?)",
      id,
      amount.toPlainString(),
      date.toString(),
      s(b, "method"),
      s(b, "reference")
    );
    audit("payment received", "invoice:" + id, b);
    return invoiceState(one("invoices", id));
  }

  @Transactional
  public Object withdraw(int id, Map<String, Object> b) {
    var i = one("invoices", id);
    require(
      Boolean.TRUE.equals(b.get("confirmed")) && !s(b, "reason").isEmpty(),
      "Explicit confirmation and a withdrawal reason are required"
    );
    require(
      !s(i, "snapshot").isEmpty() &&
        !Set.of("Replaced", "Withdrawn").contains(s(i, "state")),
      "Only a live finalized invoice can be withdrawn"
    );
    require(
      db.queryForObject(
        "SELECT count(*) FROM payments WHERE invoice_id=?",
        Integer.class,
        id
      ) == 0,
      "Invoices with payments require an accounting credit/refund process"
    );
    db.update("DELETE FROM allocations WHERE invoice_id=?", id);
    db.update("DELETE FROM expense_allocations WHERE invoice_id=?", id);
    db.update(
      "UPDATE invoices SET state='Withdrawn',notes=? WHERE id=?",
      s(b, "reason"),
      id
    );
    audit("invoice explicitly withdrawn for correction", "invoice:" + id, b);
    return preview(id);
  }

  @Transactional
  public void historical(Map<String, Object> b) {
    LocalDate d = LocalDate.parse(s(b, "received"));
    require(
      !d.isAfter(LocalDate.now()) && !s(b, "reference").isEmpty(),
      "Actual date and receipt reference required"
    );
    var old = one("historical", 1);
    require(
      s(old, "received").isEmpty(),
      "Historical receipt already recorded"
    );
    db.update(
      "UPDATE historical SET received=?,reference=? WHERE id=1",
      d.toString(),
      s(b, "reference")
    );
    audit("historical receipt recorded", "historical:1", b);
  }

  public Map<String, Object> email(int id) {
    var p = preview(id);
    var i = (Map<String, Object>) p.get("invoice");
    var c = (Map<String, Object>) p.get("contract");
    var cl = (Map<String, Object>) p.get("client");
    String period = s(i, "period_start") + " to " + s(i, "period_end");
    return Map.of(
      "to",
      s(cl, "email"),
      "subject",
      "Consulting Invoice â€” " + period + " â€” " + s(c, "contractor"),
      "body",
      "Hello,\n\nAttached is invoice " +
        s(i, "number") +
        " for consulting services provided during " +
        period +
        " under our Independent Contractor Agreement.\n\nThe invoice includes an itemized breakdown of work performed, dates, billable hours, and applicable project references.\n\nPlease let me know if any additional information is needed.\n\nThank you,\n" +
        s(c, "contractor")
    );
  }
}
