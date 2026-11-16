package com.cognition.est.archive;

import org.flywaydb.core.Flyway;

public final class ArchiveMigrator {
    private ArchiveMigrator() {}

    public static void main(String[] args) {
        String url = required("ARCHIVE_JDBC_URL");
        String user = required("ARCHIVE_DB_USER");
        String password = required("ARCHIVE_DB_PASSWORD");
        String locations =
                System.getenv()
                        .getOrDefault(
                                "ARCHIVE_FLYWAY_LOCATIONS",
                                "filesystem:../infra/neon/archive-migrations");

        Flyway.configure()
                .dataSource(url, user, password)
                .locations(locations)
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
