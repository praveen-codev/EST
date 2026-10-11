package com.cognition.est.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Authoritative layer: the member-owned record with per-field provenance. */
public record Session(
        String id,
        String state,
        String title,
        String clientId,
        String teamId,
        String facilitatorId,
        List<String> coFacilitatorIds,
        List<SessionContact> contacts,
        Instant scheduledStart,
        Instant scheduledEnd,
        Instant actualStart,
        Instant actualEnd,
        Integer participantsEstimated,
        List<String> participantIds,
        String sessionType,
        String outcomeId,
        List<String> technologyIds,
        List<UseCase> useCases,
        String summary,
        String notes,
        List<String> sourceEventIds,
        Map<String, String> provenance,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt,
        int version) {

    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Mutable copy used by the state-transition services; {@link #build()} snapshots it. */
    public static final class Builder {
        public String id,
                state,
                title,
                clientId,
                teamId,
                facilitatorId,
                sessionType,
                outcomeId,
                summary,
                notes,
                createdBy,
                updatedBy;
        public List<String> coFacilitatorIds, participantIds, technologyIds, sourceEventIds;
        public List<SessionContact> contacts;
        public List<UseCase> useCases;
        public Instant scheduledStart, scheduledEnd, actualStart, actualEnd, createdAt, updatedAt;
        public Integer participantsEstimated;
        public Map<String, String> provenance;
        public int version;

        Builder(Session s) {
            id = s.id;
            state = s.state;
            title = s.title;
            clientId = s.clientId;
            teamId = s.teamId;
            facilitatorId = s.facilitatorId;
            sessionType = s.sessionType;
            outcomeId = s.outcomeId;
            summary = s.summary;
            notes = s.notes;
            createdBy = s.createdBy;
            updatedBy = s.updatedBy;
            coFacilitatorIds = new ArrayList<>(s.coFacilitatorIds);
            participantIds = new ArrayList<>(s.participantIds);
            technologyIds = new ArrayList<>(s.technologyIds);
            sourceEventIds = new ArrayList<>(s.sourceEventIds);
            contacts = new ArrayList<>(s.contacts);
            useCases = new ArrayList<>(s.useCases);
            scheduledStart = s.scheduledStart;
            scheduledEnd = s.scheduledEnd;
            actualStart = s.actualStart;
            actualEnd = s.actualEnd;
            createdAt = s.createdAt;
            updatedAt = s.updatedAt;
            participantsEstimated = s.participantsEstimated;
            provenance = new LinkedHashMap<>(s.provenance);
            version = s.version;
        }

        public Session build() {
            return new Session(
                    id,
                    state,
                    title,
                    clientId,
                    teamId,
                    facilitatorId,
                    List.copyOf(coFacilitatorIds),
                    List.copyOf(contacts),
                    scheduledStart,
                    scheduledEnd,
                    actualStart,
                    actualEnd,
                    participantsEstimated,
                    List.copyOf(participantIds),
                    sessionType,
                    outcomeId,
                    List.copyOf(technologyIds),
                    List.copyOf(useCases),
                    summary,
                    notes,
                    List.copyOf(sourceEventIds),
                    Map.copyOf(provenance),
                    createdBy,
                    createdAt,
                    updatedBy,
                    updatedAt,
                    version);
        }
    }
}
