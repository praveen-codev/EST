package com.cognition.est.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of web/lib/ai/redact.ts — links, passcodes, PINs and phone numbers never reach the model.
 */
public final class Redactor {
    private Redactor() {}

    public record Result(String text, List<String> redactions) {}

    private record Rule(String label, Pattern pattern, String replacement) {}

    private static final List<Rule> RULES =
            List.of(
                    new Rule(
                            "conferencing link",
                            Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE),
                            "[link]"),
                    new Rule(
                            "passcode",
                            Pattern.compile(
                                    "\\b(passcode|password|pwd)\\s*[:#]?\\s*[A-Za-z0-9-]{3,}",
                                    Pattern.CASE_INSENSITIVE),
                            "$1: [redacted]"),
                    new Rule(
                            "PIN",
                            Pattern.compile(
                                    "\\bpin\\s*[:#]?\\s*[\\d ]{3,}", Pattern.CASE_INSENSITIVE),
                            "PIN: [redacted]"),
                    new Rule(
                            "phone number",
                            Pattern.compile("\\+?\\d[\\d\\s().-]{7,}\\d"),
                            "[phone]"));

    public static Result redact(String input) {
        String text = input == null ? "" : input;
        List<String> redactions = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(text);
            int count = 0;
            while (m.find()) count++;
            if (count > 0) {
                redactions.add(count + " " + rule.label() + (count > 1 ? "s" : ""));
                text = rule.pattern().matcher(text).replaceAll(rule.replacement());
            }
        }
        return new Result(text, List.copyOf(redactions));
    }
}
