package io.consulting.integration.ai;

import static io.consulting.domain.DomainValues.require;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Model wording is reviewable; source coverage and financial facts are checked independently. */
final class ProviderResponseValidator {

    private ProviderResponseValidator() {}

    static List<Map<String, String>> segments(JsonNode result, String source) {
        var nodes = result.path("segments");
        require(
            nodes.isArray() && !nodes.isEmpty() && nodes.size() <= 30,
            "Provider must return 1–30 source segments"
        );
        var segments = new ArrayList<Map<String, String>>();
        int cursor = 0;
        for (var segment : nodes) {
            require(
                segment.isObject() && segment.path("source").isTextual(),
                "Provider source must be text"
            );
            for (String field : List.of("description", "project", "category"))
                require(
                    !segment.has(field) || segment.path(field).isTextual(),
                    "Provider " + field + " must be text"
                );
            String literal = segment.path("source").asText();
            int at = source.indexOf(literal, cursor);
            require(
                !literal.isBlank() && at >= cursor,
                "Provider returned absent, overlapping, or reordered source; no actions were applied"
            );
            require(
                source.substring(cursor, at).isBlank(),
                "Provider omitted part of your description; no actions were applied"
            );
            String description = segment.path("description").asText("");
            require(description.length() <= 2000, "Suggested description exceeds 2,000 characters");
            segments.add(
                Map.of(
                    "source",
                    literal,
                    "description",
                    description,
                    "project",
                    segment.path("project").asText(),
                    "category",
                    segment.path("category").asText()
                )
            );
            cursor = at + literal.length();
        }
        require(
            source.substring(cursor).isBlank(),
            "Provider omitted part of your description; no actions were applied"
        );
        return segments;
    }
}
