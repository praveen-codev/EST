package com.cognition.est.calendar;

import com.cognition.est.app.config.EstProperties;
import com.cognition.est.auth.GoogleIdTokenVerifier;
import com.cognition.est.auth.GoogleIdentity;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Authorization-code + PKCE against Google's OAuth 2.0 endpoints. The client secret never leaves
 * the server.
 */
@Component
@ConditionalOnProperty(
        prefix = "est.google",
        name = "mock",
        havingValue = "false",
        matchIfMissing = true)
public class GoogleOAuthClientImpl implements GoogleOAuthClient {
    private static final Logger log = LoggerFactory.getLogger(GoogleOAuthClientImpl.class);
    public static final List<String> SCOPES =
            List.of(
                    "openid",
                    "email",
                    "profile",
                    "https://www.googleapis.com/auth/calendar.readonly",
                    "https://www.googleapis.com/auth/calendar.events.readonly");

    private final EstProperties props;
    private final GoogleIdTokenVerifier verifier;
    private final RestClient http = RestClient.create();

    public GoogleOAuthClientImpl(EstProperties props, GoogleIdTokenVerifier verifier) {
        this.props = props;
        this.verifier = verifier;
    }

    @Override
    public String authorizationUrl(String state, String codeChallenge) {
        var g = props.google();
        return "https://accounts.google.com/o/oauth2/v2/auth?client_id="
                + enc(g.clientId())
                + "&redirect_uri="
                + enc(g.redirectUri())
                + "&response_type=code&scope="
                + enc(String.join(" ", SCOPES))
                + "&access_type=offline&prompt=consent&include_granted_scopes=true&state="
                + enc(state)
                + "&code_challenge="
                + enc(codeChallenge)
                + "&code_challenge_method=S256";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Tokens exchangeCode(String code, String codeVerifier)
            throws CalendarProvider.CalendarAccessException {
        var g = props.google();
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_id", g.clientId());
        form.add("client_secret", g.clientSecret());
        form.add("code", code);
        form.add("code_verifier", codeVerifier);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", g.redirectUri());
        try {
            Map<String, Object> body =
                    http.post()
                            .uri("https://oauth2.googleapis.com/token")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .retrieve()
                            .body(Map.class);
            String idToken = (String) body.get("id_token");
            GoogleIdentity who = verifier.verify(idToken);
            String scope = (String) body.getOrDefault("scope", "");
            return new Tokens(
                    (String) body.get("refresh_token"),
                    (String) body.get("access_token"),
                    idToken,
                    who.email(),
                    who.sub(),
                    who.name(),
                    Arrays.asList(scope.split(" ")));
        } catch (RestClientResponseException e) {
            log.warn("Google token exchange failed: {}", e.getStatusCode());
            throw new CalendarProvider.CalendarAccessException(
                    "Google rejected the authorization code", false, e);
        } catch (GoogleIdTokenVerifier.InvalidTokenException e) {
            throw new CalendarProvider.CalendarAccessException(
                    "Google returned an unverifiable identity", false, e);
        }
    }

    @Override
    public void revoke(String refreshToken) {
        try {
            http.post()
                    .uri("https://oauth2.googleapis.com/revoke?token=" + enc(refreshToken))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.info("Google revoke returned {} (token may already be invalid)", e.getStatusCode());
        }
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
