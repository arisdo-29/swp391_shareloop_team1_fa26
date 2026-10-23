package com.shareloop.setting;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Kiểm tra dữ liệu mô tả của ConfigKey vì trang Admin hiển thị nguyên văn các chuỗi này. */
class ConfigKeyTest {

    @Test
    void everyKey_hasLabelDescriptionAndGroup() {
        for (ConfigKey key : ConfigKey.values()) {
            assertThat(key.label()).as("label của %s", key.key()).isNotBlank();
            assertThat(key.description()).as("description của %s", key.key()).isNotBlank();
            assertThat(key.group()).as("group của %s", key.key()).isNotNull();
        }
    }

    @Test
    void labelsAndDescriptions_haveNoSemicolonBecauseAdminSeesThemAsText() {
        for (ConfigKey key : ConfigKey.values()) {
            assertThat(key.label()).as("label của %s", key.key()).doesNotContain(";");
            assertThat(key.description()).as("description của %s", key.key()).doesNotContain(";");
        }
    }

    @Test
    void labels_areUniqueSoAdminCanTellKeysApart() {
        Set<String> labels = new HashSet<>();
        for (ConfigKey key : ConfigKey.values()) {
            assertThat(labels.add(key.label()))
                    .as("label trùng: %s", key.label())
                    .isTrue();
        }
    }

    @Test
    void reputationAndActiveItemKeys_areGroupedForTheReputationPage() {
        assertThat(ConfigKey.MAX_ACTIVE_ITEMS_BRONZE.group()).isEqualTo(ConfigGroup.REPUTATION);
        assertThat(ConfigKey.MAX_ACTIVE_ITEMS_DIAMOND.group()).isEqualTo(ConfigGroup.REPUTATION);
        assertThat(ConfigKey.POST_DISPLAY_DAYS_GOLD.group()).isEqualTo(ConfigGroup.REPUTATION);
        assertThat(ConfigKey.DAILY_POINTS_CAP.group()).isEqualTo(ConfigGroup.REPUTATION);
        assertThat(ConfigKey.REPUTATION_SILVER_MIN.group()).isEqualTo(ConfigGroup.REPUTATION);
        assertThat(ConfigKey.REPUTATION_PAIR_ALERT_THRESHOLD.group()).isEqualTo(ConfigGroup.REPUTATION);
    }
}
