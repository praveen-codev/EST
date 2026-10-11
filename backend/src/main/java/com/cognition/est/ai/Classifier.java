package com.cognition.est.ai;

import com.cognition.est.domain.model.Classification;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Port of web/lib/ai/classify.ts. Only the team's own session formats can make an event "likely".
 */
public final class Classifier {
    private Classifier() {}

    public static final double LIKELY_THRESHOLD = 0.7;
    public static final double REVIEW_THRESHOLD = 0.4;

    /** Highest score an event can reach without matching a session format from the catalogue. */
    public static final double NO_TYPE_CAP = 0.62;

    private static final List<String> SUPPORTING =
            List.of(
                    "workshop",
                    "enablement",
                    "kickoff",
                    "kick-off",
                    "office hours",
                    "hands-on",
                    "hands on",
                    "training",
                    "onboarding");
    private static final List<String> PRODUCT = List.of("devin");
    private static final List<String> WEAK =
            List.of("follow-up", "follow up", "sync", "readout", "pilot", "demo");
    private static final List<String> SOFT_NEGATIVE =
            List.of("intro call", "intro:", "intro to", "introduction");
    private static final List<String> NEGATIVE =
            List.of(
                    "1:1",
                    "1-1",
                    "standup",
                    "stand-up",
                    "interview",
                    "lunch",
                    "focus time",
                    "dentist",
                    "ooo",
                    "out of office",
                    "retro",
                    "all-hands",
                    "all hands",
                    "pto");

    public record TypeMatch(String label, String keyword, Integer typicalMinutes) {}

    public static String bucketFor(double score) {
        if (score >= LIKELY_THRESHOLD) return "likely";
        if (score >= REVIEW_THRESHOLD) return "review";
        return "unlikely";
    }

    public static Classification classify(
            NormalizedEvent n, boolean knownClient, TypeMatch matchedType) {
        if (n.cancelled())
            return new Classification(0.05, "unlikely", List.of("Event was cancelled"));
        List<String> reasons = new ArrayList<>();
        double score = 0.2;
        String text = n.text();

        List<String> negatives = NEGATIVE.stream().filter(text::contains).toList();
        if (!negatives.isEmpty()) {
            score -= 0.5;
            reasons.add("Looks like \"" + negatives.getFirst() + "\", not an enablement session");
        }
        if (matchedType != null) {
            score += 0.4;
            reasons.add(
                    "\""
                            + matchedType.keyword()
                            + "\" matches the "
                            + matchedType.label()
                            + " format");
            Integer typical = matchedType.typicalMinutes();
            if (typical != null
                    && typical != 0
                    && Math.abs(n.durationMin() - typical) <= typical * 0.5) score += 0.05;
        }
        List<String> strong = SUPPORTING.stream().filter(text::contains).toList();
        if (!strong.isEmpty()) {
            score += 0.1;
            reasons.add(
                    "Mentions "
                            + String.join(
                                    " and ",
                                    strong.stream().limit(2).map(k -> "\"" + k + "\"").toList()));
        }
        if (PRODUCT.stream().anyMatch(text::contains)) {
            score += 0.1;
            reasons.add("Mentions Devin");
        }
        List<String> weak = WEAK.stream().filter(text::contains).toList();
        if (!weak.isEmpty() && strong.isEmpty()) {
            score += 0.1;
            reasons.add("Mentions \"" + weak.getFirst() + "\" (weak signal)");
        }
        if (SOFT_NEGATIVE.stream().anyMatch(text::contains)) {
            score -= 0.2;
            reasons.add("Reads like an intro call rather than a session");
        }
        if (!n.external().isEmpty()) {
            score += 0.2;
            reasons.add(
                    n.external().size()
                            + " external attendee"
                            + (n.external().size() > 1 ? "s" : "")
                            + " from "
                            + String.join(", ", n.externalDomains()));
        } else {
            score -= 0.35;
            reasons.add(
                    n.attendees().size() <= 1
                            ? "Only you on the invite"
                            : "Internal attendees only");
        }
        if (!n.internal().isEmpty() && !n.external().isEmpty()) score += 0.05;
        long d = n.durationMin();
        if (d >= 45 && d <= 240) {
            score += 0.1;
            if (d >= 90) score += 0.05;
            reasons.add(d + " min fits a session");
        } else if (d < 45) {
            score -= d < 30 ? 0.25 : 0.1;
            reasons.add("Only " + d + " min");
        }
        if (n.attendees().size() >= 3) score += 0.1;
        if (knownClient) {
            score += 0.1;
            reasons.add("Attendees match a client with prior sessions");
        }
        if (n.description().isEmpty() && n.title().split("\\s+").length <= 2) {
            score -= 0.1;
            reasons.add("Sparse title and no description");
        }
        if (matchedType == null && score > NO_TYPE_CAP) {
            score = NO_TYPE_CAP;
            reasons.addFirst(
                    "No recognised session format (101, 201, pair programming, AMA, sprint in a day)");
        }
        double clamped = Math.max(0, Math.min(1, round2(score)));
        return new Classification(clamped, bucketFor(clamped), List.copyOf(reasons));
    }

    static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
