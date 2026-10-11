package com.cognition.est.domain.model;

import java.time.Instant;
import java.util.List;

/** Evidence layer: a calendar event as last seen by sync. Never edited by humans. */
public record CalendarEvent(
        String id,
        String identityId,
        String ownerMemberId,
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
        String contentHash,
        String status) {
    public boolean cancelled() {
        return "cancelled".equals(status);
    }
}
