package com.shareloop.setting.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.setting.dto.ChecklistItem;
import com.shareloop.setting.dto.LexiconEntry;
import com.shareloop.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ConfigServiceIT extends IntegrationTest {

    @Autowired
    ConfigService configService;

    @Autowired
    AdminLexiconService adminLexiconService;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM website_attributes WHERE attr_key LIKE 'config_service_it_%'");
    }

    @Test
    void readsSeededLexiconAndChecklistInConfiguredOrder() {
        List<LexiconEntry> lexicon = configService.lexicon();
        List<ChecklistItem> checklist = configService.checklistItems();

        assertThat(lexicon).extracting(LexiconEntry::term).containsSubsequence("súng", "cần sa", "zalo");
        assertThat(lexicon)
                .filteredOn(rule -> rule.term().equals("súng"))
                .singleElement()
                .satisfies(rule -> {
                    assertThat(rule.category()).isEqualTo("WEAPONS");
                    assertThat(rule.severity()).isEqualTo("BLOCK");
                    assertThat(rule.matchMode()).isEqualTo("WORD");
                    assertThat(rule.exceptions()).contains("súng nước", "súng bắn keo");
                });
        assertThat(lexicon).filteredOn(rule -> rule.severity().equals("REVIEW")).isNotEmpty();
        assertThat(checklist)
                .extracting(ChecklistItem::statement)
                .hasSizeGreaterThanOrEqualTo(1)
                .startsWith("Món đồ không thuộc diện tài sản phải đăng ký sang tên hay công chứng.");
    }

    @Test
    void q01_everySeededChecklistItemHasPassConditionWithoutSemicolon() {
        List<ChecklistItem> checklist = configService.checklistItems();

        assertThat(checklist).hasSize(10);
        assertThat(checklist).allSatisfy(item -> {
            assertThat(item.description()).startsWith("Đạt khi").contains("Từ chối nếu");
            assertThat(item.statement()).doesNotContain(";");
            assertThat(item.description()).doesNotContain(";");
        });
        assertThat(checklist)
                .filteredOn(item -> item.key().equals("desired_item"))
                .singleElement()
                .satisfies(item ->
                        assertThat(item.statement()).isEqualTo("Nhu cầu trao đổi rõ ràng, không ghi chung chung."));
    }

    @Test
    void u08_migrationSeedsHourWindowsAndSoftDeletesDayKeys() {
        // Đọc từ dòng DB do migration ghi, không phải giá trị mặc định trong ConfigKey
        assertThat(jdbc.queryForObject(
                        "SELECT attr_value FROM website_attributes WHERE attr_group = 'CONFIG'"
                                + " AND attr_key = 'complaint_window_hours' AND is_deleted = FALSE",
                        String.class))
                .isEqualTo("24");
        assertThat(jdbc.queryForObject(
                        "SELECT attr_value FROM website_attributes WHERE attr_group = 'CONFIG'"
                                + " AND attr_key = 'response_window_hours' AND is_deleted = FALSE",
                        String.class))
                .isEqualTo("48");
        assertThat(configService.getInt(com.shareloop.setting.ConfigKey.COMPLAINT_WINDOW_HOURS))
                .isEqualTo(24);
        assertThat(configService.getInt(com.shareloop.setting.ConfigKey.RESPONSE_WINDOW_HOURS))
                .isEqualTo(48);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM website_attributes WHERE attr_group = 'CONFIG' AND is_deleted = FALSE AND"
                                + " attr_key IN ('complaint_window_days', 'response_window_days',"
                                + " 'DISPUTE_WINDOW_DAYS', 'DISPUTE_RESPONSE_DAYS')",
                        Integer.class))
                .isZero();
    }

    @Test
    void ignoresInactiveLexiconAndChecklistRows() {
        jdbc.update("INSERT INTO website_attributes (attr_group, attr_key, attr_value, extra, sort_order, is_active) "
                + "VALUES ('BANNED_KEYWORD', 'config_service_it_inactive', 'inactive', "
                + "'{\"severity\":\"BLOCK\"}'::jsonb, -1, FALSE), "
                + "('CHECKLIST', 'config_service_it_inactive', 'Inactive checklist item', NULL, -1, FALSE)");

        assertThat(configService.lexicon()).extracting(LexiconEntry::term).doesNotContain("config_service_it_inactive");
        assertThat(configService.checklistItems())
                .extracting(ChecklistItem::statement)
                .doesNotContain("Inactive checklist item");
    }

    @Test
    void deactivatedAdminLexiconEntryIsNoLongerReturnedByConfigService() {
        Long id = jdbc.queryForObject(
                "INSERT INTO website_attributes (attr_group, attr_key, attr_value, extra, sort_order) "
                        + "VALUES ('BANNED_KEYWORD', 'config_service_it_hidden', 'integration hidden keyword', "
                        + "'{\"category\":\"TEST\",\"severity\":\"REVIEW\",\"matchMode\":\"WORD\","
                        + "\"exceptions\":[],\"scope\":\"POST\"}'::jsonb, 999) RETURNING id",
                Long.class);

        adminLexiconService.setActive(id, false, null);

        assertThat(configService.lexicon()).extracting(LexiconEntry::term).doesNotContain("integration hidden keyword");
        assertThat(adminLexiconService.listAll())
                .filteredOn(entry -> entry.id().equals(id))
                .singleElement()
                .satisfies(entry -> assertThat(entry.active()).isFalse());
    }
}
