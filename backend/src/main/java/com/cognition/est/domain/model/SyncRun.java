package com.cognition.est.domain.model;

import java.time.Instant;

public record SyncRun(
        String id,
        String identityId,
        String kind,
        Instant startedAt,
        Instant finishedAt,
        String status,
        int eventsSeen,
        int eventsNew,
        int eventsUpdated,
        int eventsUnchanged,
        int candidatesCreated,
        String error) {}
