package com.cognition.est.calendar;

import com.cognition.est.ai.NormalizedEvent;
import com.cognition.est.domain.model.Attendee;
import com.cognition.est.domain.model.CalendarEvent;
import com.cognition.est.domain.model.Ids;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Port of web/lib/sync/sync.ts: ids, content hash and duplicate detection. */
public final class EventFingerprint {
    private EventFingerprint() {}

    public static String contentHash(
            String title, Instant start, Instant end, List<Attendee> attendees) {
        String external =
                attendees.stream()
                        .map(a -> a.email().toLowerCase(Locale.ROOT))
                        .filter(
                                e ->
                                        !NormalizedEvent.domainOf(e)
                                                .equals(NormalizedEvent.ORG_DOMAIN))
                        .sorted()
                        .collect(Collectors.joining(","));
        String t = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        return Ids.hash(t + "|" + start + "|" + end + "|" + external);
    }

    public static String eventIdFor(String ownerMemberId, String providerEventId) {
        return Ids.stableId("evt", ownerMemberId + "/" + providerEventId);
    }

    public static String candidateIdFor(String ownerMemberId, String providerEventId) {
        return Ids.stableId("cand", ownerMemberId + "/" + providerEventId);
    }

    public static CalendarEvent toCalendarEvent(
            ProviderEvent p, String identityId, String ownerMemberId) {
        return new CalendarEvent(
                eventIdFor(ownerMemberId, p.providerEventId()),
                identityId,
                ownerMemberId,
                p.providerEventId(),
                p.recurringEventId(),
                p.title() == null ? "" : p.title(),
                p.description() == null ? "" : p.description(),
                p.location(),
                p.conferencing(),
                p.start(),
                p.end(),
                p.timeZone() == null ? "UTC" : p.timeZone(),
                p.organizerEmail(),
                p.attendees(),
                p.etag(),
                contentHash(p.title() == null ? "" : p.title(), p.start(), p.end(), p.attendees()),
                p.status() == null ? "confirmed" : p.status());
    }

    private static double overlapRatio(CalendarEvent a, CalendarEvent b) {
        long aS = a.start().toEpochMilli(),
                aE = a.end().toEpochMilli(),
                bS = b.start().toEpochMilli(),
                bE = b.end().toEpochMilli();
        long overlap = Math.max(0, Math.min(aE, bE) - Math.max(aS, bS));
        long shortest = Math.max(1, Math.min(aE - aS, bE - bS));
        return (double) overlap / shortest;
    }

    private static Set<String> externalEmails(CalendarEvent e) {
        return e.attendees().stream()
                .map(a -> a.email().toLowerCase(Locale.ROOT))
                .filter(x -> !NormalizedEvent.domainOf(x).equals(NormalizedEvent.ORG_DOMAIN))
                .collect(Collectors.toSet());
    }

    /**
     * Same meeting imported twice (forwarded invite, second linked calendar): provider id, content
     * hash, then overlap + shared attendees.
     */
    public static CalendarEvent findDuplicate(CalendarEvent event, List<CalendarEvent> existing) {
        for (CalendarEvent other : existing) {
            if (other.id().equals(event.id()) || other.cancelled()) continue;
            if (other.providerEventId().equals(event.providerEventId())) return other;
            if (other.contentHash().equals(event.contentHash())) return other;
            if (overlapRatio(event, other) < 0.8) continue;
            Set<String> mine = externalEmails(event);
            Set<String> theirs = externalEmails(other);
            long shared = mine.stream().filter(theirs::contains).count();
            if (shared >= 2 || (shared >= 1 && mine.size() <= 2 && theirs.size() <= 2))
                return other;
        }
        return null;
    }
}
