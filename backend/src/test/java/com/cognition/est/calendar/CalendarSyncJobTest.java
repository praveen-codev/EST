package com.cognition.est.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.cognition.est.app.job.JobExit;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** The headless prod sync job: preconditions, per-identity isolation, shared row lock, output. */
class CalendarSyncJobTest {
    private static int databases;

    private String url;
    private JdbcClient db;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeEach
    void database() {
        url =
                "jdbc:h2:mem:est_sync_job_"
                        + ++databases
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration/common", "classpath:db/migration/h2")
                .load()
                .migrate();
        db = JdbcClient.create(new DriverManagerDataSource(url, "sa", ""));
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restore() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private int runJob(String... extra) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String[] base = {
            "--spring.profiles.active=prod",
            "--SPRING_DATASOURCE_URL=" + url,
            "--SPRING_DATASOURCE_USERNAME=sa",
            "--SPRING_DATASOURCE_PASSWORD=",
            "--est.crypto.refresh-token-key=" + Base64.getEncoder().encodeToString(key)
        };
        String[] args = new String[base.length + extra.length];
        System.arraycopy(base, 0, args, 0, base.length);
        System.arraycopy(extra, 0, args, base.length, extra.length);
        return CalendarSyncJob.run(Map.of(), args);
    }

    private void claim(String track) {
        db.sql("insert into deployment_track values (1, ?, current_timestamp)")
                .param(track)
                .update();
    }

    private void identity(String id, String email) {
        db.sql(
                        """
                        insert into members (id, organization_id, google_sub, email, name, role, status,
                            created_at, updated_at)
                        values (?, 'org_cognition', ?, ?, ?, 'MEMBER', 'ACTIVE', current_timestamp,
                            current_timestamp)
                        """)
                .params("mem_" + id, "sub-" + id, email, email)
                .update();
        db.sql(
                        """
                        insert into member_identities (id, organization_id, member_id, provider, email,
                            status, created_at, updated_at)
                        values (?, 'org_cognition', ?, 'mock', ?, 'ACTIVE', current_timestamp,
                            current_timestamp)
                        """)
                .params(id, "mem_" + id, email)
                .update();
    }

    private String stdout() {
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void refusesUnclaimedOrSandboxDatabasesAndDoesNotClaimThem() {
        assertThat(runJob()).isEqualTo(JobExit.REFUSED);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("no track claim");
        assertThat(db.sql("select count(*) from deployment_track").query(Integer.class).single())
                .isZero();

        claim("sandbox");
        assertThat(runJob()).isEqualTo(JobExit.REFUSED);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("claimed by the sandbox track");
    }

    @Test
    void refusesSandboxProfileAndMockGoogle() {
        claim("prod");
        assertThat(runJob("--est.track=sandbox", "--est.calendar-sync-enabled=false"))
                .isEqualTo(JobExit.REFUSED);
        assertThat(runJob("--est.google.mock=true")).isEqualTo(JobExit.REFUSED);
    }

    @Test
    void refusesASchemaThatIsNotThisBuildsVersion() {
        claim("prod");
        db.sql(
                        "delete from flyway_schema_history where installed_rank ="
                                + " (select max(installed_rank) from flyway_schema_history)")
                .update();
        assertThat(runJob()).isEqualTo(JobExit.REFUSED);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("Schema mismatch on production");
    }

    @Test
    void emptyOrganisationCompletesWithZeroCounts() {
        claim("prod");
        assertThat(runJob()).isEqualTo(JobExit.OK);
        assertThat(stdout()).contains("Calendar sync job completed: identities=0, ok=0, failed=0");
    }

    @Test
    void failingIdentityIsIsolatedAndReportedOnlyAsCounts() {
        claim("prod");
        identity("idn_a", "a@cognition.ai");
        identity("idn_b", "b@cognition.ai");
        assertThat(runJob()).isEqualTo(JobExit.PARTIAL);
        assertThat(stdout())
                .contains("Calendar sync job partial: identities=2, ok=0, failed=2, busy=0")
                .doesNotContain("a@cognition.ai")
                .doesNotContain("idn_a");
        assertThat(
                        db.sql("select count(*) from calendar_sync_runs where status = 'failed'")
                                .query(Integer.class)
                                .single())
                .isEqualTo(2);
    }

    @Test
    void identityLockedByAnotherConnectionIsSkippedAsBusy() throws SQLException {
        claim("prod");
        identity("idn_a", "a@cognition.ai");
        identity("idn_b", "b@cognition.ai");
        try (Connection manual = DriverManager.getConnection(url, "sa", "")) {
            manual.setAutoCommit(false);
            manual.createStatement()
                    .executeQuery("select id from member_identities where id = 'idn_a' for update")
                    .next();
            assertThat(runJob()).isEqualTo(JobExit.PARTIAL);
            manual.rollback();
        }
        assertThat(stdout()).contains("identities=2, ok=0, failed=1, busy=1");
        assertThat(
                        db.sql(
                                        "select count(*) from calendar_sync_runs where identity_id = 'idn_a'")
                                .query(Integer.class)
                                .single())
                .isZero();
    }
}
