package com.cognition.est.calendar;

import com.cognition.est.app.config.EstProperties;
import com.cognition.est.app.crypto.TokenCrypto;
import com.cognition.est.domain.model.Attendee;
import com.cognition.est.domain.model.Member;
import com.cognition.est.domain.repo.IdentityRepository;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.Events;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Google Calendar v3 with incremental sync: the first run is a bounded lookback (-30d … +60d,
 * recurrences expanded), later runs send the stored syncToken and receive only changes. A 410 from
 * Google means the token expired → full resync. Refresh tokens are decrypted only here.
 */
@Component
@ConditionalOnProperty(
        prefix = "est.google",
        name = "mock",
        havingValue = "false",
        matchIfMissing = true)
public class GoogleCalendarProvider implements CalendarProvider {
    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarProvider.class);
    private static final Duration LOOKBACK = Duration.ofDays(30);
    private static final Duration LOOKAHEAD = Duration.ofDays(60);

    private final EstProperties props;
    private final IdentityRepository identities;
    private final TokenCrypto crypto;

    public GoogleCalendarProvider(
            EstProperties props, IdentityRepository identities, TokenCrypto crypto) {
        this.props = props;
        this.identities = identities;
        this.crypto = crypto;
    }

    @Override
    public String name() {
        return "google";
    }

    @Override
    public Page fetch(IdentityRef identity, Member owner, Instant now)
            throws CalendarAccessException {
        var enc =
                identities
                        .refreshToken(identity.id())
                        .orElseThrow(
                                () ->
                                        new CalendarAccessException(
                                                "No refresh token stored for this calendar; re-link it.",
                                                true,
                                                null));
        String refreshToken = crypto.decrypt(new TokenCrypto.Encrypted(enc.ciphertext(), enc.iv()));
        Calendar calendar;
        try {
            UserCredentials creds =
                    UserCredentials.newBuilder()
                            .setClientId(props.google().clientId())
                            .setClientSecret(props.google().clientSecret())
                            .setRefreshToken(refreshToken)
                            .build();
            HttpRequestInitializer init = new HttpCredentialsAdapter(creds);
            calendar =
                    new Calendar.Builder(
                                    GoogleNetHttpTransport.newTrustedTransport(),
                                    GsonFactory.getDefaultInstance(),
                                    init)
                            .setApplicationName("Enablement Session Tracker")
                            .build();
        } catch (Exception e) {
            throw new CalendarAccessException("Could not build Google Calendar client", false, e);
        }
        try {
            return list(calendar, identity.syncToken(), now, false);
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 410) {
                log.info(
                        "sync token expired for identity {}; running a full resync", identity.id());
                try {
                    return list(calendar, null, now, true);
                } catch (IOException inner) {
                    throw new CalendarAccessException(
                            "Full resync failed: " + inner.getMessage(), false, inner);
                }
            }
            boolean reauth =
                    e.getStatusCode() == 401
                            || (e.getDetails() != null
                                    && e.getDetails().getMessage() != null
                                    && e.getDetails().getMessage().contains("invalid_grant"));
            throw new CalendarAccessException(
                    "Google Calendar error " + e.getStatusCode(), reauth, e);
        } catch (IOException e) {
            boolean reauth = e.getMessage() != null && e.getMessage().contains("invalid_grant");
            throw new CalendarAccessException(
                    reauth
                            ? "Google access was revoked; re-link this calendar."
                            : "Google Calendar unreachable: " + e.getMessage(),
                    reauth,
                    e);
        }
    }

    private Page list(Calendar calendar, String syncToken, Instant now, boolean fullResync)
            throws IOException {
        List<ProviderEvent> out = new ArrayList<>();
        String pageToken = null;
        String nextSyncToken = null;
        do {
            Calendar.Events.List req =
                    calendar.events()
                            .list("primary")
                            .setMaxResults(250)
                            .setPageToken(pageToken)
                            .setShowDeleted(true);
            if (syncToken != null) {
                req.setSyncToken(syncToken);
            } else {
                req.setSingleEvents(true)
                        .setTimeMin(new DateTime(now.minus(LOOKBACK).toEpochMilli()))
                        .setTimeMax(new DateTime(now.plus(LOOKAHEAD).toEpochMilli()));
            }
            Events page = req.execute();
            for (Event e : page.getItems() == null ? List.<Event>of() : page.getItems()) {
                ProviderEvent pe = map(e, page.getTimeZone());
                if (pe != null) out.add(pe);
            }
            pageToken = page.getNextPageToken();
            if (page.getNextSyncToken() != null) nextSyncToken = page.getNextSyncToken();
        } while (pageToken != null);
        return new Page(out, nextSyncToken, fullResync);
    }

    static ProviderEvent map(Event e, String calendarTz) {
        Instant start = when(e.getStart());
        Instant end = when(e.getEnd());
        if (start == null || end == null)
            return null; // deleted instances without times carry nothing we can use
        List<Attendee> attendees = new ArrayList<>();
        for (EventAttendee a :
                e.getAttendees() == null ? List.<EventAttendee>of() : e.getAttendees()) {
            if (a.getEmail() == null) continue;
            attendees.add(
                    new Attendee(
                            a.getEmail(),
                            a.getDisplayName(),
                            a.getResponseStatus(),
                            Boolean.TRUE.equals(a.getOrganizer())));
        }
        String conferencing = e.getHangoutLink();
        if (conferencing == null
                && e.getConferenceData() != null
                && e.getConferenceData().getEntryPoints() != null
                && !e.getConferenceData().getEntryPoints().isEmpty()) {
            conferencing = e.getConferenceData().getEntryPoints().getFirst().getUri();
        }
        String tz =
                e.getStart() != null && e.getStart().getTimeZone() != null
                        ? e.getStart().getTimeZone()
                        : (calendarTz == null ? "UTC" : calendarTz);
        return new ProviderEvent(
                e.getId(),
                e.getRecurringEventId(),
                e.getSummary() == null ? "" : e.getSummary(),
                e.getDescription() == null ? "" : e.getDescription(),
                e.getLocation(),
                conferencing,
                start,
                end,
                tz,
                e.getOrganizer() == null ? null : e.getOrganizer().getEmail(),
                attendees,
                e.getEtag(),
                "cancelled".equals(e.getStatus()) ? "cancelled" : "confirmed",
                e.toString());
    }

    private static Instant when(com.google.api.services.calendar.model.EventDateTime dt) {
        if (dt == null) return null;
        if (dt.getDateTime() != null) return Instant.ofEpochMilli(dt.getDateTime().getValue());
        if (dt.getDate() != null) return Instant.ofEpochMilli(dt.getDate().getValue());
        return null;
    }
}
