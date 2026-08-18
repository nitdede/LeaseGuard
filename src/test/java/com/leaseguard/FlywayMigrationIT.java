package com.leaseguard;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Confirms Flyway applies the baseline migration cleanly to an empty PostgreSQL database and
 * that Hibernate's schema validation (ddl-auto=validate) agrees with the result - if either
 * failed, the shared {@code @SpringBootTest} context used by every integration test would not
 * start at all, so a successful context load is itself evidence, but this test asserts the
 * concrete facts explicitly.
 */
class FlywayMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void flywayAppliedExactlyOneMigrationAndCreatedCoreTables() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(appliedMigrations).isEqualTo(1);

        for (String table : new String[] {"properties", "tenants", "leases", "lease_actions", "import_batches"}) {
            Integer tableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?", Integer.class, table);
            assertThat(tableCount).as("table %s should exist", table).isEqualTo(1);
        }
    }
}
