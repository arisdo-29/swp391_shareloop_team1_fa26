package com.shareloop.setting.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.dto.ConfigResponse;
import com.shareloop.setting.dto.UpdateConfigRequest;
import com.shareloop.setting.service.ConfigService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/configs")
public class AdminConfigController {

    private final ConfigService configService;

    public AdminConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping
    public List<ConfigResponse> listConfigs() {
        return configService.listConfigs();
    }

    @PutMapping("/{key}")
    public ConfigResponse updateConfig(
            @PathVariable String key, @Valid @RequestBody UpdateConfigRequest request, @CurrentUserId Long adminId) {
        ConfigKey configKey = java.util.Arrays.stream(ConfigKey.values())
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown configuration key: " + key));
        return configService.update(configKey, request.value(), request.reason(), adminId);
    }
}
