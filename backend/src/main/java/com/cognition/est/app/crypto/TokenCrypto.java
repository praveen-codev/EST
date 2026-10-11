package com.cognition.est.app.crypto;

import com.cognition.est.app.config.EstProperties;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM for provider refresh tokens and PKCE verifiers at rest. Key from EST_CRYPTO_KEY
 * (base64, 32 bytes).
 */
@Component
public class TokenCrypto {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public record Encrypted(String ciphertext, String iv) {}

    public TokenCrypto(EstProperties props, Environment env) {
        String b64 = props.crypto().refreshTokenKey();
        if (b64 == null || b64.isBlank()) {
            if (!env.matchesProfiles("local"))
                throw new IllegalStateException(
                        "EST_CRYPTO_KEY must be set outside the local profile");
            byte[] k = new byte[32];
            random.nextBytes(k);
            key = new SecretKeySpec(k, "AES");
            return;
        }
        byte[] raw = Base64.getDecoder().decode(b64.trim());
        if (raw.length != 32)
            throw new IllegalStateException("EST_CRYPTO_KEY must decode to 32 bytes");
        key = new SecretKeySpec(raw, "AES");
    }

    public Encrypted encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] out = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new Encrypted(
                    Base64.getEncoder().encodeToString(out),
                    Base64.getEncoder().encodeToString(iv));
        } catch (Exception e) {
            throw new IllegalStateException("encryption failed", e);
        }
    }

    public String decrypt(Encrypted enc) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(128, Base64.getDecoder().decode(enc.iv())));
            return new String(
                    c.doFinal(Base64.getDecoder().decode(enc.ciphertext())),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decryption failed (wrong EST_CRYPTO_KEY?)", e);
        }
    }
}
