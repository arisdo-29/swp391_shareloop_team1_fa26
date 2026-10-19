package com.shareloop.review.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.review.repository.ReviewItemRepository;
import com.shareloop.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ReviewItemIT extends IntegrationTest {
    @Autowired
    JdbcClient jdbc;

    @Autowired
    ReviewItemRepository repository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    ReviewService reviewService;

    @Autowired
    DataSource dataSource;

    private long itemId;
    private long adminId;

    @BeforeEach
    void createItem() {
        adminId = jdbc.sql("INSERT INTO users (email, password_hash, full_name, phone, status) "
                        + "VALUES (:email, 'unused', 'Review Test', :phone, 'ACTIVE') RETURNING id")
                .param("email", "review-" + UUID.randomUUID() + "@shareloop.local")
                .param(
                        "phone",
                        "09"
                                + String.format(
                                        "%08d",
                                        Integer.toUnsignedLong(UUID.randomUUID().hashCode()) % 100000000))
                .query(Long.class)
                .single();
        itemId = jdbc.sql(
                        "INSERT INTO items (donor_id, category_id, area_id, offer_type, title, description, condition) "
                                + "VALUES (:userId, (SELECT id FROM item_categories WHERE is_active ORDER BY id LIMIT 1), "
                                + "(SELECT id FROM areas WHERE level = 2 ORDER BY id LIMIT 1), 'GIVE', 'Sách', 'Sách còn tốt', 'GOOD') RETURNING id")
                .param("userId", adminId)
                .query(Long.class)
                .single();
    }

    @Test
    void persistsApprovalActorAndSnapshot() {
        Instant approvedAt = Instant.parse("2026-10-19T00:00:00Z");
        repository.findById(itemId).orElseThrow().approve(Map.of("checks", java.util.List.of()), approvedAt, adminId);
        entityManager.flush();

        assertThat(jdbc.sql("SELECT approved_by FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(Long.class)
                        .single())
                .isEqualTo(adminId);
        assertThat(jdbc.sql("SELECT approved_at FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(java.sql.Timestamp.class)
                        .single()
                        .toInstant())
                .isEqualTo(approvedAt);
        assertThat(jdbc.sql("SELECT review_snapshot ->> 'checks' FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(String.class)
                        .single())
                .isEqualTo("[]");
    }

    @Test
    void persistsRejectionActorAndTime() {
        Instant rejectedAt = Instant.parse("2026-10-19T01:00:00Z");
        repository.findById(itemId).orElseThrow().reject("Ảnh mờ", rejectedAt, adminId);
        entityManager.flush();

        assertThat(jdbc.sql("SELECT rejected_by FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(Long.class)
                        .single())
                .isEqualTo(adminId);
        assertThat(jdbc.sql("SELECT rejected_at FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(java.sql.Timestamp.class)
                        .single()
                        .toInstant())
                .isEqualTo(rejectedAt);
        assertThat(jdbc.sql("SELECT reject_reason FROM items WHERE id = :id")
                        .param("id", itemId)
                        .query(String.class)
                        .single())
                .isEqualTo("Ảnh mờ");
    }

    @Test
    void queueReadsJsonMatchedTermsThroughPostgres() {
        jdbc.sql("UPDATE items SET ai_result = '{\"flag\":\"REVIEW\",\"reason\":\"LEXICON_REVIEW\","
                        + "\"matchedTerms\":[\"zalo\"]}'::jsonb WHERE id = :id")
                .param("id", itemId)
                .update();

        assertThat(reviewService.listPending(0, 100))
                .filteredOn(item -> item.id().equals(itemId))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.flagReasonCode()).isEqualTo("LEXICON_REVIEW");
                    assertThat(item.flagReason()).isEqualTo("Có từ khoá cần xem xét: zalo");
                    assertThat(item.matchedTerms()).containsExactly("zalo");
                });
    }

    @Test
    void migrationConvertsKnownQuestionsAndPreservesCustomContentAndOrder() {
        String snapshot = "{\"checks\":[{\"question\":\"Danh mục có đúng với món đồ?\",\"passed\":true},"
                + "{\"question\":\"Điều kiện riêng\",\"passed\":true}]}";
        jdbc.sql("UPDATE items SET review_snapshot = CAST(:snapshot AS jsonb) WHERE id = :id")
                .param("snapshot", snapshot)
                .param("id", itemId)
                .update();
        long logId = jdbc.sql("INSERT INTO activity_logs (type, target_type, target_id, data) "
                        + "VALUES ('REVIEW_APPROVE', 'ITEM', :id, CAST(:snapshot AS jsonb)) RETURNING id")
                .param("id", itemId)
                .param("snapshot", snapshot)
                .query(Long.class)
                .single();
        jdbc.sql("UPDATE website_attributes SET attr_value = 'Danh mục có đúng với món đồ?' "
                        + "WHERE attr_group = 'CHECKLIST' AND attr_key = 'category_match' AND NOT is_deleted")
                .update();
        jdbc.sql("UPDATE website_attributes SET attr_value = 'Điều kiện do Admin sửa' "
                        + "WHERE attr_group = 'CHECKLIST' AND attr_key = 'real_photos' AND NOT is_deleted")
                .update();

        var connection = DataSourceUtils.getConnection(dataSource);
        try {
            ScriptUtils.executeSqlScript(
                    connection, new ClassPathResource("db/migration/V202610191600__setting_checklist_statements.sql"));
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }

        assertThat(jdbc.sql("SELECT attr_value FROM website_attributes WHERE attr_group = 'CHECKLIST' "
                                + "AND attr_key = 'category_match' AND NOT is_deleted")
                        .query(String.class)
                        .single())
                .isEqualTo("Danh mục đúng với món đồ.");
        assertThat(jdbc.sql("SELECT attr_value FROM website_attributes WHERE attr_group = 'CHECKLIST' "
                                + "AND attr_key = 'real_photos' AND NOT is_deleted")
                        .query(String.class)
                        .single())
                .isEqualTo("Điều kiện do Admin sửa");
        String expected = "{\"checks\":[{\"key\":\"category_match\",\"statement\":\"Danh mục đúng với món đồ.\","
                + "\"passed\":true},{\"question\":\"Điều kiện riêng\",\"passed\":true}]}";
        assertThat(jdbc.sql("SELECT review_snapshot = CAST(:expected AS jsonb) FROM items WHERE id = :id")
                        .param("expected", expected)
                        .param("id", itemId)
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assertThat(jdbc.sql("SELECT data = CAST(:expected AS jsonb) FROM activity_logs WHERE id = :id")
                        .param("expected", expected)
                        .param("id", logId)
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }
}
