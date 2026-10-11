package com.cognition.est.calendar;

/**
 * The two OAuth calls the linking flow needs; real implementation talks to Google, the mock one to
 * nobody.
 */
public interface GoogleOAuthClient {

    record Tokens(
            String refreshToken,
            String accessToken,
            String idToken,
            String email,
            String sub,
            String displayName,
            java.util.List<String> scopes) {}

    String authorizationUrl(String state, String codeChallenge);

    Tokens exchangeCode(String code, String codeVerifier)
            throws CalendarProvider.CalendarAccessException;

    void revoke(String refreshToken);
}
