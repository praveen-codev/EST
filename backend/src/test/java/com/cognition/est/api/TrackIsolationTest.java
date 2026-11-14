package com.cognition.est.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cognition.est.app.config.EstProperties;
import com.cognition.est.calendar.IdentityService;
import com.cognition.est.calendar.SyncService;
import com.cognition.est.domain.repo.IdentityRepository;
import com.cognition.est.domain.repo.SyncRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TrackIsolationTest {
    @Test
    void publicFeaturesIdentifySandboxAndReplicationMode() {
        EstProperties props = mock(EstProperties.class);
        EstProperties.Dev dev = mock(EstProperties.Dev.class);
        EstProperties.Google google = mock(EstProperties.Google.class);
        when(props.track()).thenReturn("sandbox");
        when(props.calendarSyncEnabled()).thenReturn(false);
        when(props.dev()).thenReturn(dev);
        when(props.google()).thenReturn(google);
        when(dev.endpointsEnabled()).thenReturn(false);
        when(google.mock()).thenReturn(false);
        when(google.clientId()).thenReturn("client");

        var features = new FeaturesController(props).features();

        assertThat(features)
                .containsEntry("track", "sandbox")
                .containsEntry("calendarSyncEnabled", false);
    }

    @Test
    void sandboxRejectsDirectCalendarSyncBeforeResolvingTheCaller() {
        EstProperties props = mock(EstProperties.class);
        when(props.calendarSyncEnabled()).thenReturn(false);
        var controller =
                new CalendarSyncController(
                        mock(SyncService.class), mock(SyncRunRepository.class), props);

        assertThatThrownBy(controller::syncNow)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void sandboxRejectsCalendarLinkingBeforeResolvingTheCaller() {
        EstProperties props = mock(EstProperties.class);
        when(props.calendarSyncEnabled()).thenReturn(false);
        var controller =
                new CalendarIdentityController(
                        mock(IdentityService.class),
                        mock(IdentityRepository.class),
                        mock(SyncService.class),
                        props);

        assertThatThrownBy(controller::start)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
