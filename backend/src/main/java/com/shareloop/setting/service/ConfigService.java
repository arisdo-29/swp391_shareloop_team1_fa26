package com.shareloop.setting.service;

import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.dto.ConfigResponse;
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
                .map(key -> new ConfigResponse(
                        key.key(), configuredValues.getOrDefault(key.key(), key.defaultValue()), key.defaultValue()))
                .toList();
    }

    public String get(ConfigKey key) {
        return repository
                .findFirstByAttrGroupAndAttrKeyAndIsActiveTrue("CONFIG", key.key())
                .map(attribute -> attribute.getAttrValue())
                .orElse(key.defaultValue());
    }

    public int getInt(ConfigKey key) {
        Objects.requireNonNull(key, "key must not be null");

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

    @Transactional
    public ConfigResponse update(ConfigKey key, String value, String reason, Long adminId) {
        validateValue(key, value);
        var attribute = repository
                .findFirstByAttrGroupAndAttrKeyAndIsActiveTrue("CONFIG", key.key())
                .orElseThrow(() -> new BusinessException(SettingErrorCode.CONFIG_KEY_NOT_FOUND));
        String previousValue = attribute.getAttrValue();
        attribute.updateValue(value);
        repository.save(attribute);
        activityLogService.log(
                adminId,
                "CONFIG_UPDATED",
                "CONFIG",
                null,
                Map.of("key", key.key(), "oldValue", previousValue, "newValue", value),
                reason);
        return new ConfigResponse(key.key(), value, key.defaultValue());
    }

    private void validateValue(ConfigKey key, String value) {
        try {
            if (key == ConfigKey.CHAT_AI_THRESHOLD) {
                BigDecimal threshold = new BigDecimal(value);
                if (threshold.compareTo(BigDecimal.ZERO) < 0 || threshold.compareTo(BigDecimal.ONE) > 0) {
                    throw new NumberFormatException();
                }
                return;
            }

            int number = Integer.parseInt(value);
            if (key == ConfigKey.MAX_ACTIVE_ITEMS_DIAMOND && number == -1) {
                return;
            }
            if (number < 0 || (number == 0 && !allowsZero(key))) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            String expectedFormat =
                    switch (key) {
                        case CHAT_AI_THRESHOLD -> "nằm trong khoảng từ 0 đến 1";
                        case MAX_ACTIVE_ITEMS_DIAMOND -> "là số nguyên dương hoặc bằng -1";
                        default -> allowsZero(key) ? "là số nguyên không âm" : "là số nguyên dương";
                    };
            throw new BusinessException(
                    SettingErrorCode.CONFIG_VALUE_INVALID,
                    "Giá trị cấu hình '%s' phải %s.".formatted(key.key(), expectedFormat),
                    "value",
                    Map.of("key", key.key()));
        }
    }

    private boolean allowsZero(ConfigKey key) {
        return switch (key) {
            case POST_FEE,
                    EDIT_FEE,
                    RENEW_FEE,
                    BOOST_FEE,
                    AI_SEARCH_FEE,
                    AI_FREE_PER_DAY,
                    AI_TRIAL_USES,
                    ITEM_AI_RETRIES,
                    BOOST_MAX_PER_PAGE -> true;
            default -> false;
        };
    }
}
