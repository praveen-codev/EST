package com.cognition.est.auth;

/**
 * Verifies a Google ID token's signature, audience, issuer and expiry and returns its identity
 * claims.
 */
public interface GoogleIdTokenVerifier {
    GoogleIdentity verify(String idToken) throws InvalidTokenException;

    class InvalidTokenException extends Exception {
        public InvalidTokenException(String message) {
            super(message);
        }
    }
}
