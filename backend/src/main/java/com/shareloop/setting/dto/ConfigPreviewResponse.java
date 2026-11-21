package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Kết quả xem trước một thay đổi cấu hình")
public record ConfigPreviewResponse(
        @Schema(description = "Các cấu hình sẽ đổi, gồm cả cấu hình Admin gửi và cấu hình đồng bộ kèm theo")
        List<ConfigChangeResponse> changes,

        @Schema(description = "Lỗi chặn lưu") List<ConfigMessageResponse> errors,

        @Schema(description = "Cảnh báo không chặn lưu nhưng cần Admin xác nhận")
        List<ConfigMessageResponse> warnings) {}
