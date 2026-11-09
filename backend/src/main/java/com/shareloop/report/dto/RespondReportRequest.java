package com.shareloop.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RespondReportRequest(
        @NotBlank(message = "Nội dung giải trình không được để trống.")
        @Size(max = 2000, message = "Nội dung giải trình không được vượt quá 2000 ký tự.")
        String response,

        List<@NotNull @Positive Long> evidenceMediaIds) {}
