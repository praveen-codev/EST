package com.cognition.est.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Rate limiting, security headers and the "no secrets on the wire" rule. */
@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:est_hard;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class HardeningTest {
    @Autowired MockMvc mvc;

    @Test
    void authEndpointsAreRateLimitedPerClient() throws Exception {
        int limited = 0;
        for (int i = 0; i < 40; i++) {
            int status =
                    mvc.perform(
                                    post("/api/v1/auth/google")
                                            .with(
                                                    r -> {
                                                        r.setRemoteAddr("10.9.9.9");
                                                        return r;
                                                    })
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{\"idToken\":\"nope\"}"))
                            .andReturn()
                            .getResponse()
                            .getStatus();
            if (status == 429) limited++;
        }
        assertThat(limited).isGreaterThan(5);
        // Another client is unaffected.
        mvc.perform(
                        post("/api/v1/auth/google")
                                .with(
                                        r -> {
                                            r.setRemoteAddr("10.1.1.1");
                                            return r;
                                        })
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"idToken\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/api/v1/features"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(
                        header().string(
                                        "Content-Security-Policy",
                                        "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Permissions-Policy"));
    }

    @Test
    void noTokenMaterialOnAnyReadEndpoint() throws Exception {
        String body =
                mvc.perform(
                                post("/api/v1/dev/login-as")
                                        .param("email", "priya@cognition.ai")
                                        .with(
                                                r -> {
                                                    r.setRemoteAddr("10.2.2.2");
                                                    return r;
                                                }))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String token = JsonMapper.shared().readTree(body).get("accessToken").asString();
        mvc.perform(post("/api/v1/calendar/sync").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        for (String path :
                new String[] {
                    "/api/v1/snapshot",
                    "/api/v1/me",
                    "/api/v1/calendar/identities",
                    "/api/v1/calendar/sync-runs",
                    "/api/v1/inbox",
                    "/api/v1/today"
                }) {
            String out =
                    mvc.perform(get(path).header("Authorization", "Bearer " + token))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertThat(out)
                    .as(path)
                    .doesNotContainIgnoringCase("refresh_token")
                    .doesNotContain(
                            "refreshToken",
                            "syncToken",
                            "mock-refresh",
                            "client_secret",
                            "clientSecret");
        }
    }
}
