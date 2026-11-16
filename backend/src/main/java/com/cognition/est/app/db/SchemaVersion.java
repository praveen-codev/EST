package com.cognition.est.app.db;

import com.cognition.est.app.job.JobExit;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

/**
 * Read-only check that a database carries exactly the versioned Flyway migrations bundled with this
 * build. Headless jobs never migrate: a database that is behind (pending) or ahead (applied
 * versions this build does not know) is refused. Repeatable migrations (the sandbox seed marker)
 * are ignored.
 */
public final class SchemaVersion {
    private SchemaVersion() {}

    public static void requireCurrent(DataSource dataSource, String label) {
        String vendor;
        try (Connection c = dataSource.getConnection()) {
            vendor =
                    c.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres")
                            ? "postgresql"
                            : "h2";
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read " + label + " database metadata", e);
        }
        MigrationInfo[] all =
                Flyway.configure()
                        .dataSource(dataSource)
                        .locations(
                                "classpath:db/migration/common", "classpath:db/migration/" + vendor)
                        .load()
                        .info()
                        .all();
        int behind = 0;
        int ahead = 0;
        int failed = 0;
        for (MigrationInfo m : all) {
            if (m.getVersion() == null) continue;
            if (m.getState().isFailed()) failed++;
            else if (m.getState().isResolved() && !m.getState().isApplied()) behind++;
            else if (m.getState().isApplied() && !m.getState().isResolved()) ahead++;
        }
        if (behind + ahead + failed > 0)
            throw new JobExit.Refused(
                    "Schema mismatch on "
                            + label
                            + ": pending="
                            + behind
                            + " unknown="
                            + ahead
                            + " failed="
                            + failed);
    }
}
