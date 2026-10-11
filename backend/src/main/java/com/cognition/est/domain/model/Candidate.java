package com.cognition.est.domain.model;

import java.time.Instant;
import java.util.List;

public record Candidate(
        String id,
        String eventId,
        String ownerMemberId,
        Classification classification,
        String duplicateOfCandidateId,
        String status,
        String sessionId,
        String redactedText,
        List<String> redactions,
        Instant createdAt,
        Instant updatedAt) {}
