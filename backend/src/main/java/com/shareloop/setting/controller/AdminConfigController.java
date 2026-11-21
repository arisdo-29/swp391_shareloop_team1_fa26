package com.shareloop.setting.controller;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.common.security.CurrentUserId;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.SettingErrorCode;
import com.shareloop.setting.dto.AdminUpdateConfigRequest;
import com.shareloop.setting.dto.BatchUpdateConfigRequest;
import com.shareloop.setting.dto.BatchUpdateConfigResponse;
import com.shareloop.setting.dto.ConfigPreviewResponse;
import com.shareloop.setting.dto.ConfigResponse;
import com.shareloop.setting.dto.PreviewConfigRequest;
import com.shareloop.setting.dto.UpdateConfigRequest;
import com.shareloop.setting.service.ConfigBatchService;
import com.shareloop.setting.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "admin-config-controller", description = "Admin quản lý cấu hình hệ thống (configs)")
@RestController
@RequestMapping("/api/v1/admin/configs")
public class AdminConfigController {

    private final ConfigService configService;
    private final ConfigBatchService configBatchService;

    public AdminConfigController(ConfigService configService, ConfigBatchService configBatchService) {
        this.configService = configService;
        this.configBatchService = configBatchService;
    }

    @Operation(
            summary = "Xem trước thay đổi cấu hình",
            description =
                    "Chạy bộ luật liên khoá trên một thay đổi: trả các cấu hình đồng bộ kèm theo, lỗi và cảnh báo."
                            + " Không ghi gì vào cơ sở dữ liệu.")
    @ApiResponse(responseCode = "200", description = "Trả về kết quả xem trước (kể cả khi có lỗi luật trong errors)")
    @ApiResponse(responseCode = "400", description = "Giá trị sai kiểu, ngoài khoảng hoặc khoá cố định")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy cấu hình")
    @PostMapping("/preview")
    public ConfigPreviewResponse preview(@Valid @RequestBody PreviewConfigRequest request) {
        return configBatchService.preview(request.key(), request.value());
    }

    @Operation(
            summary = "Cập nhật nhiều cấu hình theo lô",
            description =
                    "Lưu các thay đổi cùng cấu hình đồng bộ kèm theo trong một transaction và ghi nhật ký từng khoá.")
    @ApiResponse(responseCode = "200", description = "Lưu thành công, trả mọi cấu hình đã đổi")
    @ApiResponse(responseCode = "400", description = "Có lỗi luật, giá trị không hợp lệ hoặc gửi trùng khoá")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy cấu hình")
    @ApiResponse(responseCode = "409", description = "Có cảnh báo mà chưa xác nhận đã hiểu")
    @PutMapping("/batch")
    public BatchUpdateConfigResponse updateBatch(
            @Valid @RequestBody BatchUpdateConfigRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        boolean acknowledged = Boolean.TRUE.equals(request.acknowledgedWarnings());
        return configBatchService.updateBatch(request.changes(), request.reason(), acknowledged, adminId);
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
    @ApiResponse(
            responseCode = "409",
            description = "Thay đổi kéo theo cấu hình liên quan hoặc có cảnh báo, kèm bản xem trước, chưa lưu gì")
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
