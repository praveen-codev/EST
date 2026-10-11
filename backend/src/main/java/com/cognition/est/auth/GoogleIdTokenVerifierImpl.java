package com.cognition.est.auth;

import com.cognition.est.app.config.EstProperties;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.Collections;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Real verifier: Google's JWKS, audience = our OAuth client id, issuer accounts.google.com. */
@Component
@ConditionalOnProperty(
        prefix = "est.google",
        name = "mock",
        havingValue = "false",
        matchIfMissing = true)
public class GoogleIdTokenVerifierImpl implements GoogleIdTokenVerifier {
    private final com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier verifier;

    public GoogleIdTokenVerifierImpl(EstProperties props) throws Exception {
        if (props.google().clientId().isBlank())
            throw new IllegalStateException(
                    "GOOGLE_CLIENT_ID must be set when est.google.mock=false");
        this.verifier =
                new com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier.Builder(
                                GoogleNetHttpTransport.newTrustedTransport(),
                                GsonFactory.getDefaultInstance())
                        .setAudience(Collections.singletonList(props.google().clientId()))
                        .build();
    }

    @Override
    public GoogleIdentity verify(String idToken) throws InvalidTokenException {
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null)
                throw new InvalidTokenException("Google ID token failed verification");
            var p = token.getPayload();
            return new GoogleIdentity(
                    p.getSubject(),
                    p.getEmail(),
                    Boolean.TRUE.equals(p.getEmailVerified()),
                    (String) p.get("name"),
                    p.getHostedDomain(),
                    (String) p.get("picture"));
        } catch (InvalidTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidTokenException("Google ID token could not be verified");
        }
    }
}
