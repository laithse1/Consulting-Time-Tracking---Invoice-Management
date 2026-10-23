package io.consulting.domain.assistant;

import static io.consulting.domain.DomainValues.integer;
import static io.consulting.domain.DomainValues.require;
import static io.consulting.domain.DomainValues.text;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Bounded, deterministic language parsing. Missing or uncertain facts stay missing. */
public class WorkDescriptionParser {

    public static final List<String> CATEGORIES = List.of(
        "Software Development",
        "Code Refactoring",
        "Repository Management",
        "Research",
        "Architecture and Design",
        "Deployment and Infrastructure",
        "Testing and Debugging",
        "Documentation",
        "Meetings and Standups",
        "Project Planning",
        "Other"
    );
    static final Pattern ISO = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    static final Pattern DURATION = Pattern.compile(
        "(?i)(?<![a-z\\d.\\-])(\\d+(?:\\.\\d+)?)\\s*(hours?|hrs?|h|minutes?|mins?|m)(?![a-z])"
    );
    static final Pattern RANGE = Pattern.compile(
        "(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)\\s*(?:-|–|—|to)\\s*([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)"
    );
    static final Pattern UNCERTAIN = Pattern.compile(
        "(?i)\\b(about|approximately|approx|roughly|estimate[ds]?|planned|planning to|plan to|will|todo|need to|not worked|did not|didn't|haven't)\\b|~"
    );
    static final Pattern MONTH = Pattern.compile(
        "(?i)\\b(january|february|march|april|may|june|july|august|september|october|november|december|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec)\\s+(\\d{4})\\b"
    );

    public static LocalDate date(String text, LocalDate reference) {
        var iso = ISO.matcher(text);
        if (iso.find()) return LocalDate.parse(iso.group(1));
        var lower = text.toLowerCase(Locale.ROOT);
        if (Pattern.compile("\\byesterday\\b").matcher(lower).find()) return reference.minusDays(1);
        if (Pattern.compile("\\btoday\\b").matcher(lower).find()) return reference;
        var named = Pattern.compile(
            "(?i)\\b(?:on\\s+)?([a-z]{3,9})\\s+(\\d{1,2})(?:st|nd|rd|th)?[,]?\\s+(\\d{4})\\b"
        ).matcher(text);
        if (named.find()) try {
            return LocalDate.of(
                Integer.parseInt(named.group(3)),
                month(named.group(1)),
                Integer.parseInt(named.group(2))
            );
        } catch (IllegalArgumentException | DateTimeException ignored) {}
        return null;
    }

    static int month(String name) {
        String key = name.substring(0, 3).toLowerCase(Locale.ROOT);
        return (
            List.of(
                "jan",
                "feb",
                "mar",
                "apr",
                "may",
                "jun",
                "jul",
                "aug",
                "sep",
                "oct",
                "nov",
                "dec"
            ).indexOf(key) + 1
        );
    }

    public static YearMonth period(String text) {
        var iso = Pattern.compile("\\b(\\d{4}-\\d{2})(?:-\\d{2})?\\b").matcher(text);
        if (iso.find()) return YearMonth.parse(iso.group(1));
        var m = MONTH.matcher(text);
        return m.find() ? YearMonth.of(Integer.parseInt(m.group(2)), month(m.group(1))) : null;
    }

    static String category(String line) {
        String t = line.toLowerCase(Locale.ROOT);
        String[][] rules = {
            { "Code Refactoring", "refactor|restructur" },
            { "Meetings and Standups", "meeting|standup|stand-up|introduction|call with" },
            { "Testing and Debugging", "test|debug|bug|fix" },
            { "Documentation", "document|readme|wrote.*guide" },
            { "Research", "research|evaluat|investigat|compar" },
            {
                "Deployment and Infrastructure",
                "deploy|hosting|infrastructure|docker|railway|render",
            },
            { "Repository Management", "repository|migrat|pushed|github organization" },
            { "Architecture and Design", "architect|design|transport|isolation" },
            { "Project Planning", "planning|user stor|backlog" },
            { "Software Development", "implement|develop|built|code|feature" },
        };
        for (var rule : rules) if (Pattern.compile(rule[1]).matcher(t).find()) return rule[0];
        return "Other";
    }

    static Integer project(String text, List<Map<String, Object>> projects) {
        var matches = new ArrayList<Map<String, Object>>();
        for (var p : projects)
            if (
                Pattern.compile(
                    "(?i)(?<![a-z0-9])" + Pattern.quote(text(p, "name")) + "(?![a-z0-9])"
                )
                    .matcher(text)
                    .find()
            ) matches.add(p);
        // A longer project name subsumes the short name, e.g. Audiom MCP Server vs Audiom.
        matches.removeIf(p ->
            matches
                .stream()
                .anyMatch(
                    q ->
                        integer(q, "id") != integer(p, "id") &&
                        text(q, "name")
                            .toLowerCase(Locale.ROOT)
                            .contains(text(p, "name").toLowerCase(Locale.ROOT))
                )
        );
        if (matches.size() == 1) return integer(matches.getFirst(), "id");
        if (matches.isEmpty()) return null;
        return -1;
    }

    public static Map<String, Object> work(
        String line,
        LocalDate reference,
        LocalDate context,
        Integer projectContext,
        List<Map<String, Object>> projects
    ) {
        var out = new LinkedHashMap<String, Object>();
        var questions = new ArrayList<String>();
        LocalDate d = date(line, reference);
        if (d == null) d = context;
        int seconds = 0;
        String start = "";
        var range = RANGE.matcher(line);
        boolean rangeFound = range.find();
        boolean uncertain = UNCERTAIN.matcher(line).find();
        if (rangeFound) {
            int a = Integer.parseInt(range.group(1)) * 3600 + Integer.parseInt(range.group(2)) * 60,
                b = Integer.parseInt(range.group(3)) * 3600 + Integer.parseInt(range.group(4)) * 60;
            start = String.format("%02d:%02d", a / 3600, (a / 60) % 60);
            if (b > a) seconds = b - a;
            else questions.add("Split a time range crossing midnight into separate dated entries.");
        }
        boolean multipleRanges = rangeFound && range.find();
        var durations = DURATION.matcher(line);
        BigDecimal count = BigDecimal.ZERO;
        int durationParts = 0,
            hourParts = 0,
            minuteParts = 0;
        while (durations.find()) {
            durationParts++;
            boolean h = durations.group(2).toLowerCase(Locale.ROOT).startsWith("h");
            if (h) hourParts++;
            else minuteParts++;
            count = count.add(
                new BigDecimal(durations.group(1)).multiply(BigDecimal.valueOf(h ? 3600 : 60))
            );
        }
        if (!rangeFound && durationParts > 0) try {
            seconds = count.intValueExact();
        } catch (ArithmeticException e) {
            questions.add("Enter an actual duration in whole seconds.");
        }
        if (rangeFound && durationParts > 0 && count.compareTo(BigDecimal.valueOf(seconds)) != 0) {
            seconds = 0;
            questions.add("The stated duration and clock range disagree; confirm the actual time.");
        }
        if (seconds <= 0 || seconds > 86400) {
            seconds = 0;
            questions.add("Actual duration is missing or invalid; no hours were assumed.");
        }
        if (uncertain) {
            seconds = 0;
            questions.add(
                "Estimated, planned or negated work needs an actual-work confirmation and duration."
            );
        }
        if (multipleRanges) {
            seconds = 0;
            start = "";
            questions.add(
                "Several clock ranges were supplied. Split them into separate work items."
            );
        }
        if (
            Pattern.compile("(?<!\\w)-\\s*\\d+(?:\\.\\d+)?\\s*(?:h|m)", Pattern.CASE_INSENSITIVE)
                .matcher(line)
                .find() ||
            (Pattern.compile("(?i)\\b(or|between)\\b").matcher(line).find() && durationParts > 0)
        ) {
            seconds = 0;
            questions.add("Confirm a single positive actual duration.");
        }
        if (
            hourParts > 1 ||
            minuteParts > 1 ||
            (Pattern.compile("(?i)\\b(or|between)\\b").matcher(line).find() && durationParts > 1)
        ) {
            seconds = 0;
            questions.add(
                "Several durations are ambiguous. Split the tasks or confirm one actual duration."
            );
        }
        var dates = ISO.matcher(line);
        var uniqueDates = new HashSet<String>();
        while (dates.find()) uniqueDates.add(dates.group(1));
        var namedDates = Pattern.compile(
            "(?i)\\b([a-z]{3,9})\\s+(\\d{1,2})(?:st|nd|rd|th)?[,]?\\s+(\\d{4})\\b"
        ).matcher(line);
        while (namedDates.find())
            try {
                uniqueDates.add(
                    LocalDate.of(
                        Integer.parseInt(namedDates.group(3)),
                        month(namedDates.group(1)),
                        Integer.parseInt(namedDates.group(2))
                    ).toString()
                );
            } catch (DateTimeException | IllegalArgumentException ignored) {}
        if (Pattern.compile("(?i)\\btoday\\b").matcher(line).find()) uniqueDates.add(
            reference.toString()
        );
        if (Pattern.compile("(?i)\\byesterday\\b").matcher(line).find()) uniqueDates.add(
            reference.minusDays(1).toString()
        );
        if (uniqueDates.size() > 1) {
            d = null;
            questions.add("Several dates were supplied. Split the work by date.");
        }
        if (d == null) questions.add("Work date is missing. Choose the actual date.");
        Integer pid = project(line, projects);
        if (pid == null) pid = projectContext;
        if (pid != null && pid <= 0) pid = null;
        if (pid == null) questions.add(
            "Choose a project; the description does not identify exactly one."
        );
        if (!rangeFound) questions.add(
            "No clock range supplied. Duration-only work cannot be checked for clock overlaps."
        );
        var refs = new ArrayList<String>();
        var urls = Pattern.compile(
            "https?://[^\\s|]+|(?i)(?:PR|issue|commit)\\s*#?[a-z0-9]+|#\\d+"
        ).matcher(line);
        while (urls.find()) refs.add(urls.group());
        out.put("type", "log_work");
        out.put("description", WorkDescriptionFormatter.format(line));
        out.put("source_text", line);
        out.put("work_date", d == null ? "" : d.toString());
        out.put("seconds", seconds);
        out.put("start_time", start);
        out.put("project_id", pid == null ? 0 : pid);
        out.put("category", category(line));
        out.put("billable", Pattern.compile("(?i)non[- ]?billable").matcher(line).find() ? 0 : 1);
        out.put("draft", d == null || seconds == 0 || pid == null ? 1 : 0);
        out.put("reference", String.join(" ", refs));
        out.put("repository", "");
        out.put(
            "notes",
            "Captured from Work Assistant. Original wording is retained in the assistant history."
        );
        out.put("questions", questions);
        return out;
    }

    public static List<Map<String, Object>> parse(
        String text,
        LocalDate reference,
        List<Map<String, Object>> projects
    ) {
        var actions = new ArrayList<Map<String, Object>>();
        LocalDate context = null;
        Integer projectContext = null;
        for (String raw : text.split("\\R")) {
            String line = raw.replaceFirst("^\\s*(?:[-*•]|\\d+[.)])\\s+", "").trim();
            if (line.isBlank()) continue;
            if (
                line.matches("(?i)^(date|day)\\s*:.*") ||
                line.matches("(?i)^(today|yesterday|\\d{4}-\\d{2}-\\d{2})\\s*:?$")
            ) {
                context = date(line, reference);
                continue;
            }
            if (line.matches("(?i)^project\\s*:.*")) {
                projectContext = project(line, projects);
                continue;
            }
            if (line.matches("(?i)^total\\s*:?.*")) {
                continue;
            }
            var command = command(line, reference, projects);
            actions.add(
                command == null ? work(line, reference, context, projectContext, projects) : command
            );
        }
        require(!actions.isEmpty(), "Include a work description or supported command.");
        require(actions.size() <= 30, "Use at most 30 work items/commands per message.");
        return actions;
    }

    static Map<String, Object> command(
        String line,
        LocalDate reference,
        List<Map<String, Object>> projects
    ) {
        String t = line.toLowerCase(Locale.ROOT);
        var timer = Pattern.compile("(?i)^(pause|resume|stop) (?:the )?timer[.!]?$").matcher(line);
        if (timer.matches()) return new LinkedHashMap<>(
            Map.of("type", timer.group(1).toLowerCase(Locale.ROOT) + "_timer", "description", line)
        );
        if (t.startsWith("start timer")) {
            var w = work(
                line.replaceFirst("(?i)^start timer\\s*(?:for)?\\s*", ""),
                reference,
                reference,
                null,
                projects
            );
            w.put("type", "start_timer");
            w.put("work_date", reference.toString());
            w.put(
                "questions",
                integer(w, "project_id") == 0
                    ? List.of("Choose the project for this timer.")
                    : List.of()
            );
            return w;
        }
        if (
            t.matches("^(prepare|create|generate|draft)\\s+(?:a |an |the )?invoice\\b.*") ||
            t.matches("^invoice\\s+.*")
        ) {
            var m = period(line);
            return new LinkedHashMap<>(
                Map.of(
                    "type",
                    "draft_invoice",
                    "description",
                    line,
                    "period_start",
                    m == null ? "" : m.atDay(1).toString(),
                    "period_end",
                    m == null ? "" : m.atEndOfMonth().toString(),
                    "questions",
                    m == null
                        ? List.of("Choose the service period; no month was assumed.")
                        : List.of()
                )
            );
        }
        if (
            t.matches("^(show |generate |prepare )?(?:a |the )?report\\b.*") ||
            t.startsWith("review week")
        ) {
            var m = period(line);
            LocalDate from = m == null ? reference.withDayOfMonth(1) : m.atDay(1),
                to =
                    m == null
                        ? reference.withDayOfMonth(reference.lengthOfMonth())
                        : m.atEndOfMonth();
            return new LinkedHashMap<>(
                Map.of(
                    "type",
                    t.startsWith("review week") ? "compliance" : "report",
                    "description",
                    line,
                    "period_start",
                    from.toString(),
                    "period_end",
                    to.toString(),
                    "questions",
                    List.of()
                )
            );
        }
        var email = Pattern.compile(
            "(?i)^(?:prepare|copy) (?:submission )?email(?: for)? ([A-Z][A-Z0-9]{0,11}-\\d{4}-\\d{2}-\\d+)$"
        ).matcher(line);
        if (email.matches()) return new LinkedHashMap<>(
            Map.of(
                "type",
                "prepare_email",
                "description",
                line,
                "number",
                email.group(1).toUpperCase(Locale.ROOT),
                "questions",
                List.of()
            )
        );
        return null;
    }
}
