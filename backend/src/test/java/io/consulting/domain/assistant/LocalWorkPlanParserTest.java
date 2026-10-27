package io.consulting.domain.assistant;

import static io.consulting.domain.DomainValues.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LocalWorkPlanParserTest {

    private final LocalDate reference = LocalDate.of(2026, 10, 25);
    private final List<Map<String, Object>> projects = List.of(Map.of("id", 1, "name", "Audiom"));

    @Test
    void automaticModeRetainsEveryNarrativeLineAndAccuracyRequirement() {
        String source =
            "Work summary\n" +
            IntStream.rangeClosed(1, 40)
                .mapToObj(i -> "- Audiom reference issue #" + i)
                .collect(Collectors.joining("\n")) +
            "\nAccuracy requirements: actual hours unconfirmed. Live deployment remains outstanding. Do not claim production acceptance.";
        var actions = LocalWorkPlanParser.parse(source, reference, projects, "auto", 30);
        assertEquals(1, actions.size());
        assertEquals("save_note", text(actions.getFirst(), "type"));
        assertEquals(source, text(actions.getFirst(), "source_text"));
        assertFalse(actions.getFirst().containsKey("seconds"));
        assertFalse(actions.getFirst().containsKey("work_date"));
    }

    @Test
    void strictModeReportsTheConfiguredLimitAndOffersRecovery() {
        var error = assertThrows(WorkDescriptionParser.ActionLimitExceeded.class, () ->
            LocalWorkPlanParser.parse(
                "Today 1h testing Audiom\nToday 2h documenting Audiom\nToday 3h research Audiom",
                reference,
                projects,
                "lines",
                2
            )
        );
        assertTrue(error.getMessage().contains("at most 2 actions"));
        assertTrue(error.getMessage().contains("Narrative notes"));
    }

    @Test
    void contextHeadersDoNotConsumeTaskSlots() {
        var actions = LocalWorkPlanParser.parse(
            "Project: Audiom\nDate: 2026-10-24\nTesting, 1h\nDocumentation, 30m",
            reference,
            projects,
            "auto",
            2
        );
        assertEquals(2, actions.size());
        assertEquals("log_work", text(actions.getFirst(), "type"));
        assertEquals(3600, integer(actions.getFirst(), "seconds"));
    }

    @Test
    void explicitNarrativeModeNeverExecutesEmbeddedCommandsOrInfersFacts() {
        var action = LocalWorkPlanParser.parse(
            "Today 2h testing Audiom\nStart timer for Audiom\nDraft invoice October 2026\nFuture work remains unperformed",
            reference,
            projects,
            "narrative",
            30
        ).getFirst();
        assertEquals("save_note", text(action, "type"));
        assertEquals(0, integer(action, "seconds"));
        assertFalse(action.containsKey("period_start"));
    }

    @Test
    void automaticModeDoesNotSwallowInvalidTaskFactsOrUnsupportedModes() {
        assertThrows(RuntimeException.class, () ->
            LocalWorkPlanParser.parse(
                "2026-99-99 2h testing Audiom",
                reference,
                projects,
                "auto",
                30
            )
        );
        assertThrows(RuntimeException.class, () ->
            LocalWorkPlanParser.parse(
                "Today 2h testing Audiom",
                reference,
                projects,
                "anything",
                30
            )
        );
    }
}
