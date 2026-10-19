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
                .startsWith("Không cần sang tên, đăng ký hay công chứng để đổi chủ.");
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
