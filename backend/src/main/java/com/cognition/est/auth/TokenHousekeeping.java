package com.cognition.est.auth;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purges expired refresh-token families and stale PKCE link states so the tables never grow
 * unbounded.
 */
@Component
public class TokenHousekeeping {
    private static final Logger log = LoggerFactory.getLogger(TokenHousekeeping.class);
    private final RefreshTokenRepository families;
    private final JdbcClient jdbc;

    public TokenHousekeeping(RefreshTokenRepository families, JdbcClient jdbc) {
        this.families = families;
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void purge() {
        Instant now = Instant.now();
        int tokens = families.purgeExpired(now);
        int states =
                jdbc.sql("delete from oauth_link_states where expires_at < :now")
                        .param("now", now)
                        .update();
        if (tokens + states > 0)
            log.info(
                    "housekeeping: purged {} refresh-token families, {} link states",
                    tokens,
                    states);
    }
}
