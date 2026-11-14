package com.cognition.est.app.config;

import static com.cognition.est.app.db.JdbcValues.timestamp;

import java.time.Instant;
import java.util.Optional;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Refuses to start when developer conveniences would run on production: dev endpoints and mock
 * Google are never allowed on the prod track, and every database is bound to the first track that
 * opened it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TrackGuard implements ApplicationRunner {
    public static final String PROD = "prod";

    private final EstProperties props;
    private final JdbcClient jdbc;

    public TrackGuard(EstProperties props, JdbcClient jdbc) {
        this.props = props;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        check(props);
        claim(jdbc, props.track(), Instant.now());
    }

    public static void check(EstProperties props) {
        boolean developer = props.dev().endpointsEnabled() || props.google().mock();
        if (developer && PROD.equals(props.track()))
            throw new IllegalStateException(
                    "Dev endpoints and mock Google are not allowed on the prod track");
    }

    public static Optional<String> claimed(JdbcClient jdbc) {
        return jdbc.sql("select track from deployment_track where id = 1")
                .query(String.class)
                .optional();
    }

    public static void claim(JdbcClient jdbc, String track, Instant now) {
        String owner = claimed(jdbc).orElse(null);
        if (owner == null) {
            jdbc.sql(
                            "insert into deployment_track (id, track, claimed_at) values (1, :track, :now)")
                    .param("track", track)
                    .param("now", timestamp(now))
                    .update();
        } else if (!owner.equals(track)) {
            throw new IllegalStateException(
                    "This database belongs to the "
                            + owner
                            + " track; refusing to use it as "
                            + track);
        }
    }
}
