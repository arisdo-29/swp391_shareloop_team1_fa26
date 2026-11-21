package com.shareloop.setting.rules;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.SettingErrorCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Kết quả một lần chạy engine: các khoá bị đổi, lỗi và cảnh báo. Luật đồng bộ ghi giá trị qua {@link #sync} để mọi
 * kiểm tra an toàn (khoá Admin đã gửi, khoá cố định, khoảng min max, xung đột) nằm ở một chỗ.
 */
public class RuleResult {

    // Khoá Admin chủ động gửi lên: luật đồng bộ không được ghi đè.
    private final Set<ConfigKey> requestedKeys;
    // LinkedHashMap để giữ thứ tự: khoá Admin gửi trước, khoá đồng bộ sau.
    private final Map<ConfigKey, ConfigChange> changes = new LinkedHashMap<>();
    private final List<RuleMessage> errors = new ArrayList<>();
    private final List<RuleMessage> warnings = new ArrayList<>();

    // Hàm tra khoảng giá trị được truyền vào để test dựng được khoá có max (ConfigKey thật hiện chưa có khoá nào).
    private final Function<ConfigKey, ValueRange> rangeLookup;

    public RuleResult(Set<ConfigKey> requestedKeys) {
        this(requestedKeys, ValueRange::of);
    }

    public RuleResult(Set<ConfigKey> requestedKeys, Function<ConfigKey, ValueRange> rangeLookup) {
        this.requestedKeys = requestedKeys;
        this.rangeLookup = rangeLookup;
    }

    public List<ConfigChange> changes() {
        return List.copyOf(changes.values());
    }

    public List<RuleMessage> errors() {
        return List.copyOf(errors);
    }

    public List<RuleMessage> warnings() {
        return List.copyOf(warnings);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public void addChange(ConfigChange change) {
        changes.put(change.key(), change);
    }

    // Luật đồng bộ chạy lại ở vòng sau nên có thể báo cùng một lỗi lần nữa; bỏ bản trùng để Admin chỉ thấy một dòng.
    public void addError(RuleMessage error) {
        if (!errors.contains(error)) {
            errors.add(error);
        }
    }

    public void addWarning(RuleMessage warning) {
        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    /**
     * Đồng bộ khoá target theo khoá nguồn source. Không tự sửa mà báo lỗi khi: khoá cố định, giá trị vượt min max của
     * khoá đích, hoặc một luật khác đã đồng bộ khoá này sang giá trị khác. Khoá Admin đã gửi thì giữ nguyên.
     */
    public void sync(ConfigSnapshot before, ConfigSnapshot after, ConfigKey target, String newValue, ConfigKey source) {
        if (requestedKeys.contains(target) || newValue.equals(after.get(target))) {
            return;
        }
        if (!target.adminEditable()) {
            addError(RuleMessage.error(
                    "%s là cấu hình cố định nên không tự đồng bộ theo %s được. Giá trị đề xuất: %s."
                            .formatted(target.label(), source.label(), newValue),
                    SettingErrorCode.SETTING_CONFIG_RULE_VIOLATION,
                    target,
                    source));
            return;
        }
        if (isOutOfRange(target, newValue)) {
            addError(RuleMessage.error(
                    "Giá trị đồng bộ đề xuất %s cho %s nằm ngoài khoảng cho phép.".formatted(newValue, target.label()),
                    SettingErrorCode.SETTING_CONFIG_OUT_OF_RANGE,
                    target,
                    source));
            return;
        }
        if (changes.containsKey(target)) {
            addError(RuleMessage.error(
                    "%s bị hai cấu hình liên quan đòi hai giá trị khác nhau (%s và %s)."
                            .formatted(target.label(), changes.get(target).newValue(), newValue),
                    SettingErrorCode.SETTING_CONFIG_SYNC_CONFLICT,
                    target,
                    source));
            return;
        }
        after.set(target, newValue);
        changes.put(
                target,
                new ConfigChange(target, before.get(target), newValue, source, "Đồng bộ theo " + source.label()));
    }

    private boolean isOutOfRange(ConfigKey key, String value) {
        return !rangeLookup.apply(key).contains(new BigDecimal(value));
    }
}
