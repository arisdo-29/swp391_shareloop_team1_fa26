package com.shareloop.setting.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.SettingErrorCode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErrorRulesTest {

    private final ConfigRuleEngine engine = new ConfigRuleEngine();
    private final ConfigSnapshot before = ConfigSnapshot.defaults();

    @Test
    void photoRange_whenMinAboveMax_returnsError() {
        // given: ảnh tối đa mặc định 8
        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.ITEM_PHOTOS_MIN, "9"));

        // then
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.keys()).containsExactly(ConfigKey.ITEM_PHOTOS_MIN, ConfigKey.ITEM_PHOTOS_MAX);
            assertThat(error.errorCode()).isEqualTo(SettingErrorCode.SETTING_CONFIG_RULE_VIOLATION);
        });
    }

    @Test
    void boostSlots_whenPerUserAboveMaxActive_returnsError() {
        // given: số suất toàn hệ thống mặc định 30
        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.BOOST_MAX_PER_USER, "31"));

        // then
        assertThat(result.errors())
                .singleElement()
                .satisfies(error -> assertThat(error.keys())
                        .containsExactly(ConfigKey.BOOST_MAX_PER_USER, ConfigKey.BOOST_MAX_ACTIVE));
    }

    @Test
    void boostSlots_whenPerPageAboveMaxActive_returnsError() {
        // given
        // when: hạ số suất toàn hệ thống xuống thấp hơn số bài mỗi trang (3)
        RuleResult result = engine.run(before, Map.of(ConfigKey.BOOST_MAX_ACTIVE, "2"));

        // then
        assertThat(result.errors()).anyMatch(error -> error.keys().contains(ConfigKey.BOOST_MAX_PER_PAGE));
    }

    @Test
    void boostMaxDaysMultiple_whenNotMultiple_suggestsLowerAndUpper() {
        // given: gói 3 ngày, ngày hiển thị 30
        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.BOOST_MAX_DAYS, "7"));

        // then
        assertThat(result.errors())
                .singleElement()
                .satisfies(error -> assertThat(error.message())
                        .isEqualTo(
                                "Số ngày tối đa một đợt đẩy phải là bội số của số ngày mỗi gói (3). Gợi ý: 6 hoặc 9"));
    }

    @Test
    void boostMaxDaysMultiple_whenUpperSuggestionAboveDisplayDays_dropsUpper() {
        // given: gói 4 ngày, tối đa 29 -> số dưới 28, số trên 32 vượt 30 ngày hiển thị
        Map<ConfigKey, String> requested = Map.of(ConfigKey.BOOST_DAYS, "4", ConfigKey.BOOST_MAX_DAYS, "29");

        // when
        RuleResult result = engine.run(before, requested);

        // then
        assertThat(result.errors()).anyMatch(error -> error.message().endsWith("Gợi ý: 28"));
    }

    @Test
    void boostMaxDaysMultiple_whenNoSuggestionFits_reportsWithoutSuggestion() {
        // given: gói 40 ngày, tối đa 30 -> số dưới là 0 (nhỏ hơn một gói), số trên là 40 (vượt 30 ngày hiển thị)
        Map<ConfigKey, String> requested = Map.of(ConfigKey.BOOST_DAYS, "40", ConfigKey.BOOST_MAX_DAYS, "30");

        // when
        RuleResult result = engine.run(before, requested);

        // then
        assertThat(result.errors())
                .anyMatch(error ->
                        error.message().equals("Số ngày tối đa một đợt đẩy phải là bội số của số ngày mỗi gói (40)."));
    }

    @Test
    void activeItemsOrder_whenAscendingWithUnlimitedDiamond_isValid() {
        // given: 5 / 8 / 15 / -1 (mặc định)
        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.MAX_ACTIVE_ITEMS_BRONZE, "6"));

        // then
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void activeItemsOrder_whenSilverUnlimitedButGoldLimited_returnsError() {
        // given: 5 / -1 / 15 / -1
        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.MAX_ACTIVE_ITEMS_SILVER, "-1"));

        // then
        assertThat(result.errors())
                .singleElement()
                .satisfies(error -> assertThat(error.keys())
                        .containsExactly(ConfigKey.MAX_ACTIVE_ITEMS_SILVER, ConfigKey.MAX_ACTIVE_ITEMS_GOLD));
    }

    @Test
    void rankThresholds_whenOrderBroken_keepsOldErrorCodeAndMessage() {
        // given: Bạc mặc định 50, Vàng 150
        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.REPUTATION_SILVER_MIN, "150"));

        // then
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.errorCode()).isEqualTo(SettingErrorCode.SETTING_RANK_THRESHOLD_ORDER);
            assertThat(error.message()).isEqualTo(SettingErrorCode.SETTING_RANK_THRESHOLD_ORDER.defaultMessage());
        });
    }

    @Test
    void rules_whenValueIsNotNumber_skipsSoValidateValueCanReportType() {
        // given
        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.ITEM_PHOTOS_MIN, "abc"));

        // then
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void rules_whenExistingViolationIsUnrelatedToChangedKey_doesNotReportIt() {
        // given: dữ liệu cũ đang lệch sẵn (ảnh tối thiểu 9 > tối đa 8)
        ConfigSnapshot broken = before.with(ConfigKey.ITEM_PHOTOS_MIN, "9");

        // when: Admin chỉ đổi một khoá không liên quan
        RuleResult result = engine.run(broken, Map.of(ConfigKey.IMAGE_MAX_MB, "6"));

        // then
        assertThat(result.errors()).isEmpty();
    }
}
