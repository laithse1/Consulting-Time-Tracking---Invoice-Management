package io.consulting.domain.assistant;

import static io.consulting.domain.DomainValues.require;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keeps unstructured notes intact when bounded local task extraction cannot represent them. */
public final class LocalWorkPlanParser {

    private LocalWorkPlanParser() {}

    public static List<Map<String, Object>> parse(
        String source,
        LocalDate reference,
        List<Map<String, Object>> projects,
        String mode,
        int maxActions
    ) {
        require(
            Set.of("auto", "lines", "narrative").contains(mode),
            "Choose Automatic, Task lines, or Narrative notes"
        );
        if (mode.equals("narrative")) return narrative(source);
        if (mode.equals("auto")) {
            var structured = StructuredWorkIntake.parse(source, reference, projects);
            if (!structured.isEmpty()) return structured;
        }
        try {
            return WorkDescriptionParser.parse(source, reference, projects, maxActions);
        } catch (WorkDescriptionParser.ActionLimitExceeded exception) {
            if (mode.equals("lines")) throw exception;
            return narrative(source);
        }
    }

    public static List<Map<String, Object>> narrative(String source) {
        var action = new LinkedHashMap<String, Object>();
        action.put("type", "save_note");
        action.put(
            "description",
            "Retain this narrative as nonbillable notes. No work entries, hours, dates, or invoice will be generated from it."
        );
        action.put("source_text", source);
        action.put(
            "questions",
            List.of(
                "Local rules have not organized this narrative into completed work. All original notes, references, and accuracy requirements are retained together.",
                "For narrative interpretation, select a configured language provider and review a new plan from these original notes. Otherwise supply one actual dated task and duration per line in smaller messages.",
                "Future or unperformed work and unsupported completion claims must remain outside the invoice. Actual hours still require confirmation."
            )
        );
        return new ArrayList<>(List.of(action));
    }
}
