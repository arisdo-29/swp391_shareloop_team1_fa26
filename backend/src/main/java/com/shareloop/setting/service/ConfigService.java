package com.shareloop.setting.service;

import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.repository.WebsiteAttributeRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** Đọc cấu hình nghiệp vụ từ website_attributes, dùng giá trị mặc định khi chưa được seed. */
@Service
public class ConfigService {

    private final WebsiteAttributeRepository websiteAttributeRepository;

    public ConfigService(WebsiteAttributeRepository websiteAttributeRepository) {
        this.websiteAttributeRepository = websiteAttributeRepository;
    }

    public int getInt(ConfigKey key) {
        Objects.requireNonNull(key, "key must not be null");

        return websiteAttributeRepository
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
