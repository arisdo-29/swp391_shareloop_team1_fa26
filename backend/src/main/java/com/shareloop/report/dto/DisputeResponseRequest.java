package com.shareloop.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Yêu cầu giải trình khiếu nại của bên bị khiếu nại")
public record DisputeResponseRequest(
        @NotBlank(message = "Nội dung giải trình không được để trống.")
        @Size(max = 1000, message = "Nội dung giải trình không được vượt quá 1000 ký tự.")
        @Schema(
                description = "Nội dung giải trình của bên bị khiếu nại",
                example = "Đồ đã được kiểm tra cẩn thận và còn nguyên vẹn trước khi bàn giao.")
        String note,

        @Schema(description = "Danh sách ID media làm bằng chứng đối chất")
        List<Long> evidenceMediaIds) {}
