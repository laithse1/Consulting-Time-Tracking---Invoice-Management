package io.consulting.domain.assistant;

/** Conservative presentation cleanup for offline use; never adds claims or inferred outcomes. */
public final class WorkDescriptionFormatter {

    private WorkDescriptionFormatter() {}

    public static String format(String source) {
        String description = source
            .trim()
            .replaceFirst("^\\d{4}-\\d{2}-\\d{2}\\s*[:,-]?\\s*", "")
            .replaceFirst("(?i)^(?:today|yesterday)\\s*[, :-]?\\s*", "")
            .replaceFirst(
                "(?i)^(?:I\\s+)?spent\\s+\\d+(?:\\.\\d+)?\\s*(?:hours?|hrs?|h|minutes?|mins?|m)\\b(?:\\s+\\d+(?:\\.\\d+)?\\s*(?:minutes?|mins?|m)\\b)?\\s+(?:on\\s+)?",
                ""
            )
            .strip();
        if (description.isBlank()) return source.trim();
        return Character.toUpperCase(description.charAt(0)) + description.substring(1);
    }
}
