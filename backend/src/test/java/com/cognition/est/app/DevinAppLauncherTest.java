package com.cognition.est.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** infra/devin-app/start.sh must keep developer profiles away from the production database. */
class DevinAppLauncherTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path START = ROOT.resolve("infra/devin-app/start.sh");
    private static final String PROD_URL =
            "postgresql://u:p@ep-prod-123-pooler.us-east-2.aws.neon.tech/neondb?sslmode=require";
    private static final String PROD_UNPOOLED =
            "postgresql://u:p@ep-prod-123.us-east-2.aws.neon.tech/neondb?sslmode=require";

    @TempDir Path tmp;

    private record Run(int exit, String output) {}

    private Run start(Map<String, String> env) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bash", START.toString()).redirectErrorStream(true);
        pb.environment()
                .keySet()
                .removeIf(k -> k.startsWith("EST_") || k.startsWith("DATABASE_URL"));
        pb.environment().remove("SPRING_PROFILES_ACTIVE");
        pb.environment().put("EST_STATE_DIR", tmp.resolve("state").toString());
        pb.environment().put("PORT", "0");
        pb.environment().putAll(env);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Run(p.waitFor(), out);
    }

    private Path prodEnv() throws IOException {
        Path file = tmp.resolve("prod.env");
        Files.writeString(
                file,
                "DATABASE_URL='" + PROD_URL + "'\nDATABASE_URL_UNPOOLED=" + PROD_UNPOOLED + "\n");
        return file;
    }

    @Test
    void devinAppProfileDefaultsToTheSandboxEnvFile() throws Exception {
        assumeFalse(Files.exists(ROOT.resolve(".env.sandbox.local")));
        Run run = start(Map.of("EST_PRODUCTION_ENV_FILE", prodEnv().toString()));
        assertThat(run.exit()).isNotZero();
        assertThat(run.output()).contains(".env.sandbox.local").doesNotContain("prod.env");
    }

    @Test
    void devinAppProfileRefusesTheProductionDatabase() throws Exception {
        Path prod = prodEnv();
        Run viaEnvFile =
                start(
                        Map.of(
                                "EST_PRODUCTION_ENV_FILE",
                                prod.toString(),
                                "EST_ENV_FILE",
                                prod.toString()));
        assertThat(viaEnvFile.exit()).isEqualTo(1);
        assertThat(viaEnvFile.output()).contains("Refusing to start profile 'devin-app'");

        Run viaUnpooledUrl =
                start(
                        Map.of(
                                "EST_PRODUCTION_ENV_FILE",
                                prod.toString(),
                                "SPRING_PROFILES_ACTIVE",
                                "sandbox",
                                "DATABASE_URL",
                                PROD_UNPOOLED));
        assertThat(viaUnpooledUrl.exit()).isEqualTo(1);
        assertThat(viaUnpooledUrl.output()).contains("Refusing to start profile 'sandbox'");
    }

    @Test
    void sandboxDatabaseIsAllowedPastTheGuard() throws Exception {
        // A failing openssl stops the launcher at key generation, right after the guard.
        Path bin = Files.createDirectories(tmp.resolve("bin"));
        Path openssl = Files.writeString(bin.resolve("openssl"), "#!/bin/sh\nexit 97\n");
        openssl.toFile().setExecutable(true);
        Run run =
                start(
                        Map.of(
                                "EST_PRODUCTION_ENV_FILE",
                                prodEnv().toString(),
                                "DATABASE_URL",
                                "postgresql://u:p@ep-sandbox-9-pooler.us-east-2.aws.neon.tech/neondb",
                                "PATH",
                                bin + ":" + System.getenv("PATH")));
        assertThat(run.output()).doesNotContain("Refusing to start");
        assertThat(run.exit()).isEqualTo(97);
    }
}
