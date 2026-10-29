package com.shareloop.setting.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.ConfigKey.ValueType;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.dto.ChecklistItem;
import com.shareloop.setting.dto.ConfigResponse;
import com.shareloop.setting.dto.LexiconEntry;
import com.shareloop.setting.repository.WebsiteAttributeRepository;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConfigService {

    private final WebsiteAttributeRepository repository;
    private final ActivityLogService activityLogService;

    public ConfigService(WebsiteAttributeRepository repository, ActivityLogService activityLogService) {
        this.repository = repository;
        this.activityLogService = activityLogService;
    }

    public List<ConfigResponse> listConfigs() {
        Map<String, String> configuredValues =
                repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc("CONFIG").stream()
                        .collect(Collectors.toMap(
                                attribute -> attribute.getAttrKey(),
                                attribute -> attribute.getAttrValue(),
                                (first, ignored) -> first));

        return Arrays.stream(ConfigKey.values())
                .filter(ConfigKey::adminEditable)
                .map(key -> toResponse(key, configuredValues.getOrDefault(key.key(), key.defaultValue())))
                .toList();
    }

    // Tên, mô tả và nhóm lấy từ chính ConfigKey nên không cần thêm cột trong DB.
    private ConfigResponse toResponse(ConfigKey key, String value) {
        return new ConfigResponse(
                key.key(),
                value,
                key.defaultValue(),
                key.label(),
                key.description(),
                key.group().name(),
                key.group().label());
    }

    public String get(ConfigKey key) {
        return repository
                .findFirstByAttrGroupAndAttrKeyAndIsActiveTrue("CONFIG", key.key())
                .map(attribute -> attribute.getAttrValue())
                .orElse(key.defaultValue());
    }

    public int getInt(ConfigKey key) {
        Objects.requireNonNull(key, "key must not be null");
        if (key.valueType() != ValueType.INTEGER) {
            throw invalidType(key.key(), "integer", get(key));
        }

        return repository
                .findConfigValueByAttrKey(key.getAttrKey())
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException exception) {
                        throw new IllegalStateException(
                                "Configuration value for key '%s' must be an integer: '%s'"
                                        .formatted(key.getAttrKey(), value),
                                exception);
                    }
                })
                .orElse(key.getDefaultValue());
    }

    public String getRaw(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(SettingErrorCode.SETTING_CONFIG_NOT_FOUND, "Missing config: " + key);
        }
        return repository
                .findConfigValueByAttrKey(key)
                .orElseThrow(() ->
                        new BusinessException(SettingErrorCode.SETTING_CONFIG_NOT_FOUND, "Missing config: " + key));
    }

    public BigDecimal getDecimal(ConfigKey key) {
        Objects.requireNonNull(key, "key must not be null");
        if (key.valueType() != ValueType.DECIMAL) {
            throw invalidType(key.key(), "decimal", get(key));
        }
        return getDecimal(key.key());
    }

    public BigDecimal getDecimal(String key) {
        String value = getRaw(key);
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw invalidType(key, "decimal", value);
        }
    }

    public boolean getBoolean(ConfigKey key) {
        Objects.requireNonNull(key, "key must not be null");
        if (key.valueType() != ValueType.BOOLEAN) {
            throw invalidType(key.key(), "boolean", get(key));
        }
        return getBoolean(key.key());
    }

    public boolean getBoolean(String key) {
        String value = getRaw(key).trim();
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw invalidType(key, "boolean", value);
        }
        return Boolean.parseBoolean(value);
    }

    /** Active moderation rules in their configured order. */
    public List<LexiconEntry> lexicon() {
        return repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc("BANNED_KEYWORD").stream()
                .map(attribute -> {
                    Map<String, Object> extra = attribute.getExtra();
                    return new LexiconEntry(
                            attribute.getAttrValue(),
                            stringValue(extra, "category"),
                            stringValue(extra, "severity"),
                            stringValue(extra, "matchMode"),
                            stringList(extra.get("exceptions")),
                            stringValue(extra, "scope"),
                            attribute.getSortOrder());
                })
                .toList();
    }

    /** Các mục checklist duyệt bài đang dùng (kèm id, key) theo sort_order rồi id; dùng để ghép đáp án theo id. */
    public List<ChecklistItem> checklistItems() {
        return repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc("CHECKLIST").stream()
                .map(attribute ->
                        new ChecklistItem(attribute.getId(), attribute.getAttrKey(), attribute.getAttrValue()))
                .toList();
    }

    /** Active review reject reason templates in their configured order. */
    public List<String> rejectReasons() {
        return repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc("REJECT_REASON").stream()
                .map(attribute -> attribute.getAttrValue())
                .toList();
    }

    private static String stringValue(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof String string ? string : null;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    @Transactional
    public ConfigResponse update(ConfigKey key, String value, String reason, Long adminId) {
        if (key != null && !key.adminEditable()) {
            throw new BusinessException(SettingErrorCode.SETTING_CONFIG_NOT_EDITABLE);
        }
        validateValue(key, value);
        value = value.trim();
        validateRankThresholds(key, value);
        var attribute = repository
                .findFirstByAttrGroupAndAttrKeyAndIsActiveTrue("CONFIG", key.key())
                .orElseThrow(() -> new BusinessException(
                        SettingErrorCode.SETTING_CONFIG_NOT_FOUND, "Missing config: " + key.key()));
        String previousValue = attribute.getAttrValue();
        attribute.updateValue(value);
        repository.save(attribute);
        activityLogService.log(
                ActivityType.CONFIG_UPDATE.name(),
                adminId,
                "CONFIG",
                null,
                Map.of("key", key.key(), "oldValue", previousValue, "newValue", value),
                reason);
        return toResponse(key, value);
    }

    /**
     * Điểm tối thiểu của ba hạng phải tăng dần: Bạc nhỏ hơn Vàng, Vàng nhỏ hơn Kim cương (BR-U01). Nếu không, một người
     * có thể được xếp hạng cao hơn trong khi điểm lại thấp hơn. Khoá khác thì bỏ qua.
     *
     * @throws BusinessException SETTING_RANK_THRESHOLD_ORDER khi giá trị mới làm thứ tự bị đảo hoặc bằng nhau
     */
    private void validateRankThresholds(ConfigKey changedKey, String newValue) {
        boolean isRankThreshold = changedKey == ConfigKey.REPUTATION_SILVER_MIN
                || changedKey == ConfigKey.REPUTATION_GOLD_MIN
                || changedKey == ConfigKey.REPUTATION_DIAMOND_MIN;
        if (!isRankThreshold) {
            return;
        }

        int silverMin = thresholdAfterChange(ConfigKey.REPUTATION_SILVER_MIN, changedKey, newValue);
        int goldMin = thresholdAfterChange(ConfigKey.REPUTATION_GOLD_MIN, changedKey, newValue);
        int diamondMin = thresholdAfterChange(ConfigKey.REPUTATION_DIAMOND_MIN, changedKey, newValue);
        if (silverMin >= goldMin || goldMin >= diamondMin) {
            throw new BusinessException(SettingErrorCode.SETTING_RANK_THRESHOLD_ORDER);
        }
    }

    // Khoá đang được sửa lấy giá trị mới, hai khoá còn lại lấy giá trị đang lưu.
    private int thresholdAfterChange(ConfigKey thresholdKey, ConfigKey changedKey, String newValue) {
        String value = thresholdKey == changedKey ? newValue : get(thresholdKey);
        return Integer.parseInt(value.trim());
    }

    public void validateValue(ConfigKey key, String value) {
        Objects.requireNonNull(key, "key must not be null");
        String rawValue = value == null ? "" : value.trim();
        try {
            switch (key.valueType()) {
                case INTEGER -> Integer.parseInt(rawValue);
                case DECIMAL -> new BigDecimal(rawValue);
                case BOOLEAN -> {
                    if (!"true".equalsIgnoreCase(rawValue) && !"false".equalsIgnoreCase(rawValue)) {
                        throw invalidType(key.key(), "boolean", value);
                    }
                }
            }
        } catch (NumberFormatException exception) {
            throw invalidType(key.key(), typeName(key.valueType()), value);
        }

        if (key.minValue() != null || key.maxValue() != null) {
            BigDecimal numericValue;
            try {
                numericValue = new BigDecimal(rawValue);
            } catch (NumberFormatException exception) {
                throw invalidType(key.key(), typeName(key.valueType()), value);
            }
            if ((key.minValue() != null && numericValue.compareTo(key.minValue()) < 0)
                    || (key.maxValue() != null && numericValue.compareTo(key.maxValue()) > 0)) {
                String range = "%s..%s".formatted(key.minValue(), key.maxValue());
                throw new BusinessException(
                        SettingErrorCode.SETTING_CONFIG_OUT_OF_RANGE,
                        "Configuration '%s' must be within %s: '%s'".formatted(key.key(), range, value));
            }
        }
    }

    private static BusinessException invalidType(String key, String expected, String value) {
        String message = "Configuration '%s' must be %s: '%s'".formatted(key, expected, value);
        return new BusinessException(SettingErrorCode.SETTING_CONFIG_INVALID_TYPE, message);
    }

    private static String typeName(ValueType type) {
        return switch (type) {
            case INTEGER -> "an integer";
            case DECIMAL -> "a decimal";
            case BOOLEAN -> "a boolean";
        };
    }
}
