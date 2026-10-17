package com.shareloop.setting.controller;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.common.security.CurrentUserId;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.dto.AdminUpdateConfigRequest;
import com.shareloop.setting.dto.ConfigResponse;
import com.shareloop.setting.dto.UpdateConfigRequest;
import com.shareloop.setting.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "admin-config-controller", description = "Admin quản lý cấu hình hệ thống (configs)")
@RestController
@RequestMapping("/api/v1/admin/configs")
public class AdminConfigController {

    private final ConfigService configService;

    public AdminConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @Operation(
            summary = "Danh sách cấu hình hệ thống",
            description = "Lấy danh sách các giá trị cấu hình hệ thống hiện tại cùng giá trị mặc định.")
    @ApiResponse(responseCode = "200", description = "Trả về danh sách cấu hình thành công")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @GetMapping
    public List<ConfigResponse> listConfigs() {
        return configService.listConfigs();
    }

    @Operation(
            summary = "Cập nhật giá trị cấu hình theo key",
            description = "Cập nhật giá trị cấu hình theo key trên đường dẫn, kiểm tra định dạng và ghi log hoạt động.")
    @ApiResponse(responseCode = "200", description = "Cập nhật cấu hình thành công")
    @ApiResponse(responseCode = "400", description = "Giá trị hoặc lý do không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy cấu hình")
    @PutMapping("/{key}")
    public ConfigResponse updateConfig(
            @Parameter(description = "Key của cấu hình") @PathVariable String key,
            @Valid @RequestBody UpdateConfigRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        ConfigKey configKey = resolveKey(key);
        return configService.update(configKey, request.value(), request.reason(), adminId);
    }

    @Operation(
            summary = "Cập nhật giá trị cấu hình theo body",
            description =
                    "Cập nhật giá trị cấu hình bằng key truyền trong body, kiểm tra định dạng và ghi log hoạt động.")
    @ApiResponse(responseCode = "200", description = "Cập nhật cấu hình thành công")
    @ApiResponse(responseCode = "400", description = "Dữ liệu cấu hình không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy cấu hình")
    @PutMapping
    public ConfigResponse updateConfig(
            @Valid @RequestBody AdminUpdateConfigRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        ConfigKey configKey = resolveKey(request.key());
        return configService.update(configKey, request.value(), request.reason(), adminId);
    }

    private ConfigKey resolveKey(String key) {
        return java.util.Arrays.stream(ConfigKey.values())
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElseThrow(() ->
                        new BusinessException(SettingErrorCode.SETTING_CONFIG_NOT_FOUND, "Unknown config: " + key));
    }
}
