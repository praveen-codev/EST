package com.cognition.est.calendar;

import com.cognition.est.domain.model.Attendee;
import java.time.Instant;
import java.util.List;

/** What any calendar provider returns for one event, before it becomes evidence. */
public record ProviderEvent(
        String providerEventId,
        String recurringEventId,
        String title,
        String description,
        String location,
        String conferencing,
        Instant start,
        Instant end,
        String timeZone,
        String organizerEmail,
        List<Attendee> attendees,
        String etag,
        String status,
        String raw) {}
