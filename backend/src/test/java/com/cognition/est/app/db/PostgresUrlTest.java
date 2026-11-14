package com.cognition.est.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PostgresUrlTest {

    @Test
    void neonUrlBecomesJdbcWithRequiredTlsByDefault() {
        PostgresUrl url = PostgresUrl.parse("postgresql://est_job:p%40ss@ep-x.neon.tech/neondb");
        assertThat(url.jdbcUrl())
                .isEqualTo(
                        "jdbc:postgresql://ep-x.neon.tech:5432/neondb?sslmode=require&stringtype=unspecified");
        assertThat(url.username()).isEqualTo("est_job");
        assertThat(url.password()).isEqualTo("p@ss");
        assertThat(url.toString()).doesNotContain("p@ss").doesNotContain("est_job");
    }

    @Test
    void explicitSslModeIsKeptAndReadOnlyForcesDriverReadOnlyMode() {
        PostgresUrl url =
                PostgresUrl.parse("postgres://u:p@localhost:5433/db?sslmode=verify-full&x=1");
        assertThat(url.jdbcUrl()).contains("localhost:5433/db?sslmode=verify-full");
        assertThat(url.readOnly().jdbcUrl()).endsWith("&readOnly=true&readOnlyMode=always");
    }

    @Test
    void rejectsOtherSchemesAndUnknownSslModes() {
        assertThatThrownBy(() -> PostgresUrl.parse("mysql://u:p@h/db"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PostgresUrl.parse("postgres://u:p@h/db?sslmode=bogus"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PostgresUrl.parse("postgres://h/db"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
