package com.cognition.est.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sign-in, refresh rotation, reuse detection, logout, pending approval and admin-only member
 * management — all on the mock Google verifier (local profile), own H2 database.
 */
@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:est_auth;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthFlowTest {

    @Autowired MockMvc mvc;
    private static final JsonMapper JSON = JsonMapper.shared();

    private MockHttpServletResponse signIn(String mockToken, int expected) throws Exception {
        return mvc.perform(
                        post("/api/v1/auth/google")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"idToken\":\"" + mockToken + "\"}"))
                .andExpect(status().is(expected))
                .andReturn()
                .getResponse();
    }

    private static Cookie cookie(MockHttpServletResponse res, String name) {
        return Arrays.stream(res.getCookies())
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void
            signInIssuesAccessTokenAndHttpOnlyRefreshCookie_refreshRotates_reuseRevokesFamily_logoutClears()
                    throws Exception {
        MockHttpServletResponse res = signIn("mock:alex@cognition.ai", 200);
        JsonNode body = JSON.readTree(res.getContentAsString());
        assertThat(body.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(body.get("member").get("email").asString()).isEqualTo("alex@cognition.ai");
        Cookie refresh = cookie(res, AuthCookies.REFRESH);
        Cookie csrf = cookie(res, AuthCookies.CSRF);
        assertThat(refresh.isHttpOnly()).isTrue();
        assertThat(csrf.isHttpOnly()).isFalse();
        assertThat(refresh.getPath()).isEqualTo("/api/v1/auth");
        String setCookie = String.join(" ", res.getHeaders("Set-Cookie"));
        assertThat(setCookie).contains("SameSite=Strict");

        // Access token works on protected routes.
        mvc.perform(
                        get("/api/v1/me")
                                .header(
                                        "Authorization",
                                        "Bearer " + body.get("accessToken").asString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.member.name").value("Alex Rivera"));

        // Refresh without the CSRF header is rejected; with it, the token rotates.
        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh, csrf))
                .andExpect(status().isUnauthorized());
        MockHttpServletResponse rotated =
                mvc.perform(
                                post("/api/v1/auth/refresh")
                                        .cookie(refresh, csrf)
                                        .header("X-CSRF", csrf.getValue()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse();
        Cookie refresh2 = cookie(rotated, AuthCookies.REFRESH);
        Cookie csrf2 = cookie(rotated, AuthCookies.CSRF);
        assertThat(refresh2.getValue()).isNotEqualTo(refresh.getValue());

        // Presenting the old (rotated) token again is reuse → the whole family is revoked,
        // including the new token.
        mvc.perform(
                        post("/api/v1/auth/refresh")
                                .cookie(refresh, csrf)
                                .header("X-CSRF", csrf.getValue()))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/v1/auth/refresh")
                                .cookie(refresh2, csrf2)
                                .header("X-CSRF", csrf2.getValue()))
                .andExpect(status().isUnauthorized());

        // Fresh sign-in, then logout revokes and clears.
        MockHttpServletResponse again = signIn("mock:alex@cognition.ai", 200);
        Cookie r3 = cookie(again, AuthCookies.REFRESH);
        Cookie c3 = cookie(again, AuthCookies.CSRF);
        MockHttpServletResponse out =
                mvc.perform(
                                post("/api/v1/auth/logout")
                                        .cookie(r3, c3)
                                        .header("X-CSRF", c3.getValue()))
                        .andExpect(status().isNoContent())
                        .andReturn()
                        .getResponse();
        assertThat(cookie(out, AuthCookies.REFRESH).getMaxAge()).isZero();
        mvc.perform(post("/api/v1/auth/refresh").cookie(r3, c3).header("X-CSRF", c3.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void
            unknownAccountInAllowedDomainIsPendingUntilAnAdminApproves_otherDomainsRejected_tokensNeverLeakIdentitySecrets()
                    throws Exception {
        signIn("mock:newbie@othercorp.com", 403);
        MockHttpServletResponse pending = signIn("mock:newbie@cognition.ai:New Person", 403);
        assertThat(pending.getContentAsString()).contains("waiting for an admin");

        String dana =
                JSON.readTree(signIn("mock:dana@cognition.ai", 200).getContentAsString())
                        .get("accessToken")
                        .asString();
        String alex =
                JSON.readTree(signIn("mock:alex@cognition.ai", 200).getContentAsString())
                        .get("accessToken")
                        .asString();
        mvc.perform(get("/api/v1/admin/members").header("Authorization", "Bearer " + alex))
                .andExpect(status().isForbidden());
        JsonNode members =
                JSON.readTree(
                        mvc.perform(
                                        get("/api/v1/admin/members")
                                                .header("Authorization", "Bearer " + dana))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        JsonNode newbie =
                members.valueStream()
                        .filter(m -> m.get("email").asString().equals("newbie@cognition.ai"))
                        .findFirst()
                        .orElseThrow();
        assertThat(newbie.get("status").asString()).isEqualTo("PENDING");

        mvc.perform(
                        patch("/api/v1/admin/members/" + newbie.get("id").asString())
                                .header("Authorization", "Bearer " + dana)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        signIn("mock:newbie@cognition.ai", 200);

        // Admins cannot suspend themselves; /me never includes token material.
        mvc.perform(
                        patch("/api/v1/admin/members/mem_dana")
                                .header("Authorization", "Bearer " + dana)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isBadRequest());
        String me =
                mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + alex))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(me).doesNotContain("refresh_token", "syncToken", "refreshToken");
        assertThat(JSON.readTree(me).get("identities")).hasSize(1);
    }

    @Test
    void garbageIdTokenIs401() throws Exception {
        signIn("not-a-token", 401);
    }
}
