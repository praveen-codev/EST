package com.cognition.est.domain.model;

public record Evidence(String source, String excerpt, String attribution) {
    public static Evidence of(String source, String excerpt) {
        return new Evidence(source, excerpt, null);
    }
}
