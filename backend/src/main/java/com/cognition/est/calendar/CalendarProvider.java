package com.cognition.est.calendar;

import com.cognition.est.domain.model.Member;
import java.time.Instant;
import java.util.List;

/** A calendar backend (Google, or the in-process mock in the local profile). */
public interface CalendarProvider {

    record Page(List<ProviderEvent> events, String nextSyncToken, boolean fullResync) {}

    /** Identity row needed to call the provider; tokens are decrypted by the implementation. */
    record IdentityRef(
            String id, String memberId, String email, String provider, String syncToken) {}

    String name();

    /**
     * Fetch changes since {@code identity.syncToken()} (null = initial bounded lookback).
     * Implementations must signal a required full resync (Google 410) by returning {@code
     * fullResync=true} with a fresh page.
     */
    Page fetch(IdentityRef identity, Member owner, Instant now) throws CalendarAccessException;

    class CalendarAccessException extends Exception {
        public final boolean needsReauth;

        public CalendarAccessException(String message, boolean needsReauth, Throwable cause) {
            super(message, cause);
            this.needsReauth = needsReauth;
        }
    }
}
