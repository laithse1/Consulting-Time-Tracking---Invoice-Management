package io.consulting.service.assistant;

import static io.consulting.domain.DomainValues.*;
import static org.junit.jupiter.api.Assertions.*;

import io.consulting.domain.assistant.WorkDescriptionParser;
import io.consulting.service.WorkspaceService;
import io.consulting.service.assistant.WorkAssistantService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
        "spring.datasource.url=jdbc:sqlite::memory:",
        "app.token=test-token-at-least-24-characters",
    }
)
@Transactional
class WorkAssistantServiceTest {

    @Autowired
    WorkAssistantService assistant;

    @Autowired
    JdbcTemplate db;

    final LocalDate date = LocalDate.parse("2026-09-15");

    List<Map<String, Object>> projects() {
        return db.queryForList("SELECT * FROM projects");
    }

    Map<String, Object> propose(String source) {
        return assistant.propose(
            Map.of("contract_id", 1, "source", source, "reference_date", date.toString())
        );
    }

    @Test
    void parsesFactsWithoutInventingClockTimes() {
        var a = WorkDescriptionParser.parse(
            "Today spent 1h 30m testing Audiom, PR #42",
            date,
            projects()
        ).getFirst();
        assertEquals(5400, integer(a, "seconds"));
        assertEquals("2026-09-15", text(a, "work_date"));
        assertEquals("", text(a, "start_time"));
        assertEquals(0, integer(a, "draft"));
        assertEquals(1, integer(a, "project_id"));
    }

    @Test
    void incompleteUncertainAndAmbiguousWorkStaysDraft() {
        for (String text : List.of(
            "Researched Audiom",
            "Today about 2h testing Audiom",
            "Today -2h testing Audiom",
            "Today 2h or 3h testing Audiom",
            "2026-09-15 and 2026-09-16 2h testing Audiom",
            "Today will spend 2h testing Audiom"
        )) {
            var a = WorkDescriptionParser.parse(text, date, projects()).getFirst();
            assertEquals(1, integer(a, "draft"), text);
        }
    }

    @Test
    void headersDoNotDistributeTotals() {
        var actions = WorkDescriptionParser.parse(
            "Date: 2026-09-15\nProject: Audiom\nImplemented feature, 2h\nDocumented user stories\nTotal: 4h",
            date,
            projects()
        );
        assertEquals(2, actions.size());
        assertEquals(7200, integer(actions.getFirst(), "seconds"));
        assertEquals(0, integer(actions.getLast(), "seconds"));
        assertEquals(1, integer(actions.getLast(), "draft"));
    }

    @Test
    void ambiguousProjectsAndMultipleRangesNeedReview() {
        var actions = WorkDescriptionParser.parse(
            "Project: Audiom\nToday 2h testing Audiom and geospatial-mcp-server\nToday 09:00-10:00 and 11:00-12:00 testing Audiom",
            date,
            projects()
        );
        assertEquals(0, integer(actions.getFirst(), "project_id"));
        assertEquals(1, integer(actions.getFirst(), "draft"));
        assertEquals(0, integer(actions.getLast(), "seconds"));
        assertEquals(1, integer(actions.getLast(), "draft"));
    }

    @Test
    void proposalHasNoLedgerEffectsAndApplicationIsIdempotent() {
        int before = db.queryForObject("SELECT count(*) FROM entries", Integer.class);
        var p = propose(
            "2026-09-15 2h refactoring geospatial-mcp-server\nDraft invoice September 2026\nGenerate report September 2026"
        );
        assertEquals(before, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
        var first = assistant.apply(text(p, "id"), Map.of("version", 1, "confirmed", true));
        var retry = assistant.apply(text(p, "id"), Map.of("version", 1, "confirmed", true));
        assertEquals(first.get("results"), retry.get("results"));
        assertEquals(before + 1, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM invoices", Integer.class));
        assertEquals("Draft", db.queryForObject("SELECT state FROM invoices", String.class));
        assertEquals("150.00", assistant.report(1, date, date).get("servicesValue").toString());
    }

    @Test
    @Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED
    )
    void conflictRollsBackEntirePlan() {
        var p = propose(
            "2026-09-15 09:00-11:00 testing Audiom\n2026-09-15 10:00-12:00 documenting Audiom"
        );
        assertThrows(RuntimeException.class, () ->
            assistant.apply(text(p, "id"), Map.of("version", 1, "confirmed", true))
        );
        assertEquals(7, db.queryForObject("SELECT count(*) FROM entries", Integer.class));
    }

    @Test
    void confirmationAndVersionAreRequired() {
        var p = propose("Today 2h testing Audiom");
        assertThrows(RuntimeException.class, () ->
            assistant.apply(text(p, "id"), Map.of("version", 1))
        );
        assertThrows(RuntimeException.class, () ->
            assistant.apply(text(p, "id"), Map.of("version", 2, "confirmed", true))
        );
        var actions = (List<Map<String, Object>>) p.get("actions");
        actions.getFirst().put("type", "record_payment");
        assertThrows(RuntimeException.class, () ->
            assistant.update(text(p, "id"), Map.of("version", 1, "actions", actions))
        );
    }

    @Test
    void sourceDraftSurvivesParsingFailureAndProviderNeedsConsent() {
        assistant.saveDraft(1, "My unfinished notes");
        assertEquals(
            "My unfinished notes",
            text((Map<String, Object>) assistant.history(1).get("draft"), "source")
        );
        assertThrows(RuntimeException.class, () ->
            assistant.propose(
                Map.of(
                    "contract_id",
                    1,
                    "source",
                    "Today 2h testing Audiom",
                    "reference_date",
                    date.toString(),
                    "provider_id",
                    "missing"
                )
            )
        );
    }

    @Test
    void duplicateUntimedEntriesAreRejected() {
        var p = propose("Today 2h testing Audiom");
        assistant.apply(text(p, "id"), Map.of("version", 1, "confirmed", true));
        var second = propose("Today 2h testing Audiom");
        assertThrows(RuntimeException.class, () ->
            assistant.apply(text(second, "id"), Map.of("version", 1, "confirmed", true))
        );
    }
}
