package com.shareloop.setting.rules;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.SettingErrorCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Chạy bộ luật liên khoá trên một lần thay đổi: áp dụng giá trị Admin gửi, lặp luật đồng bộ cho tới khi ổn định, rồi
 * chạy luật lỗi và cảnh báo trên kết quả cuối. Engine không gọi DB và không giữ trạng thái nên chạy lại cùng đầu vào
 * luôn ra cùng kết quả. Việc kiểm kiểu và min max cho khoá Admin gửi vẫn do ConfigService.validateValue lo, còn khoá
 * đồng bộ được kiểm min max ngay trong {@link RuleResult#sync}.
 */
@Component
public class ConfigRuleEngine {

    // Luật đồng bộ chuỗi (A kéo B kéo C) thường ổn định sau 2 vòng; quá 5 vòng nghĩa là các luật đang đẩy qua đẩy lại.
    private static final int MAX_SYNC_ROUNDS = 5;

    private final List<ConfigRule> syncRules;
    private final List<ConfigRule> errorRules;
    private final List<ConfigRule> warningRules;
    private final Function<ConfigKey, ValueRange> rangeLookup;

    // Spring dùng constructor không tham số này, constructor bên dưới để test thay bộ luật.
    public ConfigRuleEngine() {
        this(SyncRules.all(), ErrorRules.all(), WarningRules.all());
    }

    public ConfigRuleEngine(List<ConfigRule> syncRules, List<ConfigRule> errorRules, List<ConfigRule> warningRules) {
        this(syncRules, errorRules, warningRules, ValueRange::of);
    }

    public ConfigRuleEngine(
            List<ConfigRule> syncRules,
            List<ConfigRule> errorRules,
            List<ConfigRule> warningRules,
            Function<ConfigKey, ValueRange> rangeLookup) {
        this.syncRules = syncRules;
        this.errorRules = errorRules;
        this.warningRules = warningRules;
        this.rangeLookup = rangeLookup;
    }

    /**
     * @param before giá trị đang lưu, không bị sửa
     * @param requested các khoá Admin gửi lên và giá trị mới của chúng
     */
    public RuleResult run(ConfigSnapshot before, Map<ConfigKey, String> requested) {
        ConfigSnapshot after = before.copy();
        RuleResult result = new RuleResult(requested.keySet(), rangeLookup);
        applyRequested(before, after, requested, result);
        runSyncRounds(before, after, result);
        runRules(errorRules, before, after, result);
        runRules(warningRules, before, after, result);
        return result;
    }

    private void applyRequested(
            ConfigSnapshot before, ConfigSnapshot after, Map<ConfigKey, String> requested, RuleResult result) {
        for (Map.Entry<ConfigKey, String> entry : requested.entrySet()) {
            ConfigKey key = entry.getKey();
            String newValue = entry.getValue().trim();
            after.set(key, newValue);
            if (!newValue.equals(before.get(key))) {
                result.addChange(new ConfigChange(key, before.get(key), newValue, null, null));
            }
        }
    }

    private void runSyncRounds(ConfigSnapshot before, ConfigSnapshot after, RuleResult result) {
        for (int round = 0; round < MAX_SYNC_ROUNDS; round++) {
            Map<ConfigKey, String> startOfRound = after.asMap();
            runRules(syncRules, before, after, result);
            if (after.asMap().equals(startOfRound)) {
                return;
            }
        }
        result.addError(RuleMessage.error(
                "Các cấu hình liên quan đồng bộ qua lại hơn %d vòng mà vẫn chưa ổn định.".formatted(MAX_SYNC_ROUNDS),
                SettingErrorCode.SETTING_CONFIG_SYNC_CONFLICT));
    }

    private void runRules(List<ConfigRule> rules, ConfigSnapshot before, ConfigSnapshot after, RuleResult result) {
        for (ConfigRule rule : rules) {
            rule.apply(before, after, result);
        }
    }
}
