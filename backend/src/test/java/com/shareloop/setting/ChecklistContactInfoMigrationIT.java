package com.shareloop.setting;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.Transactional;

/**
 * Migration V202610301000 (Q01 bổ sung) trên PostgreSQL thật: đổi các biến thể cũ của câu contact_info mà
 * V202610291200 bỏ sót, giữ câu Admin đã tự viết lại. @Transactional: mỗi test rollback dữ liệu đã sửa.
 */
@Transactional
class ChecklistContactInfoMigrationIT extends IntegrationTest {

    private static final String MIGRATION = "db/migration/V202610301000__setting_checklist_contact_info_variants.sql";
    private static final String NEW_TEXT =
            "Ảnh và nội dung không chứa số điện thoại, Zalo, link ngoài hay thông tin liên hệ ngầm.";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("Sau khi Flyway chạy: mọi mục checklist đều có mô tả, contact_info là câu Q01 mới")
    void afterMigrations_everyChecklistItemHasDescription() {
        List<Map<String, Object>> withoutDescription = jdbc.queryForList(
                "SELECT attr_key FROM website_attributes WHERE attr_group = 'CHECKLIST' AND is_deleted = FALSE"
                        + " AND (extra IS NULL OR NOT extra ? 'description')");

        assertThat(withoutDescription).isEmpty();
        assertThat(contactInfoText()).isEqualTo(NEW_TEXT);
    }

    @Test
    @DisplayName("Câu đã bỏ phần cờ AI và chưa có mô tả: đổi sang câu mới và thêm mô tả")
    void variantWithoutAiNote_isUpdatedAndGetsDescription() {
        givenContactInfo("Ảnh và mô tả không có thông tin liên hệ, kể cả viết ngầm.");
        jdbc.update("UPDATE website_attributes SET extra = extra - 'description'"
                + " WHERE attr_group = 'CHECKLIST' AND attr_key = 'contact_info'");

        runMigration();

        assertThat(contactInfoText()).isEqualTo(NEW_TEXT);
        assertThat(contactInfoDescription()).startsWith("Đạt khi tiêu đề, mô tả và ảnh");
    }

    @Test
    @DisplayName("Câu hỏi gốc của seed còn nhắc cờ AI: đổi sang câu mới")
    void seedQuestionMentioningAiFlag_isUpdated() {
        givenContactInfo("Ảnh hoặc mô tả có thông tin liên hệ, kể cả viết ngầm? (xem cờ AI)");

        runMigration();

        assertThat(contactInfoText()).isEqualTo(NEW_TEXT);
    }

    @Test
    @DisplayName("Câu và mô tả Admin đã tự viết lại: giữ nguyên")
    void adminRewrittenText_isKept() {
        givenContactInfo("Không để lộ số điện thoại hay Zalo trong bài.");
        jdbc.update("UPDATE website_attributes SET extra = jsonb_build_object('description', 'Mô tả của Admin')"
                + " WHERE attr_group = 'CHECKLIST' AND attr_key = 'contact_info'");

        runMigration();

        assertThat(contactInfoText()).isEqualTo("Không để lộ số điện thoại hay Zalo trong bài.");
        assertThat(contactInfoDescription()).isEqualTo("Mô tả của Admin");
    }

    private void givenContactInfo(String text) {
        jdbc.update(
                "UPDATE website_attributes SET attr_value = ? WHERE attr_group = 'CHECKLIST' AND attr_key = 'contact_info'",
                text);
    }

    // ResourceDatabasePopulator lấy connection qua DataSourceUtils nên chạy trong transaction của test, rollback cùng.
    private void runMigration() {
        new ResourceDatabasePopulator(new ClassPathResource(MIGRATION)).execute(dataSource);
    }

    private String contactInfoText() {
        return jdbc.queryForObject(
                "SELECT attr_value FROM website_attributes WHERE attr_group = 'CHECKLIST' AND attr_key = 'contact_info'"
                        + " AND is_deleted = FALSE",
                String.class);
    }

    private String contactInfoDescription() {
        return jdbc.queryForObject(
                "SELECT extra->>'description' FROM website_attributes WHERE attr_group = 'CHECKLIST'"
                        + " AND attr_key = 'contact_info' AND is_deleted = FALSE",
                String.class);
    }
}
