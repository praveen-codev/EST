package com.cognition.est.calendar;

import com.cognition.est.app.config.EstProperties;
import com.cognition.est.app.config.TrackGuard;
import com.cognition.est.app.crypto.TokenCrypto;
import com.cognition.est.app.db.PostgresUrl;
import com.cognition.est.app.db.SchemaVersion;
import com.cognition.est.app.job.JobExit;
import com.cognition.est.audit.AuditRepository;
import com.cognition.est.domain.model.Identity;
import com.cognition.est.domain.model.SyncRun;
import com.cognition.est.domain.repo.CandidateRepository;
import com.cognition.est.domain.repo.CatalogRepository;
import com.cognition.est.domain.repo.ClientRepository;
import com.cognition.est.domain.repo.EventRepository;
import com.cognition.est.domain.repo.IdentityRepository;
import com.cognition.est.domain.repo.JsonCodec;
import com.cognition.est.domain.repo.MemberRepository;
import com.cognition.est.domain.repo.PersonRepository;
import com.cognition.est.domain.repo.SessionRepository;
import com.cognition.est.domain.repo.SuggestionRepository;
import com.cognition.est.domain.repo.SyncRunRepository;
import com.cognition.est.domain.service.EngineContextFactory;
import com.cognition.est.domain.service.SessionOps;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Headless production calendar sync for an external scheduler. Boots only the sync beans (no web
 * server, no Flyway, no schedulers), refuses to run unless the database is the claimed prod track
 * at exactly this build's schema version, then syncs every active identity of one organisation.
 * Each identity runs in its own transaction under the same row lock the API's manual sync takes, so
 * a failure or a concurrent manual sync affects only that identity. Output is one counts line;
 * per-identity errors stay in {@code calendar_sync_runs}. Exit codes are {@link JobExit}.
 *
 * <p>Deliberately not a {@code @Configuration} or {@code @SpringBootConfiguration}: the API's
 * component scan and Spring Boot test discovery must never pick it up.
 */
@EnableConfigurationProperties(EstProperties.class)
@ImportAutoConfiguration({
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    JdbcTemplateAutoConfiguration.class,
    JdbcClientAutoConfiguration.class,
    TransactionAutoConfiguration.class
})
@Import({
    SyncService.class,
    GoogleCalendarProvider.class,
    TokenCrypto.class,
    SessionOps.class,
    EngineContextFactory.class,
    JsonCodec.class,
    AuditRepository.class,
    IdentityRepository.class,
    MemberRepository.class,
    EventRepository.class,
    CandidateRepository.class,
    SyncRunRepository.class,
    SuggestionRepository.class,
    SessionRepository.class,
    ClientRepository.class,
    PersonRepository.class,
    CatalogRepository.class
})
public class CalendarSyncJob {
    static final String DEFAULT_ORG = "org_cognition";

    private final EstProperties props;
    private final SyncService sync;
    private final IdentityRepository identities;
    private final JdbcClient jdbc;
    private final DataSource dataSource;

    public CalendarSyncJob(
            EstProperties props,
            SyncService sync,
            IdentityRepository identities,
            JdbcClient jdbc,
            DataSource dataSource) {
        this.props = props;
        this.sync = sync;
        this.identities = identities;
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    public static void main(String[] args) {
        System.exit(run(System.getenv(), args));
    }

    static int run(Map<String, String> env, String... args) {
        try (ConfigurableApplicationContext context =
                new SpringApplicationBuilder(CalendarSyncJob.class)
                        .web(WebApplicationType.NONE)
                        .bannerMode(Banner.Mode.OFF)
                        .logStartupInfo(false)
                        .properties(defaults(env))
                        .run(args)) {
            return context.getBean(CalendarSyncJob.class)
                    .execute(env.getOrDefault("EST_SYNC_ORG", DEFAULT_ORG), Instant.now());
        } catch (JobExit.Refused e) {
            System.err.println("Calendar sync job refused: " + e.getMessage());
            return JobExit.REFUSED;
        } catch (Exception e) {
            System.err.println("Calendar sync job failed: " + e.getClass().getSimpleName());
            return JobExit.ERROR;
        }
    }

    private static Map<String, Object> defaults(Map<String, String> env) {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("spring.profiles.default", "prod");
        defaults.put("spring.main.register-shutdown-hook", "false");
        defaults.put("spring.flyway.enabled", "false");
        defaults.put("EST_DB_POOL_SIZE", "2");
        // Counts only: provider and sync diagnostics go to calendar_sync_runs, not the log.
        defaults.put("logging.level.com.cognition.est", "OFF");
        defaults.put("logging.level.com.zaxxer.hikari", "WARN");
        defaults.put("logging.level.org.flywaydb", "WARN");
        String url = env.get("EST_PROD_DATABASE_URL");
        if (url != null && !url.isBlank()) {
            PostgresUrl db = PostgresUrl.parse(url);
            defaults.put("SPRING_DATASOURCE_URL", db.jdbcUrl());
            defaults.put("SPRING_DATASOURCE_USERNAME", db.username());
            defaults.put("SPRING_DATASOURCE_PASSWORD", db.password());
        }
        return defaults;
    }

    int execute(String orgId, Instant now) {
        try {
            TrackGuard.check(props);
        } catch (IllegalStateException e) {
            throw new JobExit.Refused(e.getMessage());
        }
        if (!TrackGuard.PROD.equals(props.track()))
            throw new JobExit.Refused("runs only on the prod track, not " + props.track());
        if (!props.calendarSyncEnabled())
            throw new JobExit.Refused("calendar sync is disabled for this track");
        if (props.google().mock()) throw new JobExit.Refused("mock Google is enabled");
        SchemaVersion.requireCurrent(dataSource, "production");
        String claimed =
                TrackGuard.claimed(jdbc)
                        .orElseThrow(() -> new JobExit.Refused("database has no track claim"));
        if (!TrackGuard.PROD.equals(claimed))
            throw new JobExit.Refused("database is claimed by the " + claimed + " track");

        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (String key :
                new String[] {
                    "identities", "ok", "failed", "busy", "seen", "new", "updated", "candidates"
                }) counts.put(key, 0);
        for (Identity identity : identities.findActive(orgId)) {
            counts.merge("identities", 1, Integer::sum);
            Optional<SyncRun> run;
            try {
                run = sync.syncIdentity(orgId, identity, "incremental", now);
            } catch (RuntimeException e) {
                // The identity's transaction rolled back its run record, so report the cause here.
                System.err.println(
                        "Calendar sync failed for identity "
                                + identity.id()
                                + ": "
                                + e.getClass().getSimpleName());
                counts.merge("failed", 1, Integer::sum);
                continue;
            }
            if (run.isEmpty()) {
                counts.merge("busy", 1, Integer::sum);
                continue;
            }
            SyncRun r = run.get();
            counts.merge("ok".equals(r.status()) ? "ok" : "failed", 1, Integer::sum);
            counts.merge("seen", r.eventsSeen(), Integer::sum);
            counts.merge("new", r.eventsNew(), Integer::sum);
            counts.merge("updated", r.eventsUpdated(), Integer::sum);
            counts.merge("candidates", r.candidatesCreated(), Integer::sum);
        }
        System.out.println(
                "Calendar sync job "
                        + (counts.get("failed") > 0 ? "partial" : "completed")
                        + ": "
                        + String.join(
                                ", ",
                                counts.entrySet().stream()
                                        .map(e -> e.getKey() + "=" + e.getValue())
                                        .toList()));
        return counts.get("failed") > 0 ? JobExit.PARTIAL : JobExit.OK;
    }
}
