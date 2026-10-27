package io.consulting.domain.assistant;

import static io.consulting.domain.DomainValues.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;

class StructuredWorkIntakeTest {

    public static String fixture() throws Exception {
        try (
            var input = StructuredWorkIntakeTest.class.getResourceAsStream(
                "/assistant/october-proposed-work.txt"
            )
        ) {
            return new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void proposedOctoberTablePreservesAllAllocationsWithoutInventingActualTime() throws Exception {
        var source = fixture();
        var actions = StructuredWorkIntake.parse(source, LocalDate.of(2026, 10, 25), List.of());
        assertEquals(18, actions.size()); // 16 rows, actual PR task, review questions.
        assertEquals("issue #1", text(actions.getFirst(), "reference"));
        assertEquals(
            13,
            actions
                .stream()
                .filter(a -> text(a, "type").equals("log_work"))
                .count()
        );
        assertEquals(
            4,
            actions
                .stream()
                .filter(a -> text(a, "work_state").equals("planned"))
                .count()
        );
        BigDecimal proposed = BigDecimal.ZERO,
            planned = BigDecimal.ZERO;
        for (var a : actions) {
            assertEquals(0, integer(a, "seconds"));
            if (a.containsKey("proposed_hours")) {
                var hours = new BigDecimal(text(a, "proposed_hours"));
                proposed = proposed.add(hours);
                if (text(a, "work_state").equals("planned")) planned = planned.add(hours);
            }
        }
        assertEquals(new BigDecimal("80"), proposed);
        assertEquals(new BigDecimal("16"), planned);
        assertEquals("2026-10-01", text(actions.get(1), "allocation_start"));
        assertEquals("2026-10-02", text(actions.get(1), "allocation_end"));
        assertEquals("2026-10-25", text(actions.get(16), "work_date"));
        assertTrue(text(actions.get(16), "description").contains("PR split"));
        assertEquals(source, actions.getLast().get("source_text"));
        assertEquals(true, actions.getLast().get("requires_resolution"));
        assertEquals(false, actions.getLast().get("resolved"));
    }

    @Test
    void explicitServiceRangeDoesNotExpandToTheEndOfTheMonth() {
        var reference = LocalDate.of(2026, 10, 25);
        var action = WorkDescriptionParser.parse(
            "Create invoice covering October 1–30, 2026",
            reference,
            List.of()
        ).getFirst();
        assertEquals("2026-10-01", text(action, "period_start"));
        assertEquals("2026-10-30", text(action, "period_end"));
        assertEquals(
            "2026-10-31",
            text(
                WorkDescriptionParser.parse(
                    "Draft invoice October 2026",
                    reference,
                    List.of()
                ).getFirst(),
                "period_end"
            )
        );
    }

    @Test
    void futureAndProposedDurationsAreNeverActuals() {
        var reference = LocalDate.of(2026, 10, 25);
        for (String source : List.of("2026-10-26 4h testing", "2026-10-20 proposed 4h testing")) {
            var work = WorkDescriptionParser.providerWork(source, reference, List.of());
            assertEquals(0, integer(work, "seconds"));
            assertEquals(1, integer(work, "draft"));
        }
    }

    @Test
    void markdownTablesAndChangedAllocationsAreNotHardCodedToOctober() {
        String source =
            "| Period | Work description | Hours |\n| --- | --- | --- |\n| Nov 2-6 | Document setup issue #8 | 2.5 |\n| | Investigate tests | 1.5 |\n| Nov 9-13 | Future verification | 4 |";
        var actions = StructuredWorkIntake.parse(source, LocalDate.of(2026, 11, 8), List.of());
        assertEquals(4, actions.size());
        assertEquals("2.5", text(actions.getFirst(), "proposed_hours"));
        assertEquals("2026-11-02", text(actions.get(1), "allocation_start"));
        assertEquals("save_note", text(actions.get(2), "type"));
    }
}
