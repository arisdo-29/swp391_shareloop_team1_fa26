package com.shareloop.setting.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.ConfigKey.ValueType;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.dto.ChecklistItem;
import com.shareloop.setting.dto.ConfigPreviewResponse;
import com.shareloop.setting.dto.ConfigResponse;
import com.shareloop.setting.dto.LexiconEntry;
import com.shareloop.setting.entity.WebsiteAttribute;
import com.shareloop.setting.mapper.ConfigPreviewMapper;
import com.shareloop.setting.repository.WebsiteAttributeRepository;
import com.shareloop.setting.rules.ConfigChange;
import com.shareloop.setting.rules.ConfigRuleEngine;
import com.shareloop.setting.rules.ConfigSnapshot;
import com.shareloop.setting.rules.RuleMessage;
import com.shareloop.setting.rules.RuleResult;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConfigService {

    private static final String CONFIG_GROUP = "CONFIG";

    private final WebsiteAttributeRepository repository;
    private final ActivityLogService activityLogService;
    private final ConfigRuleEngine ruleEngine;
    private final ConfigPreviewMapper previewMapper;

    public ConfigService(
            WebsiteAttributeRepository repository,
            ActivityLogService activityLogService,
            ConfigRuleEngine ruleEngine,
            ConfigPreviewMapper previewMapper) {
        this.repository = repository;
        this.activityLogService = activityLogService;
        this.ruleEngine = ruleEngine;
        this.previewMapper = previewMapper;
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
    public ConfigResponse toResponse(ConfigKey key, String value) {
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
                .map(attribute -> new ChecklistItem(
                        attribute.getId(),
                        attribute.getAttrKey(),
                        attribute.getAttrValue(),
                        stringValue(attribute.getExtra(), "description")))
                .toList();
    }

    /** Active review reject reason templates in their configured order. */
    public List<String> rejectReasons() {
        return repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc("REJECT_REASON").stream()
                .map(attribute -> attribute.getAttrValue())
                .toList();
    }

    private static String stringValue(Map<String, Object> values, String key) {
        Object value = values == null ? null : values.get(key);
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

    /**
     * Cập nhật một khoá qua bộ luật liên khoá. Không kéo theo khoá nào và không có cảnh báo thì lưu luôn và ghi log
     * (luật thứ tự điểm hạng UT-06 và các luật khác chạy trong engine). Nếu kéo theo khoá khác hoặc có cảnh báo thì không
     * lưu gì mà trả 409 kèm bản xem trước để giao diện chuyển sang cập nhật theo lô.
     *
     * @throws BusinessException lỗi kiểu, khoảng giá trị, khoá cố định, lỗi luật (400) hoặc SETTING_CONFIG_SYNC_REQUIRED
     *     (409)
     */
    // @Transactional: khoá dòng, kiểm tra và lưu nằm chung một transaction, lỗi giữa chừng thì không lưu gì.
    @Transactional
    public ConfigResponse update(ConfigKey key, String value, String reason, Long adminId) {
        Objects.requireNonNull(key, "key must not be null");
        String requestedValue = value == null ? "" : value;
        Map<ConfigKey, String> requested = Map.of(key, requestedValue);
        validateRequested(requested);
        lockConfigRows();
        RuleResult result = evaluate(requested);
        requireNoErrors(result);
        if (hasSideEffects(result)) {
            throw new BusinessException(
                    SettingErrorCode.SETTING_CONFIG_SYNC_REQUIRED,
                    null,
                    null,
                    Map.of("preview", previewMapper.toResponse(result)));
        }
        saveChanges(result.changes(), reason, adminId);
        return toResponse(key, requestedValue.trim());
    }

    // Có khoá đồng bộ kèm theo (causedByKey khác null) hoặc có cảnh báo thì Admin cần xem trước chứ không lưu thẳng.
    private boolean hasSideEffects(RuleResult result) {
        boolean hasSyncedChange = result.changes().stream().anyMatch(change -> change.causedByKey() != null);
        return hasSyncedChange || result.hasWarnings();
    }

    /**
     * Khoá toàn bộ dòng cấu hình (SELECT ... FOR UPDATE) cho tới hết transaction, để hai lần cập nhật không chạy song
     * song trên cùng một ảnh chụp giá trị cũ. Chỉ gọi được khi đã có transaction ghi.
     */
    // Propagation.MANDATORY: gọi ngoài transaction thì báo lỗi ngay, tránh khoá xong lại nhả liền mà không ai hay.
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockConfigRows() {
        repository.findByAttrGroupAndIsActiveTrueOrderByAttrKeyAsc(CONFIG_GROUP);
    }

    /** Ảnh chụp giá trị hiện hành của mọi khoá. Khoá chưa có dòng trong DB thì lấy giá trị mặc định. */
    public ConfigSnapshot loadSnapshot() {
        Map<ConfigKey, String> values = new EnumMap<>(ConfigKey.class);
        for (WebsiteAttribute attribute :
                repository.findByAttrGroupAndIsActiveTrueOrderBySortOrderAscIdAsc(CONFIG_GROUP)) {
            ConfigKey key = findKey(attribute.getAttrKey());
            if (key != null) {
                values.putIfAbsent(key, attribute.getAttrValue());
            }
        }
        return ConfigSnapshot.of(values);
    }

    /**
     * Kiểm kiểu và khoảng giá trị cho từng khoá Admin gửi, rồi chạy bộ luật trên ảnh chụp hiện tại. Không ghi gì.
     *
     * @throws BusinessException khoá cố định (SETTING_CONFIG_NOT_EDITABLE) hoặc giá trị sai kiểu hay ngoài khoảng
     */
    public RuleResult evaluate(Map<ConfigKey, String> requested) {
        validateRequested(requested);
        return ruleEngine.run(loadSnapshot(), requested);
    }

    /**
     * Kiểm đầu vào của từng khoá Admin gửi (khoá cố định, kiểu, khoảng giá trị). Gọi trước khi khoá dòng để đầu vào sai
     * bị từ chối ngay mà không giữ khoá làm người khác phải chờ.
     */
    public void validateRequested(Map<ConfigKey, String> requested) {
        for (Map.Entry<ConfigKey, String> entry : requested.entrySet()) {
            if (!entry.getKey().adminEditable()) {
                throw new BusinessException(SettingErrorCode.SETTING_CONFIG_NOT_EDITABLE);
            }
            validateValue(entry.getKey(), entry.getValue());
        }
    }

    /** Xem trước kết quả của một thay đổi, không ghi DB. */
    public ConfigPreviewResponse preview(Map<ConfigKey, String> requested) {
        return previewMapper.toResponse(evaluate(requested));
    }

    /**
     * Nếu có lỗi thì ném lỗi đầu tiên (mã và câu chữ của lỗi đó), kèm danh sách mọi lỗi trong details để giao diện
     * hiện đủ.
     */
    public void requireNoErrors(RuleResult result) {
        if (!result.hasErrors()) {
            return;
        }
        RuleMessage first = result.errors().get(0);
        throw new BusinessException(
                first.errorCode(), first.message(), null, Map.of("errors", previewMapper.toMessages(result.errors())));
    }

    /**
     * Lưu từng khoá đã đổi và ghi một dòng nhật ký cho mỗi khoá, dùng chung một lý do. Khoá đồng bộ ghi thêm
     * syncedFrom và note. Một khoá lỗi thì ném ngay và transaction rollback toàn bộ.
     *
     * @throws BusinessException SETTING_CONFIG_NOT_FOUND khi một khoá chưa có dòng trong DB
     */
    @Transactional
    public void saveChanges(List<ConfigChange> changes, String reason, Long adminId) {
        for (ConfigChange change : changes) {
            saveChange(change, reason, adminId);
        }
    }

    private void saveChange(ConfigChange change, String reason, Long adminId) {
        var attribute = repository
                .findFirstByAttrGroupAndAttrKeyAndIsActiveTrue(
                        CONFIG_GROUP, change.key().key())
                .orElseThrow(() -> new BusinessException(
                        SettingErrorCode.SETTING_CONFIG_NOT_FOUND,
                        "Missing config: " + change.key().key()));
        attribute.updateValue(change.newValue());
        repository.save(attribute);
        activityLogService.log(ActivityType.CONFIG_UPDATE.name(), adminId, CONFIG_GROUP, null, logData(change), reason);
    }

    private Map<String, Object> logData(ConfigChange change) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("key", change.key().key());
        data.put("oldValue", change.oldValue());
        data.put("newValue", change.newValue());
        if (change.causedByKey() != null) {
            data.put("syncedFrom", change.causedByKey().key());
            data.put("note", change.note());
        }
        return data;
    }

    /**
     * Tìm khoá theo tên khoá kỹ thuật.
     *
     * @throws BusinessException SETTING_CONFIG_NOT_FOUND khi không có khoá nào tên như vậy
     */
    public ConfigKey resolveKey(String key) {
        ConfigKey configKey = findKey(key);
        if (configKey == null) {
            throw new BusinessException(SettingErrorCode.SETTING_CONFIG_NOT_FOUND, "Unknown config: " + key);
        }
        return configKey;
    }

    private static ConfigKey findKey(String key) {
        for (ConfigKey configKey : ConfigKey.values()) {
            if (configKey.key().equals(key)) {
                return configKey;
            }
        }
        return null;
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
                case CODE_LIST -> {
                    // Ít nhất một mã, mọi mã đều phải hợp lệ: gõ sai một mã thì chặn ngay thay vì lặng lẽ bị bỏ qua
                    List<String> codes = splitCodes(rawValue);
                    if (codes.isEmpty() || !key.allowedCodes().containsAll(codes)) {
                        throw invalidType(key.key(), typeName(key.valueType()) + " " + key.allowedCodes(), value);
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
            case CODE_LIST -> "a comma-separated list of";
        };
    }

    /**
     * Danh sách mã của khoá kiểu CODE_LIST (L13), ví dụ report_serious_reasons. Bỏ khoảng trắng và mã rỗng. Chưa có dòng
     * trong DB thì dùng giá trị mặc định của khoá.
     */
    public List<String> getCodes(ConfigKey key) {
        Objects.requireNonNull(key, "key must not be null");
        if (key.valueType() != ValueType.CODE_LIST) {
            throw invalidType(key.key(), "a code list", get(key));
        }
        return splitCodes(get(key));
    }

    private static List<String> splitCodes(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .toList();
    }
}
