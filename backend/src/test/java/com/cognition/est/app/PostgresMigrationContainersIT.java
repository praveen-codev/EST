package com.cognition.est.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Runs the full migration chain (common + postgresql vendor dir) against a real PostgreSQL 17.
 * Needs Docker; enabled with EST_RUN_CONTAINER_TESTS=1 (see build.gradle.kts).
 */
@Tag("containers")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("qa")
class PostgresMigrationContainersIT {

    /** The qa profile refuses to start without real keys; generate a pair for the test JVM. */
    @DynamicPropertySource
    static void jwtKeys(DynamicPropertyRegistry registry) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        registry.add(
                "est.jwt.private-key-pem",
                () -> pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        registry.add(
                "est.jwt.public-key-pem", () -> pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        registry.add(
                "est.crypto.refresh-token-key",
                () -> Base64.getEncoder().encodeToString(new byte[32]));
        registry.add("est.google.client-id", () -> "test-client.apps.googleusercontent.com");
        registry.add("est.google.client-secret", () -> "test-secret");
    }

    private static String pem(String type, byte[] der) {
        return "-----BEGIN "
                + type
                + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END "
                + type
                + "-----";
    }

    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired JdbcClient jdbc;

    @Test
    void schemaMigratesAndJsonbUpgradeApplied() {
        assertThat(jdbc.sql("select count(*) from catalog_items").query(Integer.class).single())
                .isGreaterThan(40);
        String type =
                jdbc.sql(
                                "select data_type from information_schema.columns where table_name = 'suggestions' and column_name = 'value_json'")
                        .query(String.class)
                        .single();
        assertThat(type).isEqualTo("jsonb");
    }
}
