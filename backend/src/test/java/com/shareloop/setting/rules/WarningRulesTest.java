package com.shareloop.setting.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.setting.ConfigKey;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WarningRulesTest {

    private final ConfigRuleEngine engine = new ConfigRuleEngine();
    private final ConfigSnapshot before = ConfigSnapshot.defaults();

    @Test
    void boostNotice_whenAsLongAsOnePackage_returnsWarning() {
        // given: gói mặc định 3 ngày = 72 giờ
        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.BOOST_ENDING_NOTICE_HOURS, "72"));

        // then
        assertThat(result.errors()).isEmpty();
        assertThat(result.warnings())
                .singleElement()
                .satisfies(warning -> assertThat(warning.keys())
                        .containsExactly(ConfigKey.BOOST_ENDING_NOTICE_HOURS, ConfigKey.BOOST_DAYS));
    }

    @Test
    void boostNotice_whenShorterThanOnePackage_hasNoWarning() {
        // given
        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.BOOST_ENDING_NOTICE_HOURS, "48"));

        // then
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void goldDisplay_whenShorterThanBase_returnsWarning() {
        // given: mặc định 30 ngày
        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.POST_DISPLAY_DAYS_GOLD, "20"));

        // then
        assertThat(result.warnings())
                .singleElement()
                .satisfies(warning -> assertThat(warning.keys())
                        .containsExactly(ConfigKey.POST_DISPLAY_DAYS_GOLD, ConfigKey.POST_DISPLAY_DAYS));
    }

    @Test
    void fee_whenSetToZero_returnsWarningForThatFee() {
        // given
        // when
        RuleResult result = engine.run(before, Map.of(ConfigKey.BOOST_FEE, "0"));

        // then
        assertThat(result.warnings())
                .singleElement()
                .satisfies(warning -> assertThat(warning.keys()).containsExactly(ConfigKey.BOOST_FEE));
    }

    @Test
    void fee_whenUnrelatedKeyChanges_doesNotWarnAboutExistingZeroFee() {
        // given: phí đẩy đang là 0 từ trước
        ConfigSnapshot freeBoost = before.with(ConfigKey.BOOST_FEE, "0");

        // when
        RuleResult result = engine.run(freeBoost, Map.of(ConfigKey.IMAGE_MAX_MB, "6"));

        // then
        assertThat(result.warnings()).isEmpty();
    }
}
