package com.cognition.est.auth;

import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local profile only: accepts tokens of the form {@code mock:<email>[:<name>]} so the whole sign-in
 * flow (member lookup, pending approval, refresh cookies) runs without Google.
 */
@Component
@ConditionalOnProperty(prefix = "est.google", name = "mock", havingValue = "true")
public class MockGoogleIdTokenVerifier implements GoogleIdTokenVerifier {
    @Override
    public GoogleIdentity verify(String idToken) throws InvalidTokenException {
        if (idToken == null || !idToken.startsWith("mock:"))
            throw new InvalidTokenException("Mock verifier expects 'mock:<email>'");
        String[] parts = idToken.substring(5).split(":", 2);
        String email = parts[0].trim().toLowerCase(Locale.ROOT);
        if (!email.contains("@")) throw new InvalidTokenException("Invalid mock identity");
        String name = parts.length > 1 ? parts[1] : null;
        return new GoogleIdentity(
                "mock-" + email, email, true, name, email.substring(email.indexOf('@') + 1), null);
    }
}
