package com.cognition.est.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** /dev/reset must wipe everything the demo touches and re-seed, so e2e runs start clean. */
@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:est_reset;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class DevResetTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @Test
    void resetAfterSyncAndSignInReseeds() throws Exception {
        String body =
                mvc.perform(post("/api/v1/dev/login-as").param("email", "alex@cognition.ai"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String token =
                tools.jackson.databind.json.JsonMapper.shared()
                        .readTree(body)
                        .get("accessToken")
                        .asString();
        mvc.perform(post("/api/v1/calendar/sync").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(jdbc.sql("select count(*) from calendar_events").query(Integer.class).single())
                .isGreaterThan(0);
        mvc.perform(post("/api/v1/dev/reset")).andExpect(status().isNoContent());
        assertThat(jdbc.sql("select count(*) from calendar_events").query(Integer.class).single())
                .isZero();
        assertThat(jdbc.sql("select count(*) from members").query(Integer.class).single())
                .isEqualTo(5);
        assertThat(jdbc.sql("select count(*) from sessions").query(Integer.class).single())
                .isEqualTo(16);
    }
}
