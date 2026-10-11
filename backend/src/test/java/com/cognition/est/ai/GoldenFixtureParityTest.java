package com.cognition.est.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.cognition.est.domain.model.Attendee;
import com.cognition.est.domain.model.CalendarEvent;
import com.cognition.est.domain.model.Person;
import com.cognition.est.domain.model.Suggestion;
import com.cognition.est.domain.model.UseCase;
import com.cognition.est.domain.repo.JsonCodec;
import com.cognition.est.domain.service.EngineContextFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Replays contracts/ai-fixtures/alex-messy.json (exported from the TypeScript engine) against the
 * Java port using the same seed. Every classification, redaction list and suggestion signature must
 * match exactly — this is the proof that the port is faithful.
 */
@SpringBootTest
@ActiveProfiles("local")
class GoldenFixtureParityTest {

    @Autowired EngineContextFactory contexts;
    @Autowired JsonCodec codec;

    @Test
    void javaEngineMatchesTheTypeScriptGoldenOutput() throws Exception {
        Path file = Path.of("..", "contracts", "ai-fixtures", "alex-messy.json");
        JsonNode root = JsonMapper.shared().readTree(Files.readString(file));
        EngineContext ctx = contexts.forMember("org_cognition", "mem_alex");
        Map<String, String> emailByPersonId = new LinkedHashMap<>();
        ctx.people().forEach(p -> emailByPersonId.put(p.id(), p.email()));

        List<String> failures = new ArrayList<>();
        int cases = 0;
        for (JsonNode c : root.get("cases")) {
            cases++;
            String pid = c.get("providerEventId").asString();
            CalendarEvent event = toEvent(c.get("event"), pid);
            SuggestionEngine.Result result =
                    SuggestionEngine.analyze(event, "cand_" + pid, ctx, codec);
            // Unknown attendees are created by the engine; mirror that for signature lookups.
            for (Person p : result.newPeople()) emailByPersonId.put(p.id(), p.email());

            JsonNode exp = c.get("expected");
            String expectedClass = canonical(exp.get("classification"));
            String actualClass =
                    canonical(JsonMapper.shared().valueToTree(result.classification()));
            if (!expectedClass.equals(actualClass))
                failures.add(
                        pid
                                + " classification\n  expected "
                                + expectedClass
                                + "\n  actual   "
                                + actualClass);
            String expectedRed = canonical(exp.get("redactions"));
            String actualRed = canonical(JsonMapper.shared().valueToTree(result.redactions()));
            if (!expectedRed.equals(actualRed))
                failures.add(pid + " redactions expected " + expectedRed + " actual " + actualRed);

            List<String> expectedSigs = new ArrayList<>();
            exp.get("suggestions").forEach(s -> expectedSigs.add(canonical(s)));
            List<String> actualSigs =
                    result.suggestions().stream()
                            .map(s -> canonical(signature(s, emailByPersonId)))
                            .sorted()
                            .toList();
            List<String> expectedSorted = expectedSigs.stream().sorted().toList();
            if (!expectedSorted.equals(actualSigs)) {
                List<String> missing =
                        expectedSorted.stream().filter(s -> !actualSigs.contains(s)).toList();
                List<String> extra =
                        actualSigs.stream().filter(s -> !expectedSorted.contains(s)).toList();
                failures.add(
                        pid
                                + " suggestions differ\n  missing: "
                                + String.join("\n           ", missing)
                                + "\n  extra:   "
                                + String.join("\n           ", extra));
            }
        }
        assertThat(cases).isGreaterThan(15);
        assertThat(failures).as(String.join("\n\n", failures)).isEmpty();
    }

    private static CalendarEvent toEvent(JsonNode e, String providerEventId) {
        List<Attendee> attendees = new ArrayList<>();
        for (JsonNode a : e.get("attendees")) {
            attendees.add(
                    new Attendee(
                            a.get("email").asString(),
                            text(a, "name"),
                            text(a, "responseStatus"),
                            a.path("organizer").asBoolean(false)));
        }
        return new CalendarEvent(
                "evt_" + providerEventId,
                "idn_mock",
                "mem_alex",
                providerEventId,
                text(e, "recurringEventId"),
                e.get("title").asString(),
                e.get("description").asString(),
                text(e, "location"),
                text(e, "conferencing"),
                Instant.parse(e.get("start").asString()),
                Instant.parse(e.get("end").asString()),
                "UTC",
                text(e, "organizerEmail"),
                attendees,
                null,
                "hash",
                e.get("status").asString());
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }

    /** Mirrors `signature()` in web/tests/fixtures/export-ai-fixtures.test.ts. */
    private static JsonNode signature(Suggestion s, Map<String, String> emailByPersonId) {
        JsonMapper m = JsonMapper.shared();
        Map<String, Object> sig = new LinkedHashMap<>();
        sig.put("field", s.field());
        sig.put("label", s.label());
        sig.put("confidence", s.confidence());
        Object value = null;
        switch (s.field()) {
            case "title", "sessionType", "participantsEstimated", "summary", "technologyIds" ->
                    value = s.value();
            case "useCases" -> {
                UseCase uc = (UseCase) s.value();
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("title", uc.title());
                v.put("useCaseTypeId", uc.useCaseTypeId());
                v.put("technologyIds", uc.technologyIds().stream().sorted().toList());
                value = v;
            }
            case "participantIds" -> {
                @SuppressWarnings("unchecked")
                List<String> ids = (List<String>) s.value();
                value =
                        ids.stream()
                                .map(id -> emailByPersonId.getOrDefault(id, id))
                                .sorted()
                                .toList();
            }
            case "contacts" -> value = null;
            default -> value = null;
        }
        sig.put("value", value);
        sig.put("evidence", s.evidence());
        return m.valueToTree(sig);
    }

    /** Canonical JSON: sorted keys, nulls dropped (the TS export omits undefined attribution). */
    private static String canonical(JsonNode n) {
        return JsonMapper.shared().writeValueAsString(sortAndStrip(n));
    }

    private static JsonNode sortAndStrip(JsonNode n) {
        JsonMapper m = JsonMapper.shared();
        if (n.isObject()) {
            var out = m.createObjectNode();
            List<String> names = new ArrayList<>();
            n.propertyNames().forEach(names::add);
            names.sort(Comparator.naturalOrder());
            for (String name : names) {
                JsonNode v = n.get(name);
                if (v == null || v.isNull()) continue;
                out.set(name, sortAndStrip(v));
            }
            return out;
        }
        if (n.isArray()) {
            var out = m.createArrayNode();
            n.forEach(v -> out.add(sortAndStrip(v)));
            return out;
        }
        if (n.isNumber()) {
            return m.getNodeFactory()
                    .numberNode(new java.math.BigDecimal(n.asString()).stripTrailingZeros());
        }
        return n;
    }
}
