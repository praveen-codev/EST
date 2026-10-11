package com.cognition.est.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * A Google (or mock) account linked to a member for calendar access. Token material is never on
 * this record.
 */
public record Identity(
        String id,
        String memberId,
        String provider,
        String providerSub,
        String email,
        String displayName,
        List<String> scopes,
        String status,
        Instant lastSyncAt,
        String lastError,
        String syncToken) {
    public Identity withoutSyncToken() {
        return new Identity(
                id,
                memberId,
                provider,
                providerSub,
                email,
                displayName,
                scopes,
                status,
                lastSyncAt,
                lastError,
                null);
    }
}
