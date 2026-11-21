package com.shareloop.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Kết quả cập nhật lô cấu hình")
public record BatchUpdateConfigResponse(
        @Schema(description = "Mọi cấu hình đã đổi giá trị, gồm cả cấu hình đồng bộ")
        List<ConfigResponse> configs) {}
