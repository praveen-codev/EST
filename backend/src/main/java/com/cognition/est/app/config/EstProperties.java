package com.cognition.est.app.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings. Secrets are injected from the environment (see .env.example); nothing here
 * is ever logged or returned by an endpoint.
 */
@Validated
@ConfigurationProperties(prefix = "est")
public record EstProperties(
        /** Allowed browser origins (CORS). */
        @DefaultValue("http://localhost:3100") List<String> webOrigins,
        /** Email domains allowed to sign in; empty = any verified Google account (dev only). */
        @DefaultValue List<String> allowedDomains,
        /** Emails promoted to ADMIN on first sign-in. */
        @DefaultValue List<String> bootstrapAdminEmails,
        Jwt jwt,
        Google google,
        Crypto crypto,
        Dev dev) {

    public record Jwt(
            @DefaultValue("est-api") @NotBlank String issuer,
            @DefaultValue("PT15M") Duration accessTokenTtl,
            @DefaultValue("P14D") Duration refreshTokenTtl,
            /**
             * PEM-encoded PKCS#8 RSA private key; when blank in the local profile an ephemeral key
             * pair is generated.
             */
            @DefaultValue("") String privateKeyPem,
            @DefaultValue("") String publicKeyPem) {}

    public record Google(
            /**
             * OAuth 2.0 Web client id used for both sign-in (ID token audience) and calendar
             * linking.
             */
            @DefaultValue("") String clientId,
            @DefaultValue("") String clientSecret,
            @DefaultValue("http://localhost:8080/api/v1/calendar/identities/link/callback")
                    String redirectUri,
            /** When true (local profile) Google endpoints are replaced by in-process fakes. */
            @DefaultValue("false") boolean mock) {}

    public record Crypto(
            /** Base64 32-byte key for AES-256-GCM encryption of provider refresh tokens. */
            @DefaultValue("") String refreshTokenKey) {}

    public record Dev(
            /**
             * Enables /api/v1/dev/* (login-as, reset, scenario). Never true outside the local
             * profile.
             */
            @DefaultValue("false") boolean endpointsEnabled) {}
}
