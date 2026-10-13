package com.cognition.est.app.security;

import com.cognition.est.app.config.EstProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * RSA key pair for the application's own access tokens (RS256). Keys come from the environment;
 * only the {@code local} profile may fall back to an ephemeral pair, which is regenerated on every
 * start (tokens do not survive a restart — fine for development, impossible in production).
 */
@Configuration
public class JwtKeyConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyConfig.class);

    @Bean
    RSAKey jwtSigningKey(EstProperties props, Environment env) throws Exception {
        var jwt = props.jwt();
        if (!jwt.privateKeyPem().isBlank() && !jwt.publicKeyPem().isBlank()) {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            var priv =
                    (RSAPrivateKey)
                            kf.generatePrivate(new PKCS8EncodedKeySpec(pem(jwt.privateKeyPem())));
            var pub =
                    (RSAPublicKey)
                            kf.generatePublic(new X509EncodedKeySpec(pem(jwt.publicKeyPem())));
            return new RSAKey.Builder(pub)
                    .privateKey(priv)
                    .keyID("est-" + fingerprint(pub))
                    .build();
        }
        boolean local = env.matchesProfiles("local");
        if (!local) {
            throw new IllegalStateException(
                    "EST_JWT_PRIVATE_KEY / EST_JWT_PUBLIC_KEY must be set outside the local profile");
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        log.warn(
                "local profile: generated an ephemeral RSA key pair for access tokens (set EST_JWT_* to persist)");
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("dev-" + UUID.randomUUID())
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey key) {
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(key));
        return new NimbusJwtEncoder(source);
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey key, EstProperties props) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.jwt().issuer()));
        return decoder;
    }

    /**
     * Accepts real newlines or the literal {@code \n} escapes that gen-keys.sh / .env files carry.
     */
    private static byte[] pem(String pem) {
        String body =
                pem.replace("\\n", "\n")
                        .replaceAll("-----(BEGIN|END)[A-Z ]+-----", "")
                        .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static String fingerprint(RSAPublicKey key) {
        return Integer.toHexString(key.getModulus().hashCode());
    }
}
