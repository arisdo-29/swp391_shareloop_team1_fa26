package com.shareloop.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Nạp cả migration chung và dữ liệu chỉ dành cho dev trên một database trống. */
class DevSeedIT {

    @Test
    void devSeedsCreateUsersCatalogAreasAndApprovedItems() {
        var postgres = new PostgreSQLContainer("postgres:16");
        postgres.start();
        try {
            var dataSource =
                    new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration", "classpath:db/dev-data")
                    .outOfOrder(true)
                    .load()
                    .migrate();

            var jdbc = new JdbcTemplate(dataSource);
            assertThat(count(jdbc, "SELECT count(*) FROM users WHERE status = 'ACTIVE'"))
                    .isEqualTo(3);
            assertThat(count(jdbc, "SELECT count(*) FROM item_categories")).isEqualTo(8);
            assertThat(count(jdbc, "SELECT count(*) FROM areas")).isEqualTo(6);
            assertThat(count(jdbc, "SELECT count(*) FROM items WHERE status = 'APPROVED'"))
                    .isEqualTo(4);
            assertThat(count(jdbc, "SELECT count(*) FROM items WHERE offer_type = 'GIVE'"))
                    .isEqualTo(3);
            assertThat(count(jdbc, "SELECT count(*) FROM items WHERE offer_type = 'SWAP' AND desired_item IS NOT NULL"))
                    .isEqualTo(1);
            assertThat(count(
                            jdbc,
                            "SELECT count(*) FROM items WHERE condition = 'DEFECTIVE' AND defect_note IS NOT NULL"))
                    .isEqualTo(1);
            assertThat(count(
                            jdbc,
                            "SELECT count(*) FROM items i JOIN users u ON i.donor_id = u.id"
                                    + " WHERE u.email = 'an@shareloop.local'"))
                    .isEqualTo(4);
        } finally {
            postgres.stop();
        }
    }

    private long count(JdbcTemplate jdbc, String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }
}
