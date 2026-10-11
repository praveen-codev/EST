package com.cognition.est.ai;

import com.cognition.est.domain.model.CalendarEvent;
import java.time.Duration;
import java.util.List;
import java.util.TreeSet;

/** Port of web/lib/ai/normalize.ts. */
public record NormalizedEvent(
        String title,
        String description,
        String text,
        long durationMin,
        List<NormalizedAttendee> attendees,
        List<NormalizedAttendee> internal,
        List<NormalizedAttendee> external,
        List<String> externalDomains,
        NormalizedAttendee organizer,
        boolean cancelled,
        boolean recurring) {

    public static final String ORG_DOMAIN = "cognition.ai";

    public record NormalizedAttendee(
            String email, String name, String domain, boolean isInternal, boolean organizer) {}

    public static String domainOf(String email) {
        int at = email.indexOf('@');
        return at < 0 ? "" : email.substring(at + 1).toLowerCase();
    }

    public static NormalizedEvent of(CalendarEvent event) {
        String organizerEmail =
                event.organizerEmail() == null ? "" : event.organizerEmail().toLowerCase();
        List<NormalizedAttendee> attendees =
                event.attendees().stream()
                        .map(
                                a -> {
                                    String email = a.email().toLowerCase();
                                    String domain = domainOf(email);
                                    return new NormalizedAttendee(
                                            email,
                                            a.name(),
                                            domain,
                                            domain.equals(ORG_DOMAIN),
                                            a.organizer() || email.equals(organizerEmail));
                                })
                        .toList();
        List<NormalizedAttendee> internal =
                attendees.stream().filter(NormalizedAttendee::isInternal).toList();
        List<NormalizedAttendee> external =
                attendees.stream().filter(a -> !a.isInternal()).toList();
        List<String> domains =
                new TreeSet<>(external.stream().map(NormalizedAttendee::domain).toList())
                        .stream().toList();
        long minutes = Math.max(0, Duration.between(event.start(), event.end()).toMinutes());
        return new NormalizedEvent(
                event.title().trim(),
                event.description().trim(),
                (event.title() + "\n" + event.description()).toLowerCase(),
                minutes,
                attendees,
                internal,
                external,
                domains,
                attendees.stream().filter(NormalizedAttendee::organizer).findFirst().orElse(null),
                event.cancelled(),
                event.recurringEventId() != null);
    }
}
