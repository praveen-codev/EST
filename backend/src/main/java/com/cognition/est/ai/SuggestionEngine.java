package com.cognition.est.ai;

import com.cognition.est.domain.model.CalendarEvent;
import com.cognition.est.domain.model.Classification;
import com.cognition.est.domain.model.Ids;
import com.cognition.est.domain.model.Person;
import com.cognition.est.domain.model.Suggestion;
import java.util.List;

/**
 * Port of web/lib/ai/index.ts — the whole deterministic pipeline for one calendar event: normalise
 * → redact → classify → extract/match → validated suggestions. Same event + same context ⇒
 * identical output. Framework-free so it can be golden-tested and later swapped for a real model
 * provider behind the same signature.
 */
public final class SuggestionEngine {
    private SuggestionEngine() {}

    /**
     * Confidence at or above which a suggestion is written straight into the draft (still
     * revertible).
     */
    public static final double PREFILL_THRESHOLD = 0.85;

    /**
     * Confidence at or above which a suggestion is shown inline as a chip; below it is folded away.
     */
    public static final double CHIP_THRESHOLD = 0.5;

    public enum Tier {
        PREFILL,
        CHIP,
        FOLDED
    }

    public static Tier tierFor(double confidence) {
        if (confidence >= PREFILL_THRESHOLD) return Tier.PREFILL;
        if (confidence >= CHIP_THRESHOLD) return Tier.CHIP;
        return Tier.FOLDED;
    }

    public record Result(
            Classification classification,
            List<Suggestion> suggestions,
            String redactedText,
            List<String> redactions,
            List<Person> newPeople) {}

    public static Result analyze(
            CalendarEvent event,
            String candidateId,
            EngineContext ctx,
            SuggestionValueCodec codec) {
        Redactor.Result redacted = Redactor.redact(event.description());
        CalendarEvent safe =
                new CalendarEvent(
                        event.id(),
                        event.identityId(),
                        event.ownerMemberId(),
                        event.providerEventId(),
                        event.recurringEventId(),
                        event.title(),
                        redacted.text(),
                        event.location(),
                        event.conferencing(),
                        event.start(),
                        event.end(),
                        event.timeZone(),
                        event.organizerEmail(),
                        event.attendees(),
                        event.etag(),
                        event.contentHash(),
                        event.status());
        NormalizedEvent n = NormalizedEvent.of(safe);
        Extractor.Result extraction =
                n.cancelled()
                        ? new Extractor.Result(List.of(), List.of(), null)
                        : Extractor.extract(n, ctx);
        Extractor.TypeMatch typeMatch =
                n.cancelled() ? null : Extractor.matchSessionType(n, ctx.catalogOf("sessionType"));
        Classification classification =
                Classifier.classify(
                        n,
                        extraction.matchedClient() != null,
                        typeMatch == null
                                ? null
                                : new Classifier.TypeMatch(
                                        typeMatch.type().label(),
                                        typeMatch.keyword(),
                                        typeMatch.type().typicalMinutes()));
        boolean unlikely = "unlikely".equals(classification.bucket());
        List<Suggestion> suggestions =
                unlikely
                        ? List.of()
                        : extraction.suggestions().stream()
                                .map(
                                        d ->
                                                new Suggestion(
                                                        Ids.stableId(
                                                                "sug",
                                                                event.id()
                                                                        + "|"
                                                                        + d.field
                                                                        + "|"
                                                                        + codec.toJson(
                                                                                d.field, d.value)),
                                                        candidateId,
                                                        null,
                                                        d.field,
                                                        d.value,
                                                        d.label,
                                                        d.confidence,
                                                        List.copyOf(d.evidence),
                                                        "proposed",
                                                        Extractor.PROMPT_VERSION,
                                                        null,
                                                        null))
                                .toList();
        String redactedText = (event.title() + "\n\n" + redacted.text()).trim();
        return new Result(
                classification,
                suggestions,
                redactedText,
                redacted.redactions(),
                unlikely ? List.of() : extraction.newPeople());
    }

    /**
     * Serialises a suggestion value for id derivation and persistence; implemented outside the
     * engine (Jackson lives in the domain layer).
     */
    public interface SuggestionValueCodec {
        String toJson(String field, Object value);
    }
}
