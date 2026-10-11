package com.cognition.est.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cognition.est.app.crypto.TokenCrypto;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Linking a second ("partner") calendar: PKCE state round trip, encrypted token at rest, first
 * import, unlink keeps evidence.
 */
@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:est_link;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class IdentityLinkTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired TokenCrypto crypto;
    private static final JsonMapper JSON = JsonMapper.shared();

    @Test
    void linkPartnerAccount_tokenEncrypted_firstSyncRuns_unlinkRevokesButKeepsEvents()
            throws Exception {
        String body =
                mvc.perform(post("/api/v1/dev/token").param("email", "priya@cognition.ai"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String auth = "Bearer " + JSON.readTree(body).get("accessToken").asString();

        assertThat(
                        JSON.readTree(
                                mvc.perform(
                                                get("/api/v1/calendar/identities")
                                                        .header("Authorization", auth))
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString()))
                .hasSize(1);

        String startBody =
                mvc.perform(
                                post("/api/v1/calendar/identities/link/start")
                                        .header("Authorization", auth))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        URI url = URI.create(JSON.readTree(startBody).get("authorizationUrl").asString());
        String state = UriComponentsBuilder.fromUri(url).build().getQueryParams().getFirst("state");
        assertThat(state).isNotBlank();
        assertThat(jdbc.sql("select count(*) from oauth_link_states").query(Integer.class).single())
                .isEqualTo(1);

        // Forged state is rejected; the real one links a partner account and triggers the first
        // import.
        String redirect =
                mvc.perform(
                                get("/api/v1/calendar/identities/link/callback")
                                        .param("code", "mock:priya.partner@cognition.ai")
                                        .param("state", "forged"))
                        .andExpect(status().isFound())
                        .andReturn()
                        .getResponse()
                        .getHeader("Location");
        assertThat(redirect).contains("error=");
        redirect =
                mvc.perform(
                                get("/api/v1/calendar/identities/link/callback")
                                        .param("code", "mock:priya.partner@cognition.ai")
                                        .param("state", state))
                        .andExpect(status().isFound())
                        .andReturn()
                        .getResponse()
                        .getHeader("Location");
        assertThat(redirect).startsWith("http://localhost:3100/settings/calendars?linked=");
        String identityId = redirect.substring(redirect.indexOf("linked=") + 7);
        assertThat(jdbc.sql("select count(*) from oauth_link_states").query(Integer.class).single())
                .isZero();

        JsonNode list =
                JSON.readTree(
                        mvc.perform(
                                        get("/api/v1/calendar/identities")
                                                .header("Authorization", auth))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(list).hasSize(2);
        JsonNode partner =
                list.valueStream()
                        .filter(i -> i.get("id").asString().equals(identityId))
                        .findFirst()
                        .orElseThrow();
        assertThat(partner.get("email").asString()).isEqualTo("priya.partner@cognition.ai");
        assertThat(partner.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(partner.has("syncToken")).isFalse();

        // Refresh token is stored encrypted, never in clear.
        String enc =
                jdbc.sql("select refresh_token_enc from member_identities where id = :id")
                        .param("id", identityId)
                        .query(String.class)
                        .single();
        String iv =
                jdbc.sql("select refresh_token_iv from member_identities where id = :id")
                        .param("id", identityId)
                        .query(String.class)
                        .single();
        assertThat(enc).doesNotContain("mock-refresh");
        assertThat(crypto.decrypt(new TokenCrypto.Encrypted(enc, iv)))
                .isEqualTo("mock-refresh-priya.partner@cognition.ai");

        // First sync ran for the new identity (no fixtures for that email → 0 events, status ok).
        JsonNode runs =
                JSON.readTree(
                        mvc.perform(get("/api/v1/calendar/sync-runs").header("Authorization", auth))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertThat(
                        runs.valueStream()
                                .anyMatch(
                                        r ->
                                                r.get("identityId").asString().equals(identityId)
                                                        && r.get("status").asString().equals("ok")))
                .isTrue();

        // Sync the primary identity so there is evidence, then unlink the partner: disabled, token
        // wiped, events untouched.
        mvc.perform(post("/api/v1/calendar/sync").header("Authorization", auth))
                .andExpect(status().isOk());
        int events = jdbc.sql("select count(*) from calendar_events").query(Integer.class).single();
        assertThat(events).isGreaterThan(0);
        mvc.perform(
                        delete("/api/v1/calendar/identities/" + identityId)
                                .header("Authorization", auth))
                .andExpect(status().isNoContent());
        assertThat(
                        jdbc.sql("select status from member_identities where id = :id")
                                .param("id", identityId)
                                .query(String.class)
                                .single())
                .isEqualTo("DISABLED");
        assertThat(
                        jdbc.sql("select refresh_token_enc from member_identities where id = :id")
                                .param("id", identityId)
                                .query(String.class)
                                .optional())
                .isEmpty();
        assertThat(jdbc.sql("select count(*) from calendar_events").query(Integer.class).single())
                .isEqualTo(events);

        // Someone else cannot unlink my identity.
        String alex =
                "Bearer "
                        + JSON.readTree(
                                        mvc.perform(
                                                        post("/api/v1/dev/token")
                                                                .param(
                                                                        "email",
                                                                        "alex@cognition.ai"))
                                                .andReturn()
                                                .getResponse()
                                                .getContentAsString())
                                .get("accessToken")
                                .asString();
        mvc.perform(
                        delete("/api/v1/calendar/identities/idn_mem_priya")
                                .header("Authorization", alex))
                .andExpect(status().isNotFound());
    }
}
