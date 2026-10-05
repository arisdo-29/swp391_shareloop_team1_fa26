package com.shareloop.setting.service;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.dto.ConfigResponse;
import com.shareloop.setting.repository.WebsiteAttributeRepository;
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

    public ConfigService(WebsiteAttributeRepository repository) {
        this.repository = repository;
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
}
