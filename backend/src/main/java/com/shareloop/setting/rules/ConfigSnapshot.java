package com.shareloop.setting.rules;

import com.shareloop.setting.ConfigKey;
import java.util.EnumMap;
import java.util.Map;

/**
 * Ảnh chụp giá trị của mọi khoá cấu hình tại một thời điểm. Các luật chỉ đọc và ghi trên ảnh chụp này, không gọi DB,
 * nên chạy thử (xem trước) không bao giờ lưu nhầm gì.
 */
public class ConfigSnapshot {

    private final Map<ConfigKey, String> values;

    private ConfigSnapshot(Map<ConfigKey, String> values) {
        this.values = values;
    }

    /** Ảnh chụp toàn giá trị mặc định của ConfigKey. */
    public static ConfigSnapshot defaults() {
        return of(Map.of());
    }

    /** Khoá nào không có trong overrides thì lấy giá trị mặc định, giống cách ConfigService.get xử lý khi thiếu dòng. */
    public static ConfigSnapshot of(Map<ConfigKey, String> overrides) {
        Map<ConfigKey, String> values = new EnumMap<>(ConfigKey.class);
        for (ConfigKey key : ConfigKey.values()) {
            values.put(key, overrides.getOrDefault(key, key.defaultValue()));
        }
        return new ConfigSnapshot(values);
    }

    public ConfigSnapshot copy() {
        return new ConfigSnapshot(new EnumMap<>(values));
    }

    /** Bản sao đã đổi một khoá; tiện cho test. */
    public ConfigSnapshot with(ConfigKey key, String value) {
        ConfigSnapshot copy = copy();
        copy.set(key, value);
        return copy;
    }

    public String get(ConfigKey key) {
        return values.get(key);
    }

    /** Trả về null khi giá trị không phải số nguyên, để luật bỏ qua và validateValue báo lỗi kiểu. */
    public Integer getInt(ConfigKey key) {
        try {
            return Integer.valueOf(values.get(key).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public void set(ConfigKey key, String value) {
        values.put(key, value);
    }

    /** True khi có ít nhất một trong các khoá khác giá trị so với ảnh chụp other. */
    public boolean changedFrom(ConfigSnapshot other, ConfigKey... keys) {
        for (ConfigKey key : keys) {
            if (!values.get(key).equals(other.values.get(key))) {
                return true;
            }
        }
        return false;
    }

    /** Bản sao dạng Map để engine so sánh trước và sau mỗi vòng. */
    public Map<ConfigKey, String> asMap() {
        return new EnumMap<>(values);
    }
}
