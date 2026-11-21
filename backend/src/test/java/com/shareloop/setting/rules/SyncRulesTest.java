package com.shareloop.setting.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.setting.ConfigKey;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SyncRulesTest {

    private final ConfigRuleEngine engine = new ConfigRuleEngine();
    private final ConfigSnapshot before = ConfigSnapshot.defaults();

    @Test
    void boostDays_whenIncreasedFrom3To4_syncsMaxDaysFrom6To8() {
        // given: mặc định boost_days = 3, boost_max_days = 6 (2 gói)
        Map<ConfigKey, String> requested = Map.of(ConfigKey.BOOST_DAYS, "4");

        // when
        RuleResult result = engine.run(before, requested);

        // then
        assertThat(syncedValue(result, ConfigKey.BOOST_MAX_DAYS)).isEqualTo("8");
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void boostDays_whenAdminAlsoSendsMaxDays_keepsAdminValue() {
        // given: Admin gửi cả hai khoá, boost_max_days = 12 phải được giữ nguyên
        Map<ConfigKey, String> requested = Map.of(ConfigKey.BOOST_DAYS, "4", ConfigKey.BOOST_MAX_DAYS, "12");

        // when
        RuleResult result = engine.run(before, requested);

        // then
        assertThat(result.changes()).noneMatch(change -> change.causedByKey() != null);
        assertThat(result.changes())
                .filteredOn(change -> change.key() == ConfigKey.BOOST_MAX_DAYS)
                .singleElement()
                .extracting(ConfigChange::newValue)
                .isEqualTo("12");
    }

    @Test
    void boostDays_whenRaisedTo20_syncsMaxDaysTo40AndReportsAboveDisplayDays() {
        // given: boost_days = 20 kéo boost_max_days = 40 trong khi bài chỉ hiển thị 30 ngày
        Map<ConfigKey, String> requested = Map.of(ConfigKey.BOOST_DAYS, "20");

        // when
        RuleResult result = engine.run(before, requested);

        // then
        assertThat(syncedValue(result, ConfigKey.BOOST_MAX_DAYS)).isEqualTo("40");
        assertThat(result.errors())
                .anyMatch(error -> error.keys().contains(ConfigKey.POST_DISPLAY_DAYS)
                        && error.keys().contains(ConfigKey.BOOST_MAX_DAYS));
    }

    @Test
    void postFee_whenChangedFrom5To7_syncsEditFeeRenewFeeAndOverQuota() {
        // given: edit_fee, renew_fee đang bằng post_fee cũ (5), post_fee_over_quota = 10
        Map<ConfigKey, String> requested = Map.of(ConfigKey.POST_FEE, "7");

        // when
        RuleResult result = engine.run(before, requested);

        // then
        assertThat(syncedValue(result, ConfigKey.EDIT_FEE)).isEqualTo("7");
        assertThat(syncedValue(result, ConfigKey.RENEW_FEE)).isEqualTo("7");
        assertThat(syncedValue(result, ConfigKey.POST_FEE_OVER_QUOTA)).isEqualTo("14");
    }

    @Test
    void postFee_whenEditFeeWasSetDifferently_doesNotSyncEditFee() {
        // given: Admin đã cố tình đặt edit_fee khác post_fee
        ConfigSnapshot custom = before.with(ConfigKey.EDIT_FEE, "9");

        // when
        RuleResult result = engine.run(custom, Map.of(ConfigKey.POST_FEE, "7"));

        // then
        assertThat(syncedValue(result, ConfigKey.EDIT_FEE)).isNull();
        assertThat(syncedValue(result, ConfigKey.RENEW_FEE)).isEqualTo("7");
    }

    @Test
    void postFee_whenRatioIsFractional_roundsOverQuotaUp() {
        // given: over_quota 7, post_fee 5 -> 6: 7 * 6 / 5 = 8,4 phải làm tròn lên thành 9
        ConfigSnapshot custom = before.with(ConfigKey.POST_FEE_OVER_QUOTA, "7");

        // when
        RuleResult result = engine.run(custom, Map.of(ConfigKey.POST_FEE, "6"));

        // then
        assertThat(syncedValue(result, ConfigKey.POST_FEE_OVER_QUOTA)).isEqualTo("9");
    }

    @Test
    void postDisplayDays_whenIncreased_keepsGoldGap() {
        // given: mặc định 30 và 45 (chênh 15)
        Map<ConfigKey, String> requested = Map.of(ConfigKey.POST_DISPLAY_DAYS, "40");

        // when
        RuleResult result = engine.run(before, requested);

        // then
        assertThat(syncedValue(result, ConfigKey.POST_DISPLAY_DAYS_GOLD)).isEqualTo("55");
    }

    @Test
    void postDisplayDays_whenGoldWasBelowBase_syncsGoldNotBelowNewBase() {
        // given: hạng Vàng đang ngắn hơn mặc định (chênh âm)
        ConfigSnapshot custom = before.with(ConfigKey.POST_DISPLAY_DAYS_GOLD, "20");

        // when
        RuleResult result = engine.run(custom, Map.of(ConfigKey.POST_DISPLAY_DAYS, "40"));

        // then
        assertThat(syncedValue(result, ConfigKey.POST_DISPLAY_DAYS_GOLD)).isEqualTo("40");
    }

    @Test
    void sync_whenSourceKeyUnchanged_doesNothing() {
        // given: Admin gửi lại đúng giá trị cũ
        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.BOOST_DAYS, before.get(ConfigKey.BOOST_DAYS)));

        // then
        assertThat(result.changes()).isEmpty();
    }

    private static String syncedValue(RuleResult result, ConfigKey key) {
        return result.changes().stream()
                .filter(change -> change.key() == key && change.causedByKey() != null)
                .map(ConfigChange::newValue)
                .findFirst()
                .orElse(null);
    }
}
