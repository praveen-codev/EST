package com.cognition.est.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** Dev endpoints and mock Google must never run against production data. */
@SpringBootTest(
        properties =
                "spring.datasource.url=jdbc:h2:mem:est_track_guard;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@ActiveProfiles("local")
class TrackGuardTest {
    @Autowired JdbcClient jdbc;

    private static EstProperties props(String track, boolean devEndpoints, boolean mockGoogle) {
        EstProperties props = mock(EstProperties.class, RETURNS_DEEP_STUBS);
        when(props.track()).thenReturn(track);
        when(props.dev().endpointsEnabled()).thenReturn(devEndpoints);
        when(props.google().mock()).thenReturn(mockGoogle);
        return props;
    }

    @Test
    void prodTrackRejectsDevEndpointsAndMockGoogle() {
        assertThatThrownBy(() -> TrackGuard.check(props("prod", true, false)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TrackGuard.check(props("prod", false, true)))
                .isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> TrackGuard.check(props("prod", false, false)))
                .doesNotThrowAnyException();
        assertThatCode(() -> TrackGuard.check(props("sandbox", true, true)))
                .doesNotThrowAnyException();
    }

    @Test
    void databaseStaysBoundToTheTrackThatClaimedItFirst() {
        assertThat(TrackGuard.claimed(jdbc)).contains("sandbox");
        assertThatCode(() -> TrackGuard.claim(jdbc, "sandbox", Instant.now()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> TrackGuard.claim(jdbc, "prod", Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("belongs to the sandbox track");
    }
}
