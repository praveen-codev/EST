package com.cognition.est.replication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Production → sandbox replication on two real PostgreSQL databases. */
@Tag("containers")
@Testcontainers(disabledWithoutDocker = true)
class CalendarEvidenceDistributorContainersIT {
    private static final String ORG = "org_cognition";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    private static int databases;

    private Connection prodConnection;
    private Connection sandboxConnection;
    private JdbcClient prod;
    private JdbcClient sandbox;
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private static String url(String database) {
        return "jdbc:postgresql://"
                + postgres.getHost()
                + ":"
                + postgres.getMappedPort(5432)
                + "/"
                + database
                + "?stringtype=unspecified";
    }

    private static Connection database(String name) throws SQLException {
        try (Connection admin =
                DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            admin.createStatement().execute("create database " + name);
        }
        Flyway.configure()
                .dataSource(url(name), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration/common", "classpath:db/migration/postgresql")
                .load()
                .migrate();
        return DriverManager.getConnection(
                url(name), postgres.getUsername(), postgres.getPassword());
    }

    @BeforeEach
    void databases() throws SQLException {
        databases++;
        prodConnection = database("prod" + databases);
        sandboxConnection = database("sandbox" + databases);
        prod = JdbcClient.create(new SingleConnectionDataSource(prodConnection, true));
        sandbox = JdbcClient.create(new SingleConnectionDataSource(sandboxConnection, true));
        prod.sql("insert into deployment_track values (1, 'prod', now())").update();
        member(prod, "mem_prod_alex", "sub-alex", "alex@cognition.ai", "ADMIN", "ACTIVE");
        prod.sql(
                        """
                        insert into member_identities (id, organization_id, member_id, provider, provider_sub,
                            email, scopes, refresh_token_enc, refresh_token_iv, sync_token, status, created_at, updated_at)
                        values ('idn_prod_alex', ?, 'mem_prod_alex', 'google', 'sub-alex', 'alex@cognition.ai',
                            'calendar', 'ciphertext', 'iv', 'cursor', 'ACTIVE', now(), now())
                        """)
                .param(ORG)
                .update();
        prod.sql(
                        """
                        insert into calendar_events (id, organization_id, identity_id, owner_member_id,
                            provider_event_id, title, description, start_at, end_at, etag, content_hash,
                            status, created_at, updated_at)
                        values ('evt_prod_101', ?, 'idn_prod_alex', 'mem_prod_alex', 'g-101',
                            'Devin 101 session', 'Hands-on Devin 101 session for the platform team',
                            ?, ?, 'etag-1', 'hash-1', 'confirmed', now(), now())
                        """)
                .param(ORG)
                .param(now.minus(1, ChronoUnit.DAYS).atOffset(java.time.ZoneOffset.UTC))
                .param(
                        now.minus(1, ChronoUnit.DAYS)
                                .plus(90, ChronoUnit.MINUTES)
                                .atOffset(java.time.ZoneOffset.UTC))
                .update();
        // Production already decided on this event: the candidate is dismissed and its proposal
        // superseded by a manual edit. None of that may reach the sandbox.
        prod.sql(
                        """
                        insert into candidates (id, organization_id, event_id, owner_member_id, score,
                            bucket, status, created_at, updated_at)
                        values ('cand_prod_101', ?, 'evt_prod_101', 'mem_prod_alex', 0.9, 'likely',
                            'dismissed', now(), now())
                        """)
                .param(ORG)
                .update();
        prod.sql(
                        """
                        insert into suggestions (id, organization_id, candidate_id, field, value_json,
                            label, confidence, status, prompt_version, decided_by, decided_at, created_at)
                        values ('sug_prod_type', ?, 'cand_prod_101', 'sessionType', '"101"',
                            '101 session', 0.9, 'superseded', 'prod-v1', 'mem_prod_alex', now(), now())
                        """)
                .param(ORG)
                .update();
    }

    @AfterEach
    void close() throws SQLException {
        prodConnection.close();
        sandboxConnection.close();
    }

    private void member(
            JdbcClient db, String id, String sub, String email, String role, String status) {
        db.sql(
                        """
                        insert into members (id, organization_id, google_sub, email, name, role, status,
                            created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, now(), now())
                        """)
                .params(id, ORG, sub, email, email, role, status)
                .update();
    }

    private Map<String, Integer> replicate() throws SQLException {
        sandboxConnection.setAutoCommit(false);
        Map<String, Integer> counts =
                CalendarEvidenceDistributor.replicate(
                        prodConnection, sandboxConnection, ORG, now.minus(30, ChronoUnit.DAYS));
        sandboxConnection.commit();
        sandboxConnection.setAutoCommit(true);
        return counts;
    }

    private String one(String sql) {
        return sandbox.sql(sql).query(String.class).optional().orElse(null);
    }

    private int count(String sql) {
        return sandbox.sql(sql).query(Integer.class).single();
    }

    @Test
    void sandboxSignInBeforeReplicationIsRemappedByGoogleSubject() throws SQLException {
        member(sandbox, "mem_sbx_alex", "sub-alex", "Alex@Cognition.ai", "MANAGER", "ACTIVE");
        member(sandbox, "mem_sbx_sam", null, "sam@cognition.ai", "MEMBER", "PENDING");
        member(prod, "mem_prod_sam", "sub-sam", "sam@cognition.ai", "MEMBER", "ACTIVE");
        // Same e-mail, different Google account: never merged, never blocks the rest.
        member(sandbox, "mem_sbx_kim", "sub-kim-sandbox", "kim@cognition.ai", "MEMBER", "ACTIVE");
        member(prod, "mem_prod_kim", "sub-kim-prod", "kim@cognition.ai", "MEMBER", "ACTIVE");

        Map<String, Integer> counts = replicate();

        assertThat(counts).containsEntry("member_conflicts", 1);
        assertThat(count("select count(*) from members")).isEqualTo(3);
        assertThat(one("select role from members where id = 'mem_sbx_alex'")).isEqualTo("MANAGER");
        assertThat(one("select google_sub from members where id = 'mem_sbx_sam'"))
                .isEqualTo("sub-sam");
        assertThat(one("select status from members where id = 'mem_sbx_sam'")).isEqualTo("PENDING");
        assertThat(one("select google_sub from members where id = 'mem_sbx_kim'"))
                .isEqualTo("sub-kim-sandbox");
        assertThat(one("select member_id from member_identities")).isEqualTo("mem_sbx_alex");
        assertThat(
                        one(
                                "select concat(refresh_token_enc, refresh_token_iv, sync_token) from member_identities"))
                .isEmpty();
        assertThat(one("select owner_member_id from calendar_events")).isEqualTo("mem_sbx_alex");
        assertThat(one("select owner_member_id from candidates")).isEqualTo("mem_sbx_alex");
        assertThat(one("select track from deployment_track")).isEqualTo("sandbox");
    }

    @Test
    void sandboxProposalsComeFromCopiedEvidenceNotProductionDecisions() throws SQLException {
        Map<String, Integer> first = replicate();

        assertThat(first).containsEntry("candidates", 1);
        assertThat(one("select status from candidates")).isEqualTo("open");
        assertThat(count("select count(*) from suggestions where id = 'sug_prod_type'")).isZero();
        assertThat(count("select count(*) from suggestions where status <> 'proposed'")).isZero();
        int proposed = count("select count(*) from suggestions");
        assertThat(proposed).isPositive();
        assertThat(count("select count(*) from suggestions where field = 'sessionType'"))
                .isEqualTo(1);

        // Sandbox-owned decisions survive a rerun; unchanged evidence is not re-proposed.
        sandbox.sql("update suggestions set status = 'rejected' where field = 'sessionType'")
                .update();
        Map<String, Integer> second = replicate();
        assertThat(second).containsEntry("candidates", 0).containsEntry("reanalyzed", 0);
        assertThat(count("select count(*) from suggestions")).isEqualTo(proposed);
        assertThat(one("select status from suggestions where field = 'sessionType'"))
                .isEqualTo("rejected");

        // Changed production evidence is re-analysed by the sandbox engine.
        prod.sql(
                        "update calendar_events set etag = 'etag-2', description = 'Devin 101 session, now with Java' where id = 'evt_prod_101'")
                .update();
        assertThat(replicate()).containsEntry("reanalyzed", 1);
        assertThat(one("select status from suggestions where field = 'sessionType'"))
                .isEqualTo("rejected");
    }

    @Test
    void replicationRefusesToWriteIntoProductionOrReadFromSandbox() {
        sandbox.sql("insert into deployment_track values (1, 'prod', now())").update();
        assertThatThrownBy(this::replicate).hasMessageContaining("target is the prod track");

        prod.sql("update deployment_track set track = 'sandbox'").update();
        assertThatThrownBy(this::replicate).hasMessageContaining("source is the sandbox track");
    }

    @Test
    void memberOfAnotherSandboxOrganizationIsNeverMovedIntoTheReplicatedOne() throws SQLException {
        sandbox.sql(
                        """
                        insert into organizations (id, name, allowed_domains, created_at, updated_at)
                        values ('org_other', 'Other', 'other.ai', now(), now())
                        """)
                .update();
        sandbox.sql(
                        """
                        insert into members (id, organization_id, google_sub, email, name, role, status,
                            created_at, updated_at)
                        values ('mem_other_admin', 'org_other', 'sub-alex', 'alex@other.ai', 'Alex',
                            'ADMIN', 'ACTIVE', now(), now())
                        """)
                .update();
        member(prod, "mem_other_admin", "sub-other", "other@cognition.ai", "MEMBER", "ACTIVE");

        Map<String, Integer> counts = replicate();

        assertThat(counts).containsEntry("member_conflicts", 2).containsEntry("events", 0);
        assertThat(one("select organization_id from members where id = 'mem_other_admin'"))
                .isEqualTo("org_other");
        assertThat(one("select email from members where id = 'mem_other_admin'"))
                .isEqualTo("alex@other.ai");
        assertThat(count("select count(*) from members where organization_id = 'org_cognition'"))
                .isZero();
    }

    @Test
    void skippedMembersKeepTheirExistingSandboxAttendees() throws SQLException {
        member(sandbox, "mem_sbx_kim", "sub-kim-sandbox", "kim@cognition.ai", "MEMBER", "ACTIVE");
        member(prod, "mem_prod_kim", "sub-kim-prod", "kim@cognition.ai", "MEMBER", "ACTIVE");
        sandbox.sql(
                        """
                        insert into member_identities (id, organization_id, member_id, provider,
                            provider_sub, email, scopes, status, created_at, updated_at)
                        values ('idn_sbx_kim', ?, 'mem_sbx_kim', 'google', 'sub-kim-sandbox',
                            'kim@cognition.ai', 'calendar', 'ACTIVE', now(), now())
                        """)
                .param(ORG)
                .update();
        sandbox.sql(
                        """
                        insert into calendar_events (id, organization_id, identity_id, owner_member_id,
                            provider_event_id, title, start_at, end_at, etag, content_hash, status,
                            created_at, updated_at)
                        values ('evt_sbx_kim', ?, 'idn_sbx_kim', 'mem_sbx_kim', 'g-kim', 'Kim sync', now(),
                            now() + interval '1 hour', 'etag-k', 'hash-k', 'confirmed', now(), now())
                        """)
                .param(ORG)
                .update();
        sandbox.sql(
                        """
                        insert into calendar_event_attendees (event_id, email)
                        values ('evt_sbx_kim', 'customer@example.com')
                        """)
                .update();
        prod.sql(
                        """
                        insert into calendar_event_attendees (event_id, email)
                        values ('evt_prod_101', 'customer@example.com')
                        """)
                .update();

        Map<String, Integer> counts = replicate();

        assertThat(counts).containsEntry("member_conflicts", 1).containsEntry("attendees", 1);
        assertThat(
                        count(
                                "select count(*) from calendar_event_attendees where event_id = 'evt_sbx_kim'"))
                .isEqualTo(1);
        assertThat(
                        count(
                                "select count(*) from calendar_event_attendees where event_id = 'evt_prod_101'"))
                .isEqualTo(1);
    }
}
