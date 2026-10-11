package com.cognition.est.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Proposal layer. {@code value} is typed per field: String for title/summary/sessionType/
 * facilitatorId/teamId/coFacilitatorIds/technologyIds, Integer for participantsEstimated,
 * List&lt;String&gt; for participantIds, SessionContact for contacts, UseCase for useCases, and
 * either a String id or a ClientCreate for clientId.
 */
public record Suggestion(
        String id,
        String candidateId,
        String sessionId,
        String field,
        Object value,
        String label,
        double confidence,
        List<Evidence> evidence,
        String status,
        String promptVersion,
        String decidedBy,
        Instant decidedAt) {

    public Suggestion withStatus(String status, String decidedBy, Instant decidedAt) {
        return new Suggestion(
                id,
                candidateId,
                sessionId,
                field,
                value,
                label,
                confidence,
                evidence,
                status,
                promptVersion,
                decidedBy,
                decidedAt);
    }

    public Suggestion withSessionId(String sessionId) {
        return new Suggestion(
                id,
                candidateId,
                sessionId,
                field,
                value,
                label,
                confidence,
                evidence,
                status,
                promptVersion,
                decidedBy,
                decidedAt);
    }
}
