package com.shareloop.setting.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.SettingErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class ConfigRuleEngineTest {

    private final ConfigSnapshot before = ConfigSnapshot.defaults();

    @Test
    void run_whenSameInputTwice_returnsSameResult() {
        // given
        ConfigRuleEngine engine = new ConfigRuleEngine();
        Map<ConfigKey, String> requested = Map.of(ConfigKey.BOOST_DAYS, "4", ConfigKey.POST_FEE, "7");

        // when
        RuleResult first = engine.run(before, requested);
        RuleResult second = engine.run(before, requested);

        // then
        assertThat(second.changes()).isEqualTo(first.changes());
        assertThat(second.errors()).isEqualTo(first.errors());
        assertThat(second.warnings()).isEqualTo(first.warnings());
    }

    @Test
    void run_doesNotModifyBeforeSnapshot() {
        // given
        ConfigRuleEngine engine = new ConfigRuleEngine();
        Map<ConfigKey, String> beforeValues = before.asMap();

        // when
        engine.run(before, Map.of(ConfigKey.BOOST_DAYS, "4"));

        // then
        assertThat(before.asMap()).isEqualTo(beforeValues);
    }

    @Test
    void sync_whenProposedValueBelowTargetMinValue_returnsErrorWithKeyAndValue() {
        // given: luật giả đồng bộ ip_alert_min_accounts xuống 1, thấp hơn minValue (2)
        ConfigRule rule = (snapshotBefore, after, result) ->
                result.sync(snapshotBefore, after, ConfigKey.IP_ALERT_MIN_ACCOUNTS, "1", ConfigKey.BOOST_DAYS);
        ConfigRuleEngine engine = new ConfigRuleEngine(List.of(rule), List.of(), List.of());

        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.BOOST_DAYS, "4"));

        // then
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.errorCode()).isEqualTo(SettingErrorCode.SETTING_CONFIG_OUT_OF_RANGE);
            assertThat(error.keys()).contains(ConfigKey.IP_ALERT_MIN_ACCOUNTS);
            assertThat(error.message()).contains("1").contains(ConfigKey.IP_ALERT_MIN_ACCOUNTS.label());
        });
        assertThat(result.changes()).noneMatch(change -> change.key() == ConfigKey.IP_ALERT_MIN_ACCOUNTS);
    }

    @Test
    void sync_whenProposedValueAboveTargetMaxValue_returnsErrorWithKeyAndValue() {
        // given: hàm tra khoảng giả cho boost_max_days có max = 30 (ConfigKey thật chưa có khoá nào có max)
        ConfigRule rule = (snapshotBefore, after, result) ->
                result.sync(snapshotBefore, after, ConfigKey.BOOST_MAX_DAYS, "40", ConfigKey.BOOST_DAYS);
        Function<ConfigKey, ValueRange> fakeRanges = key -> key == ConfigKey.BOOST_MAX_DAYS
                ? new ValueRange(BigDecimal.ONE, BigDecimal.valueOf(30))
                : ValueRange.of(key);
        ConfigRuleEngine engine = new ConfigRuleEngine(List.of(rule), List.of(), List.of(), fakeRanges);

        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.BOOST_DAYS, "4"));

        // then
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.errorCode()).isEqualTo(SettingErrorCode.SETTING_CONFIG_OUT_OF_RANGE);
            assertThat(error.keys()).contains(ConfigKey.BOOST_MAX_DAYS);
            assertThat(error.message()).contains("40").contains(ConfigKey.BOOST_MAX_DAYS.label());
        });
        assertThat(result.changes()).noneMatch(change -> change.key() == ConfigKey.BOOST_MAX_DAYS);
    }

    @Test
    void sync_whenTargetIsNotAdminEditable_returnsErrorWithProposedValue() {
        // given: luật giả muốn đổi hạn khiếu nại, khoá cố định
        ConfigRule rule = (snapshotBefore, after, result) ->
                result.sync(snapshotBefore, after, ConfigKey.COMPLAINT_WINDOW_HOURS, "36", ConfigKey.BOOST_DAYS);
        ConfigRuleEngine engine = new ConfigRuleEngine(List.of(rule), List.of(), List.of());

        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.BOOST_DAYS, "4"));

        // then
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.keys()).contains(ConfigKey.COMPLAINT_WINDOW_HOURS);
            assertThat(error.message()).contains("36");
        });
        assertThat(result.changes()).noneMatch(change -> change.key() == ConfigKey.COMPLAINT_WINDOW_HOURS);
    }

    @Test
    void sync_whenTwoRulesWantDifferentValuesForSameKey_returnsSyncConflict() {
        // given
        ConfigRule firstRule = (snapshotBefore, after, result) ->
                result.sync(snapshotBefore, after, ConfigKey.BOOST_MAX_DAYS, "8", ConfigKey.BOOST_DAYS);
        ConfigRule secondRule = (snapshotBefore, after, result) ->
                result.sync(snapshotBefore, after, ConfigKey.BOOST_MAX_DAYS, "9", ConfigKey.POST_FEE);
        ConfigRuleEngine engine = new ConfigRuleEngine(List.of(firstRule, secondRule), List.of(), List.of());

        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.BOOST_DAYS, "4"));

        // then
        assertThat(result.errors())
                .singleElement()
                .satisfies(error ->
                        assertThat(error.errorCode()).isEqualTo(SettingErrorCode.SETTING_CONFIG_SYNC_CONFLICT));
    }

    @Test
    void run_whenRulesNeverSettle_returnsErrorAfterFiveRounds() {
        // given: luật giả mỗi vòng lại đổi một giá trị
        int[] counter = {0};
        ConfigRule neverSettles = (snapshotBefore, after, result) ->
                after.set(ConfigKey.IMAGE_MAX_MB, String.valueOf(100 + counter[0]++));
        ConfigRuleEngine engine = new ConfigRuleEngine(List.of(neverSettles), List.of(), List.of());

        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.BOOST_DAYS, "4"));

        // then
        assertThat(counter[0]).isEqualTo(5);
        assertThat(result.errors())
                .singleElement()
                .satisfies(error ->
                        assertThat(error.errorCode()).isEqualTo(SettingErrorCode.SETTING_CONFIG_SYNC_CONFLICT));
    }

    @Test
    void run_whenChainedSync_settlesWithinRounds() {
        // given: A kéo B, B kéo C (chuỗi hai bước); luật đọc nguồn từ after nên cần vòng thứ hai
        ConfigRule aToB = (snapshotBefore, after, result) -> {
            if (after.changedFrom(snapshotBefore, ConfigKey.BOOST_DAYS)) {
                result.sync(snapshotBefore, after, ConfigKey.BOOST_COOLDOWN_DAYS, "9", ConfigKey.BOOST_DAYS);
            }
        };
        ConfigRule bToC = (snapshotBefore, after, result) -> {
            if (after.changedFrom(snapshotBefore, ConfigKey.BOOST_COOLDOWN_DAYS)) {
                result.sync(snapshotBefore, after, ConfigKey.BOOST_ROTATE_SECONDS, "99", ConfigKey.BOOST_COOLDOWN_DAYS);
            }
        };
        // luật C đặt trước B để buộc phải qua vòng thứ hai
        ConfigRuleEngine engine = new ConfigRuleEngine(List.of(bToC, aToB), List.of(), List.of());

        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.BOOST_DAYS, "4"));

        // then
        assertThat(result.errors()).isEmpty();
        assertThat(result.changes()).extracting(ConfigChange::key).contains(ConfigKey.BOOST_ROTATE_SECONDS);
    }
}
