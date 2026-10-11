package com.cognition.est.calendar;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local profile: "Google" is our own callback. The authorization URL points straight back at the
 * API with a code of the form {@code mock:<email>}; exchanging it yields a fake refresh token so
 * the whole link → store encrypted → sync → unlink path runs without credentials.
 */
@Component
@ConditionalOnProperty(prefix = "est.google", name = "mock", havingValue = "true")
public class MockGoogleOAuthClient implements GoogleOAuthClient {
    private final String redirectUri;

    public MockGoogleOAuthClient(com.cognition.est.app.config.EstProperties props) {
        this.redirectUri = props.google().redirectUri();
    }

    @Override
    public String authorizationUrl(String state, String codeChallenge) {
        return redirectUri
                + "?state="
                + URLEncoder.encode(state, StandardCharsets.UTF_8)
                + "&code=mock%3Apartner";
    }

    @Override
    public Tokens exchangeCode(String code, String codeVerifier)
            throws CalendarProvider.CalendarAccessException {
        if (code == null || !code.startsWith("mock:"))
            throw new CalendarProvider.CalendarAccessException(
                    "Mock exchange expects code 'mock:<email>'", false, null);
        String email = code.substring(5);
        if (!email.contains("@")) email = email + "@cognition.ai";
        return new Tokens(
                "mock-refresh-" + email,
                "mock-access",
                null,
                email,
                "mock-" + email,
                null,
                List.of("calendar.readonly"));
    }

    @Override
    public void revoke(String refreshToken) {}
}
