package io.consulting.domain.assistant;

import static io.consulting.domain.DomainValues.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/** Reads explicit allocation tables without turning a budget into an actual time ledger. */
public final class StructuredWorkIntake {

    private StructuredWorkIntake() {}

    private static final String MONTH =
        "(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)";
    private static final Pattern PERIOD = Pattern.compile(
        "(?i)\\b" + MONTH + "\\s+(\\d{1,2})\\s*[–—-]\\s*(\\d{1,2})(?:,?\\s+(20\\d{2}))?"
    );
    private static final Pattern ROW = Pattern.compile("^(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s*$");

    /** Empty means no explicit range was supplied; month-only commands retain their old behavior. */
    public static List<LocalDate> period(String source, LocalDate reference) {
        var iso = Pattern.compile(
            "(20\\d{2}-\\d{2}-\\d{2})\\s*(?:to|through|[–—])\\s*(20\\d{2}-\\d{2}-\\d{2})",
            Pattern.CASE_INSENSITIVE
        ).matcher(source);
        if (iso.find()) return checked(
            LocalDate.parse(iso.group(1)),
            LocalDate.parse(iso.group(2))
        );
        var match = PERIOD.matcher(source);
        if (!match.find()) return List.of();
        int year = match.group(4) == null ? reference.getYear() : Integer.parseInt(match.group(4));
        int month = WorkDescriptionParser.month(match.group(1));
        return checked(
            LocalDate.of(year, month, Integer.parseInt(match.group(2))),
            LocalDate.of(year, month, Integer.parseInt(match.group(3)))
        );
    }

    private static List<LocalDate> checked(LocalDate from, LocalDate to) {
        require(!to.isBefore(from), "The explicit service period ends before it starts");
        return List.of(from, to);
    }

    public static Map<String, Object> invoice(String source, LocalDate reference) {
        for (String line : source.split("\\R")) {
            if (
                !line
                    .strip()
                    .matches("(?i)^(create|prepare|draft|generate)\\s+[^.\\r\\n]*\\binvoice\\b.*")
            ) continue;
            var dates = period(line, reference);
            if (dates.isEmpty()) continue;
            return new LinkedHashMap<>(
                Map.of(
                    "type",
                    "draft_invoice",
                    "description",
                    "Prepare invoice from confirmed actual work only",
                    "period_start",
                    dates.getFirst().toString(),
                    "period_end",
                    dates.getLast().toString(),
                    "skip",
                    true,
                    "questions",
                    List.of(
                        "Invoice preparation is initially excluded. Resolve billing questions, confirm actual work, and then include this action."
                    )
                )
            );
        }
        return null;
    }

    public static List<Map<String, Object>> parse(
        String source,
        LocalDate reference,
        List<Map<String, Object>> projects
    ) {
        var actions = new ArrayList<Map<String, Object>>();
        boolean table = false;
        List<LocalDate> dates = List.of();
        boolean planned = false;
        int rows = 0;
        for (String raw : source.split("\\R")) {
            String line = raw
                .strip()
                .replaceAll("^\\|\\s*|\\s*\\|$", "")
                .replaceAll("\\s*\\|\\s*", "\t");
            if (line.matches("(?i)^Period\\s+Work description\\s+Hours$")) {
                table = true;
                continue;
            }
            if (!table || line.isBlank() || line.matches("[-:|\\s]+")) continue;
            if (line.matches("(?i)^(proposed )?total.*")) {
                table = false;
                continue;
            }
            var row = ROW.matcher(line);
            if (!row.matches()) {
                table = false;
                continue;
            }
            String description = row.group(1).strip();
            var dateMatch = PERIOD.matcher(description);
            if (dateMatch.find() && dateMatch.start() == 0) {
                dates = period(dateMatch.group(), reference);
                planned = description
                    .substring(dateMatch.end())
                    .matches("(?is)^\\s*(?:[–—-]\\s*)?planned\\b.*");
                description = description
                    .substring(dateMatch.end())
                    .replaceFirst("(?i)^\\s*(?:[–—-]\\s*)?planned\\b", "")
                    .strip();
            }
            if (dates.isEmpty() || description.isBlank()) {
                table = false;
                continue;
            }
            BigDecimal proposed = new BigDecimal(row.group(2));
            require(
                proposed.signum() >= 0 && proposed.compareTo(BigDecimal.valueOf(10000)) <= 0,
                "Invalid proposed allocation"
            );
            boolean future = planned || dates.getFirst().isAfter(reference);
            Map<String, Object> action;
            if (future) {
                action = new LinkedHashMap<>();
                action.put("type", "save_note");
                action.put("description", description);
                action.put(
                    "questions",
                    List.of(
                        "Planned work only. No time entry or charge will be created. Resubmit actual work after it is performed."
                    )
                );
            } else {
                action = WorkDescriptionParser.providerWork(description, reference, projects);
                // A week and an Hours column describe an allocation, not a dated actual entry.
                action.put("work_date", "");
                action.put("seconds", 0);
                action.put("draft", 1);
                action.put(
                    "questions",
                    List.of(
                        "Confirm the actual date, duration, and project. The table allocation is a proposal; it has not been logged as actual hours. Split work across actual dates when necessary."
                    )
                );
            }
            action.put("source_text", raw);
            action.put("proposed_hours", proposed.toPlainString());
            action.put("allocation_start", dates.getFirst().toString());
            action.put("allocation_end", dates.getLast().toString());
            action.put("work_state", future ? "planned" : "proposed");
            actions.add(action);
            rows++;
        }
        if (rows < 2) return List.of();

        // Explicit completion corrections are separate tasks, never assigned the future budget.
        var completion = Pattern.compile(
            "(?im)^([^\\r\\n]+?) (?:was|were) (?:already )?completed on (" +
                MONTH +
                "\\s+\\d{1,2}(?:,?\\s+20\\d{2})?)[.]([^\\r\\n]*)"
        ).matcher(source);
        while (completion.find()) {
            String date = completion.group(2);
            if (!date.matches(".*20\\d{2}$")) date += " " + reference.getYear();
            var action = WorkDescriptionParser.providerWork(
                completion.group(1) + " completed on " + date,
                reference,
                projects
            );
            action.put("source_text", completion.group());
            action.put("draft", 1);
            action.put("seconds", 0);
            action.put("work_state", "performed_unconfirmed");
            action.put(
                "questions",
                List.of(
                    "Confirm actual duration and project. Rebalance the existing week's allocations and exclude overlapping future review work; no extra hours were inferred."
                )
            );
            actions.add(action);
        }
        var note = new LinkedHashMap<String, Object>();
        note.put("type", "save_note");
        note.put(
            "description",
            "Review allocation assumptions, billing authorization, references, and outstanding acceptance requirements."
        );
        note.put("source_text", source);
        note.put("requires_resolution", true);
        note.put("resolved", false);
        note.put(
            "questions",
            List.of(
                "Confirm actual work and hours; a weekly budget does not establish hours worked. Resolve partial-week billing against the configured contract or documented client agreement.",
                "Reconcile completed PR preparation with this week's allocations and later planned review so the same work is not counted twice.",
                "Retain issue/PR references and qualifications. Do not claim final acceptance, closed issues, or deployment verification when outstanding. Review all original notes for items outside the table.",
                "Use configured rate, payment terms, and billing details. Resolve missing or conflicting details before finalization. Mark these questions resolved only after reviewing them."
            )
        );
        actions.add(note);
        return actions;
    }
}
