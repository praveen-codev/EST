package com.cognition.est.app.db;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Set;

/**
 * A {@code postgres://user:password@host[:port]/db[?sslmode=...]} URL (the form Neon and most
 * secret stores hand out) converted to JDBC. {@code sslmode} defaults to {@code require}.
 */
public record PostgresUrl(String jdbcUrl, String username, String password) {
    private static final Set<String> SSL_MODES =
            Set.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full");

    public static PostgresUrl parse(String value) {
        URI uri = URI.create(value.trim());
        if (!Set.of("postgres", "postgresql").contains(uri.getScheme()))
            throw new IllegalArgumentException("Database URL must use postgres or postgresql");
        if (uri.getHost() == null || uri.getRawUserInfo() == null)
            throw new IllegalArgumentException("Database URL must include user and host");
        String[] userInfo = uri.getRawUserInfo().split(":", 2);
        String user = URLDecoder.decode(userInfo[0], StandardCharsets.UTF_8);
        String password =
                userInfo.length == 2 ? URLDecoder.decode(userInfo[1], StandardCharsets.UTF_8) : "";
        String database =
                uri.getPath() == null || uri.getPath().length() <= 1
                        ? "neondb"
                        : uri.getPath().substring(1);
        String sslMode = queryParam(uri.getRawQuery(), "sslmode");
        if (sslMode == null) sslMode = "require";
        if (!SSL_MODES.contains(sslMode))
            throw new IllegalArgumentException("Unsupported sslmode in database URL");
        String jdbc =
                "jdbc:postgresql://"
                        + uri.getHost()
                        + ":"
                        + (uri.getPort() == -1 ? 5432 : uri.getPort())
                        + "/"
                        + database
                        + "?sslmode="
                        + sslMode
                        + "&stringtype=unspecified";
        return new PostgresUrl(jdbc, user, password);
    }

    /** Same URL with the driver forcing read-only transactions even in autocommit mode. */
    public PostgresUrl readOnly() {
        return new PostgresUrl(jdbcUrl + "&readOnly=true&readOnlyMode=always", username, password);
    }

    public Connection connect() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", username);
        props.setProperty("password", password);
        return DriverManager.getConnection(jdbcUrl, props);
    }

    @Override
    public String toString() {
        return "PostgresUrl[redacted]";
    }

    private static String queryParam(String rawQuery, String name) {
        if (rawQuery == null) return null;
        for (String pair : rawQuery.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name))
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
        }
        return null;
    }
}
