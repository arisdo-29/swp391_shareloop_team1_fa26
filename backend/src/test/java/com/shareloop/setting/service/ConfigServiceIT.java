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

        // M6: food_safety đã xoá mềm nên còn 9 câu
        assertThat(checklist).hasSize(9);
        assertThat(checklist).extracting(ChecklistItem::key).doesNotContain("food_safety");
        // Chỉ bỏ câu checklist, từ khoá cấm về thực phẩm (V202610241200) vẫn chặn tại form
        assertThat(configService.lexicon()).extracting(LexiconEntry::term).contains("thực phẩm tươi sống");
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
    void l13_migrationSoftDeletesUnusedKeysAndKeepsSeriousReasonsEditable() {
        // Khoá không còn đọc ở đâu và khoá chữ hoa trùng đều đã xoá mềm
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM website_attributes WHERE attr_group = 'CONFIG' AND is_deleted = FALSE AND"
                                + " attr_key IN ('renew_expired_window_days', 'media_orphan_hours',"
                                + " 'dispute_window_days', 'dispute_response_days',"
                                + " 'REPUTATION_SILVER_MIN', 'REPUTATION_GOLD_MIN', 'REPUTATION_DIAMOND_MIN',"
                                + " 'REPUTATION_GIVE_DONOR', 'REPUTATION_GIVE_RECEIVER', 'REPUTATION_SWAP_EACH',"
                                + " 'REPORT_SERIOUS_REASONS')",
                        Integer.class))
                .isZero();
        // Khoá chữ thường vẫn còn: hạng tín nhiệm đọc đúng dòng seed, lý do nghiêm trọng đọc qua ConfigKey
        assertThat(configService.getInt(com.shareloop.setting.ConfigKey.REPUTATION_SILVER_MIN))
                .isPositive();
        assertThat(configService.getCodes(com.shareloop.setting.ConfigKey.REPORT_SERIOUS_REASONS))
                .containsExactlyInAnyOrder("PROHIBITED_ITEM", "COUNTERFEIT");
        assertThat(configService.listConfigs())
                .extracting(config -> config.key())
                .contains("report_serious_reasons")
                .doesNotContain("renew_expired_window_days", "media_orphan_hours");
    }

    @Test
    void boostRotationMigration_seedsNewKeysShortensMaxDaysAndRevivesMaxPerPage() {
        // given: migration V202611061200 đã chạy khi dựng DB test

        // when: đọc các khoá đẩy bài qua ConfigService (đọc dòng DB, không phải mặc định trong ConfigKey)
        int maxDays = configService.getInt(com.shareloop.setting.ConfigKey.BOOST_MAX_DAYS);
        Integer liveMaxPerPageRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM website_attributes WHERE attr_group = 'CONFIG'"
                        + " AND attr_key = 'boost_max_per_page' AND is_active = TRUE AND is_deleted = FALSE",
                Integer.class);

        // then: khoá mới có dòng để Admin sửa, boost_max_days còn 6, boost_max_per_page được bật lại đúng một dòng
        assertThat(maxDays).isEqualTo(6);
        assertThat(liveMaxPerPageRows).isEqualTo(1);
        assertThat(configService.getInt(com.shareloop.setting.ConfigKey.BOOST_MAX_PER_PAGE))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM website_attributes WHERE attr_group = 'CONFIG' AND is_active = TRUE"
                                + " AND is_deleted = FALSE AND attr_key IN ('boost_cooldown_days',"
                                + " 'boost_max_per_user', 'boost_max_active', 'boost_rotate_seconds',"
                                + " 'boost_ending_notice_hours')",
                        Integer.class))
                .isEqualTo(5);
        // boost_ending_notice_hours do V202611061300 thêm
        assertThat(configService.getInt(com.shareloop.setting.ConfigKey.BOOST_ENDING_NOTICE_HOURS))
                .isEqualTo(24);
        assertThat(configService.listConfigs())
                .extracting(config -> config.key())
                .contains(
                        "boost_cooldown_days",
                        "boost_max_per_user",
                        "boost_max_active",
                        "boost_rotate_seconds",
                        "boost_ending_notice_hours");
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
