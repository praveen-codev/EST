package com.cognition.est.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Boots the local profile (H2 + Flyway + seed) and exercises the public/protected boundary. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ApiSmokeTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @Test
    void flywayMigratesAndLocalSeedApplies() {
        assertThat(
                        jdbc.sql("select count(*) from catalog_items where kind = 'sessionType'")
                                .query(Integer.class)
                                .single())
                .isEqualTo(5);
        assertThat(jdbc.sql("select count(*) from members").query(Integer.class).single())
                .isEqualTo(5);
        assertThat(jdbc.sql("select count(*) from sessions").query(Integer.class).single())
                .isEqualTo(16);
        assertThat(jdbc.sql("select count(*) from use_cases").query(Integer.class).single())
                .isGreaterThan(16);
    }

    @Test
    void healthAndDocsArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void catalogRequiresABearerToken() throws Exception {
        mvc.perform(get("/api/v1/catalog/sessionType")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPublicRouteIsA404NotA500() throws Exception {
        mvc.perform(get("/api/v1/dev/nope")).andExpect(status().isNotFound());
    }

    @Test
    void mintedTokenReadsTheCallersOrgCatalog() throws Exception {
        String body =
                mvc.perform(post("/api/v1/dev/token").param("email", "alex@cognition.ai"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode json = JsonMapper.shared().readTree(body);
        String token = json.get("accessToken").asString();

        mvc.perform(get("/api/v1/catalog/sessionType").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].id").value("101"))
                .andExpect(jsonPath("$[0].keywords").isArray());

        mvc.perform(get("/api/v1/catalog/nonsense").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"));
    }
}
