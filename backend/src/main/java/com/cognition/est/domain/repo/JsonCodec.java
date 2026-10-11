package com.cognition.est.domain.repo;

import com.cognition.est.ai.SuggestionEngine;
import com.cognition.est.domain.model.Attendee;
import com.cognition.est.domain.model.ClientCreate;
import com.cognition.est.domain.model.Evidence;
import com.cognition.est.domain.model.SessionContact;
import com.cognition.est.domain.model.UseCase;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Jackson 3 (de)serialisation for the JSON-in-TEXT/jsonb columns and typed suggestion values. */
@Component
public class JsonCodec implements SuggestionEngine.SuggestionValueCodec {

    private final JsonMapper mapper = JsonMapper.builder().build();

    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    public <T> T read(String json, Class<T> type) {
        return mapper.readValue(json, type);
    }

    public <T> T read(String json, TypeReference<T> type) {
        return mapper.readValue(json, type);
    }

    public List<String> strings(String json) {
        return json == null || json.isBlank()
                ? List.of()
                : read(json, new TypeReference<List<String>>() {});
    }

    public List<Evidence> evidence(String json) {
        return json == null || json.isBlank()
                ? List.of()
                : read(json, new TypeReference<List<Evidence>>() {});
    }

    public List<Attendee> attendees(String json) {
        return json == null || json.isBlank()
                ? List.of()
                : read(json, new TypeReference<List<Attendee>>() {});
    }

    public Map<String, String> stringMap(String json) {
        return json == null || json.isBlank()
                ? Map.of()
                : read(json, new TypeReference<Map<String, String>>() {});
    }

    @Override
    public String toJson(String field, Object value) {
        return write(value);
    }

    /** Inverse of {@link #toJson}: values are typed per suggestion field. */
    public Object suggestionValue(String field, String json) {
        JsonNode node = mapper.readTree(json);
        return switch (field) {
            case "participantsEstimated" -> node.asInt();
            case "participantIds" -> read(json, new TypeReference<List<String>>() {});
            case "contacts" -> read(json, SessionContact.class);
            case "useCases" -> read(json, UseCase.class);
            case "clientId" -> node.isObject() ? read(json, ClientCreate.class) : node.asString();
            default -> node.asString();
        };
    }
}
