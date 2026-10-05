package com.shareloop.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Không gắn @Transactional: INSERT và UPDATE phải commit ở hai transaction riêng (now() là giờ bắt đầu transaction). */
class SchemaMigrationIT extends IntegrationTest {

    private static final List<String> TABLES = List.of(
            "users",
            "areas",
            "website_attributes",
            "activity_logs",
            "notifications",
            "item_categories",
            "item_attributes",
            "items",
            "item_attribute_mappings",
            "media_files",
            "item_media_mappings",
            "requests",
            "request_media_mappings",
            "messages",
            "reports",
            "payment_orders",
            "credit_ledger");

    private static final List<String> MAIN_TABLES = List.of("users", "items", "requests", "reports", "media_files");

    private static final List<String> CATALOG_TABLES =
            List.of("item_categories", "item_attributes", "areas", "website_attributes");

    private static final List<String> AUDIT_COLUMNS =
            List.of("created_at", "updated_at", "created_by", "updated_by", "is_active", "is_deleted");

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayAppliedAllMigrations() {
        assertThat(flyway.info().pending()).isEmpty();
        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        assertThat(current.getState().isApplied()).isTrue();
        assertThat(flyway.info().applied())
                .extracting(migration -> migration.getVersion().getVersion())
                .contains("1", "202610051200");
    }

    @Test
    void allSeventeenTablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables"
                        + " WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class);
        assertThat(tables).containsAll(TABLES);
    }

    @Test
    void mainTablesHaveSevenStandardColumns() {
        for (String table : MAIN_TABLES) {
            List<String> columns = columnsOf(table);
            assertThat(columns).as(table).containsAll(AUDIT_COLUMNS).contains("status");
        }
    }

    @Test
    void catalogTablesHaveSixStandardColumnsAndNoStatus() {
        for (String table : CATALOG_TABLES) {
            List<String> columns = columnsOf(table);
            assertThat(columns).as(table).containsAll(AUDIT_COLUMNS).doesNotContain("status");
        }
    }

    @Test
    void extensionsAreInstalled() {
        List<String> extensions = jdbc.queryForList("SELECT extname FROM pg_extension", String.class);
        assertThat(extensions).contains("unaccent", "pg_trgm");
    }

    @Test
    void fUnaccentStripsDiacritics() {
        String result = jdbc.queryForObject("SELECT f_unaccent('Áo khoác')", String.class);
        assertThat(result).isEqualTo("Ao khoac");
    }

    @Test
    void updatedAtTriggerIsAttachedToTenTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT DISTINCT event_object_table FROM information_schema.triggers"
                        + " WHERE trigger_schema = 'public' AND event_manipulation = 'UPDATE'"
                        + " AND action_statement LIKE '%set_updated_at%'",
                String.class);
        assertThat(tables)
                .containsExactlyInAnyOrder(
                        "users",
                        "items",
                        "requests",
                        "reports",
                        "media_files",
                        "item_categories",
                        "item_attributes",
                        "areas",
                        "website_attributes",
                        "payment_orders");
    }

    @Test
    void updatedAtAdvancesOnUpdate() throws InterruptedException {
        Long id =
                jdbc.queryForObject("INSERT INTO areas (name, level) VALUES ('Test area', 1) RETURNING id", Long.class);
        try {
            Timestamp before = jdbc.queryForObject("SELECT updated_at FROM areas WHERE id = ?", Timestamp.class, id);

            Thread.sleep(50);
            jdbc.update("UPDATE areas SET name = 'Test area 2' WHERE id = ?", id);

            Timestamp after = jdbc.queryForObject("SELECT updated_at FROM areas WHERE id = ?", Timestamp.class, id);
            assertThat(after).isAfter(before);
        } finally {
            jdbc.update("DELETE FROM areas WHERE id = ?", id);
        }
    }

    private List<String> columnsOf(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns"
                        + " WHERE table_schema = 'public' AND table_name = ?",
                String.class,
                table);
    }
}
