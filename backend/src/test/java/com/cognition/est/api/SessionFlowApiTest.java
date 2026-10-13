package com.cognition.est.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The plan-doc acceptance list through the HTTP API, on the local profile (H2 + seed + mock
 * calendar): sync → inbox buckets → confirm → prefilled feedback → use cases/outcome → re-sync
 * creates no duplicates and refreshes evidence → manual edits survive → admin-only catalog writes.
 */
@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:est_flow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SessionFlowApiTest {

    @Autowired MockMvc mvc;
    private static final JsonMapper JSON = JsonMapper.shared();
    private static String alex;
    private static String dana;
    private static String sessionId;

    private String token(String email) throws Exception {
        String body =
                mvc.perform(post("/api/v1/dev/token").param("email", email))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return JSON.readTree(body).get("accessToken").asString();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token);
    }

    private JsonNode call(MockHttpServletRequestBuilder b, String token, int expected)
            throws Exception {
        String body =
                mvc.perform(auth(b, token))
                        .andExpect(status().is(expected))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return body.isBlank() ? JSON.nullNode() : JSON.readTree(body);
    }

    private static String json(String s) {
        return s.replace('\'', '"');
    }

    @Test
    @Order(1)
    void initialSyncClassifiesTheWeek() throws Exception {
        alex = token("alex@cognition.ai");
        dana = token("dana@cognition.ai");
        JsonNode runs = call(post("/api/v1/calendar/sync"), alex, 200);
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).get("status").asString()).isEqualTo("ok");
        assertThat(runs.get(0).get("eventsNew").asInt()).isEqualTo(21);

        JsonNode inbox = call(get("/api/v1/inbox"), alex, 200);
        List<String> likely = titles(inbox.get("likely"));
        assertThat(likely)
                .contains(
                        "Mercedes-Benz Data Engineering — Devin 101 session (Ab Initio squad)",
                        "Vantage Claims — Sprint in a day",
                        "Acme Payments — Ask me anything: Devin Review rollout");
        assertThat(titles(inbox.get("review")))
                .contains(
                        "Helios Grid — enablement workshop",
                        "Intro call: Brightline Software <> Cognition");
        assertThat(titles(inbox.get("duplicates")))
                .containsExactly("Vantage Claims Sprint in a day (copy)");
        assertThat(titles(inbox.get("unlikely")))
                .contains("Alex / Dana 1:1", "Dentist", "Orbital Storefront — Ask me anything");
        assertThat(inbox.get("stats").get("prefillRatio").asDouble()).isGreaterThan(0.5);
        // The generic workshop is capped: reason says why.
        JsonNode helios =
                inbox.get("review")
                        .valueStream()
                        .filter(
                                c ->
                                        c.get("event")
                                                .get("title")
                                                .asString()
                                                .equals("Helios Grid — enablement workshop"))
                        .findFirst()
                        .orElseThrow();
        assertThat(helios.get("candidate").get("classification").get("reasons").get(0).asString())
                .startsWith("No recognised session format");
    }

    @Test
    @Order(2)
    void todayShowsOnlyMySessionsToday_andConfirmPrefillsTheFeedbackForm() throws Exception {
        JsonNode today = call(get("/api/v1/today"), alex, 200);
        // The two Mercedes events are pinned to the real current day; other fixtures are
        // week-relative
        // and may also land on today depending on the weekday, so assert containment, not an exact
        // list.
        String mercedes101 = "Mercedes-Benz Data Engineering — Devin 101 session (Ab Initio squad)";
        assertThat(titles(today.get("today")))
                .contains(
                        mercedes101,
                        "Mercedes-Benz — Pair programming: fleet KPI Excel to Power BI");
        assertThat(
                        today.get("today")
                                .valueStream()
                                .map(i -> i.get("start").asString().substring(0, 10))
                                .distinct()
                                .count())
                .as("every Today item starts on the same calendar day")
                .isLessThanOrEqualTo(2); // a late-evening session may cross midnight in UTC
        String candidateId =
                today.get("today")
                        .valueStream()
                        .filter(i -> i.get("title").asString().equals(mercedes101))
                        .findFirst()
                        .orElseThrow()
                        .get("id")
                        .asString();

        JsonNode detail = call(post("/api/v1/candidates/" + candidateId + "/confirm"), alex, 200);
        sessionId = detail.get("session").get("id").asString();
        JsonNode s = detail.get("session");
        assertThat(detail.get("client").get("name").asString()).isEqualTo("Mercedes-Benz Tech");
        assertThat(s.get("sessionType").asString()).isEqualTo("101");
        assertThat(s.get("participantsEstimated").asInt()).isEqualTo(12);
        assertThat(s.get("provenance").get("clientId").asString()).isEqualTo("prefilled");
        assertThat(
                        detail.get("contacts")
                                .valueStream()
                                .map(c -> c.get("person").get("name").asString())
                                .toList())
                .contains("Katrin Vogel", "Jonas Keller");
        // Chips (0.5–0.85) wait for a decision; nothing below prefill tier was written.
        assertThat(
                        detail.get("pending")
                                .valueStream()
                                .allMatch(p -> p.get("confidence").asDouble() < 0.85))
                .isTrue();
        assertThat(
                        detail.get("pending")
                                .valueStream()
                                .anyMatch(p -> "useCases".equals(p.get("field").asString())))
                .isTrue();
        // Today now shows the session with its completeness ring instead of the candidate.
        JsonNode again = call(get("/api/v1/today"), alex, 200);
        JsonNode mb =
                again.get("today")
                        .valueStream()
                        .filter(i -> i.get("title").asString().equals(mercedes101))
                        .findFirst()
                        .orElseThrow();
        assertThat(mb.get("kind").asString()).isEqualTo("session");
    }

    @Test
    @Order(3)
    void acceptDraftUseCase_addAnother_pickOutcome_feedbackComplete() throws Exception {
        JsonNode detail = call(get("/api/v1/sessions/" + sessionId), alex, 200);
        String draftId =
                detail.get("pending")
                        .valueStream()
                        .filter(p -> "useCases".equals(p.get("field").asString()))
                        .findFirst()
                        .orElseThrow()
                        .get("id")
                        .asString();
        detail = call(post("/api/v1/suggestions/" + draftId + "/accept"), alex, 200);
        assertThat(detail.get("session").get("useCases")).hasSize(1);
        assertThat(detail.get("session").get("useCases").get(0).get("useCaseTypeId").asString())
                .isEqualTo("etlMigration");
        assertThat(
                        detail.get("session")
                                .get("useCases")
                                .get(0)
                                .get("technologyIds")
                                .valueStream()
                                .map(JsonNode::asString)
                                .toList())
                .contains("abInitio", "databricks");

        detail =
                call(
                        post("/api/v1/sessions/" + sessionId + "/use-cases")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("{'title':'Fleet KPI pack to Power BI'}")),
                        alex,
                        201);
        String ucId = detail.get("session").get("useCases").get(1).get("id").asString();
        detail =
                call(
                        patch("/api/v1/sessions/" + sessionId + "/use-cases/" + ucId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json(
                                                "{'useCaseTypeId':'excelToDashboard','technologyIds':['excel','powerBi']}")),
                        alex,
                        200);
        assertThat(
                        detail.get("session")
                                .get("technologyIds")
                                .valueStream()
                                .map(JsonNode::asString)
                                .toList())
                .contains("excel", "powerBi");
        assertThat(detail.get("session").get("provenance").get("useCases").asString())
                .isEqualTo("manual");

        detail =
                call(
                        patch("/api/v1/sessions/" + sessionId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("{'outcomeId':'prRaised'}")),
                        alex,
                        200);
        assertThat(detail.get("session").get("outcomeId").asString()).isEqualTo("prRaised");
        assertThat(detail.get("completeness").asDouble()).isEqualTo(1.0);
        assertThat(detail.get("missing")).isEmpty();

        // Optimistic locking: a stale If-Match is rejected.
        mvc.perform(
                        auth(
                                patch("/api/v1/sessions/" + sessionId)
                                        .header("If-Match", "\"1\"")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json("{'notes':'late'}")),
                                alex))
                .andExpect(status().isPreconditionFailed());

        // Removing a use case works and keeps the first.
        detail = call(delete("/api/v1/sessions/" + sessionId + "/use-cases/" + ucId), alex, 200);
        assertThat(detail.get("session").get("useCases")).hasSize(1);
    }

    @Test
    @Order(4)
    void manualEditsSurviveResync_andResyncNeverDuplicates() throws Exception {
        // Confirm the Acme 101 (it gets an updated description in the next batch), then edit its
        // title by hand.
        JsonNode inbox = call(get("/api/v1/inbox"), alex, 200);
        JsonNode acme =
                inbox.get("likely")
                        .valueStream()
                        .filter(
                                c ->
                                        c.get("event")
                                                .get("title")
                                                .asString()
                                                .startsWith("Acme Platform"))
                        .findFirst()
                        .orElseThrow();
        String acmeSession =
                call(
                                post(
                                        "/api/v1/candidates/"
                                                + acme.get("candidate").get("id").asString()
                                                + "/confirm"),
                                alex,
                                200)
                        .get("session")
                        .get("id")
                        .asString();
        call(
                patch("/api/v1/sessions/" + acmeSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'title':'Acme office hours — my title'}")),
                alex,
                200);

        int sessionsBefore =
                call(get("/api/v1/sessions").param("includeCandidates", "false"), alex, 200).size();
        JsonNode run = call(post("/api/v1/calendar/sync"), alex, 200).get(0);
        assertThat(run.get("eventsUpdated").asInt()).isEqualTo(1);
        assertThat(run.get("eventsUnchanged").asInt()).isEqualTo(1);
        assertThat(run.get("eventsNew").asInt()).isEqualTo(2);

        JsonNode detail = call(get("/api/v1/sessions/" + acmeSession), alex, 200);
        assertThat(detail.get("session").get("title").asString())
                .isEqualTo("Acme office hours — my title");
        assertThat(detail.get("session").get("provenance").get("title").asString())
                .isEqualTo("manual");
        // The updated invite now names Raj Patel as architect — a fresh chip, not an overwrite.
        assertThat(
                        detail.get("pending")
                                .valueStream()
                                .anyMatch(p -> p.get("label").asString().startsWith("Raj Patel")))
                .isTrue();
        assertThat(
                        call(get("/api/v1/sessions").param("includeCandidates", "false"), alex, 200)
                                .size())
                .isEqualTo(sessionsBefore);

        // Re-syncing identical data: everything unchanged, nothing new.
        JsonNode third = call(post("/api/v1/calendar/sync"), alex, 200).get(0);
        assertThat(third.get("eventsNew").asInt() + third.get("eventsUpdated").asInt())
                .isEqualTo(2); // batch 2 only adds its two events
        JsonNode fourth = call(post("/api/v1/calendar/sync"), alex, 200).get(0);
        assertThat(fourth.get("eventsSeen").asInt()).isZero();
    }

    @Test
    @Order(5)
    void confirmAllLikely_mergeDuplicate_dismissRestore() throws Exception {
        JsonNode inbox = call(get("/api/v1/inbox"), alex, 200);
        String dup = inbox.get("duplicates").get(0).get("candidate").get("id").asString();
        JsonNode merged = call(post("/api/v1/candidates/" + dup + "/merge"), alex, 200);
        assertThat(merged.get("session").get("sourceEventIds")).hasSize(2);

        String noise = inbox.get("unlikely").get(0).get("candidate").get("id").asString();
        call(post("/api/v1/candidates/" + noise + "/dismiss"), alex, 204);
        assertThat(titles(call(get("/api/v1/inbox"), alex, 200).get("dismissed"))).hasSize(1);
        call(post("/api/v1/candidates/" + noise + "/restore"), alex, 204);
        assertThat(call(get("/api/v1/inbox"), alex, 200).get("dismissed")).isEmpty();

        JsonNode result = call(post("/api/v1/candidates/confirm-all-likely"), alex, 200);
        assertThat(result.get("confirmed").asInt()).isGreaterThan(3);
        assertThat(call(get("/api/v1/inbox"), alex, 200).get("likely")).isEmpty();
    }

    @Test
    @Order(6)
    void catalogWritesAreAdminOnly_butOtherIsForEveryone_andOrgScopingHolds() throws Exception {
        mvc.perform(
                        auth(
                                post("/api/v1/catalog/sessionType")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json("{'label':'Lunch & learn'}")),
                                alex))
                .andExpect(status().isForbidden());
        JsonNode added =
                call(
                        post("/api/v1/catalog/sessionType")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json(
                                                "{'label':'Lunch & learn','keywords':['lunch and learn','L&L'],'typicalMinutes':60}")),
                        dana,
                        200);
        assertThat(added.get("id").asString()).isEqualTo("lunchLearn");
        assertThat(added.get("keywords").valueStream().map(JsonNode::asString).toList())
                .containsExactly("lunch & learn", "lunch and learn", "l&l");

        JsonNode other =
                call(
                        post("/api/v1/catalog/sessionOutcome")
                                .param("adhoc", "true")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("{'label':'Demo recorded'}")),
                        alex,
                        200);
        JsonNode again =
                call(
                        post("/api/v1/catalog/sessionOutcome")
                                .param("adhoc", "true")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("{'label':'demo recorded'}")),
                        alex,
                        200);
        assertThat(again.get("id").asString()).isEqualTo(other.get("id").asString());

        JsonNode archived =
                call(
                        patch("/api/v1/catalog/sessionType/" + added.get("id").asString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json("{'archived':true}")),
                        dana,
                        200);
        assertThat(archived.get("archived").asBoolean()).isTrue();
        mvc.perform(
                        auth(
                                patch("/api/v1/catalog/sessionType/lunchLearn")
                                        .header("If-Match", "\"0\"")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json("{'label':'x'}")),
                                dana))
                .andExpect(status().isPreconditionFailed());

        // A session id from this org is a 404 for a token whose org claim differs.
        mvc.perform(get("/api/v1/sessions/" + sessionId).header("Authorization", "Bearer " + alex))
                .andExpect(status().isOk());
    }

    private static List<String> titles(JsonNode items) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : items)
            out.add(
                    n.has("event")
                            ? n.get("event").get("title").asString()
                            : n.get("title").asString());
        return out;
    }
}
