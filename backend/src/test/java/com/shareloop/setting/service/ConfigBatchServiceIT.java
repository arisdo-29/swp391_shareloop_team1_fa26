package com.shareloop.setting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.dto.BatchUpdateConfigResponse;
import com.shareloop.setting.dto.ConfigChangeItem;
import com.shareloop.setting.dto.ConfigPreviewResponse;
import com.shareloop.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class ConfigBatchServiceIT extends IntegrationTest {

    @Autowired
    ConfigBatchService batchService;

    @Autowired
    JdbcTemplate jdbc;

    private long adminId;
    private List<Map<String, Object>> originalConfigRows;

    // activity_logs.actor_id tham chiếu users nên Admin trong test phải là một user có thật.
    @BeforeEach
    void setUp() {
        adminId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, full_name, phone, status)"
                        + " VALUES (?, 'not-a-real-hash', 'Config Batch IT', '0988000002', 'ACTIVE') RETURNING id",
                Long.class,
                "config-batch-it-" + System.nanoTime() + "@test.local");
        originalConfigRows = jdbc.queryForList(
                "SELECT id, attr_value FROM website_attributes WHERE attr_group = 'CONFIG' AND is_deleted = FALSE");
        // Đưa các khoá dùng trong test về giá trị đã biết, vì DB dùng chung với các test khác.
        setValue(ConfigKey.BOOST_DAYS, "3");
        setValue(ConfigKey.BOOST_MAX_DAYS, "6");
        setValue(ConfigKey.POST_DISPLAY_DAYS, "30");
        setValue(ConfigKey.BOOST_FEE, "5");
    }

    @AfterEach
    void cleanUp() {
        for (Map<String, Object> row : originalConfigRows) {
            jdbc.update(
                    "UPDATE website_attributes SET attr_value = ? WHERE id = ?", row.get("attr_value"), row.get("id"));
        }
        jdbc.update("DELETE FROM activity_logs WHERE actor_id = ?", adminId);
        jdbc.update("DELETE FROM users WHERE id = ?", adminId);
    }

    @Test
    void updateBatch_whenBoostDaysChanges_savesSyncedMaxDaysAndLogsBothKeys() {
        // given: gói 3 ngày, tối đa 6 ngày (2 gói)
        List<ConfigChangeItem> items = List.of(new ConfigChangeItem("boost_days", "4"));

        // when
        BatchUpdateConfigResponse response = batchService.updateBatch(items, "Đổi gói đẩy", false, adminId);

        // then: cả hai khoá được lưu, giữ 2 gói nên tối đa thành 8
        assertThat(valueOf(ConfigKey.BOOST_DAYS)).isEqualTo("4");
        assertThat(valueOf(ConfigKey.BOOST_MAX_DAYS)).isEqualTo("8");
        assertThat(response.configs())
                .extracting(config -> config.key())
                .containsExactly("boost_days", "boost_max_days");
        assertThat(logCount("boost_days")).isEqualTo(1);
        assertThat(syncedFromOfLog("boost_max_days")).isEqualTo("boost_days");
        assertThat(syncedFromOfLog("boost_days")).isNull();
    }

    @Test
    void updateBatch_whenRuleErrorFound_throwsBadRequestAndSavesNothing() {
        // given: gói 20 ngày kéo tối đa 40 ngày, vượt 30 ngày hiển thị bài
        List<ConfigChangeItem> items = List.of(new ConfigChangeItem("boost_days", "20"));

        // when / then
        assertThatThrownBy(() -> batchService.updateBatch(items, "Thử", false, adminId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(SettingErrorCode.SETTING_CONFIG_RULE_VIOLATION));
        assertThat(valueOf(ConfigKey.BOOST_DAYS)).isEqualTo("3");
        assertThat(valueOf(ConfigKey.BOOST_MAX_DAYS)).isEqualTo("6");
    }

    @Test
    void updateBatch_whenOneRequestedKeyBreaksRule_savesNoKeyAtAll() {
        // given: boost_fee hợp lệ, nhưng item_photos_min lớn hơn item_photos_max mặc định
        List<ConfigChangeItem> items =
                List.of(new ConfigChangeItem("boost_fee", "7"), new ConfigChangeItem("item_photos_min", "99"));

        // when / then
        assertThatThrownBy(() -> batchService.updateBatch(items, "Thử", false, adminId))
                .isInstanceOf(BusinessException.class);
        assertThat(valueOf(ConfigKey.BOOST_FEE)).isEqualTo("5");
    }

    @Test
    void updateBatch_whenLogInsertViolatesForeignKey_rollsBackBothKeysAndWritesNoLog() {
        // given: actor_id không tồn tại trong users nên ghi nhật ký vi phạm khoá ngoại. Cách này ép lỗi thật từ DB mà
        // không cần mock hay thêm context Spring (mỗi context giữ một pool kết nối tới Postgres dùng chung).
        long missingAdminId = -1L;
        int configLogsBefore = configUpdateLogCount();
        List<ConfigChangeItem> items = List.of(new ConfigChangeItem("boost_days", "4"));

        // when / then: lỗi DB lan ra ngoài
        assertThatThrownBy(() -> batchService.updateBatch(items, "Đổi gói", false, missingAdminId))
                .isInstanceOf(DataAccessException.class);

        // then: cả khoá chính (boost_days) và khoá đồng bộ (boost_max_days) giữ giá trị cũ, không có log CONFIG_UPDATE
        // mới
        assertThat(valueOf(ConfigKey.BOOST_DAYS)).isEqualTo("3");
        assertThat(valueOf(ConfigKey.BOOST_MAX_DAYS)).isEqualTo("6");
        assertThat(configUpdateLogCount()).isEqualTo(configLogsBefore);
    }

    @Test
    void updateBatch_whenWarningNotAcknowledged_throwsConflictAndSavesNothing() {
        // given: phí đẩy bằng 0 sinh cảnh báo
        List<ConfigChangeItem> items = List.of(new ConfigChangeItem("boost_fee", "0"));

        // when / then
        assertThatThrownBy(() -> batchService.updateBatch(items, "Miễn phí đẩy", false, adminId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(SettingErrorCode.SETTING_CONFIG_WARNING_NOT_ACKNOWLEDGED));
        assertThat(valueOf(ConfigKey.BOOST_FEE)).isEqualTo("5");
    }

    @Test
    void updateBatch_whenWarningAcknowledged_savesChange() {
        // given
        List<ConfigChangeItem> items = List.of(new ConfigChangeItem("boost_fee", "0"));

        // when
        batchService.updateBatch(items, "Miễn phí đẩy", true, adminId);

        // then
        assertThat(valueOf(ConfigKey.BOOST_FEE)).isEqualTo("0");
    }

    @Test
    void updateBatch_whenSameKeySentTwice_throwsBadRequest() {
        // given
        List<ConfigChangeItem> items =
                List.of(new ConfigChangeItem("boost_fee", "6"), new ConfigChangeItem("boost_fee", "7"));

        // when / then
        assertThatThrownBy(() -> batchService.updateBatch(items, "Thử", false, adminId))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(SettingErrorCode.SETTING_CONFIG_RULE_VIOLATION));
        assertThat(valueOf(ConfigKey.BOOST_FEE)).isEqualTo("5");
    }

    @Test
    void preview_whenBoostDaysChanges_returnsSyncedChangeAndWritesNothing() {
        // given
        // when
        ConfigPreviewResponse preview = batchService.preview("boost_days", "4");

        // then
        assertThat(preview.changes()).hasSize(2);
        assertThat(preview.changes().get(1).key()).isEqualTo("boost_max_days");
        assertThat(preview.changes().get(1).newValue()).isEqualTo("8");
        assertThat(valueOf(ConfigKey.BOOST_DAYS)).isEqualTo("3");
        assertThat(valueOf(ConfigKey.BOOST_MAX_DAYS)).isEqualTo("6");
    }

    private void setValue(ConfigKey key, String value) {
        jdbc.update(
                "UPDATE website_attributes SET attr_value = ? WHERE attr_group = 'CONFIG' AND attr_key = ?"
                        + " AND is_deleted = FALSE",
                value,
                key.key());
    }

    private String valueOf(ConfigKey key) {
        return jdbc.queryForObject(
                "SELECT attr_value FROM website_attributes WHERE attr_group = 'CONFIG' AND attr_key = ?"
                        + " AND is_deleted = FALSE",
                String.class,
                key.key());
    }

    private int configUpdateLogCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM activity_logs WHERE type = 'CONFIG_UPDATE'", Integer.class);
    }

    private int logCount(String key) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity_logs WHERE actor_id = ? AND data ->> 'key' = ?",
                Integer.class,
                adminId,
                key);
    }

    private String syncedFromOfLog(String key) {
        return jdbc.queryForObject(
                "SELECT data ->> 'syncedFrom' FROM activity_logs WHERE actor_id = ? AND data ->> 'key' = ?",
                String.class,
                adminId,
                key);
    }
}
