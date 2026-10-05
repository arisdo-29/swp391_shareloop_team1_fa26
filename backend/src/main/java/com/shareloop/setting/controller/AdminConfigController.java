package com.shareloop.setting.controller;

import com.shareloop.setting.dto.ConfigResponse;
import com.shareloop.setting.service.ConfigService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
