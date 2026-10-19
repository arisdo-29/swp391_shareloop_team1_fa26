package com.shareloop.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RespondReportRequest(
        @NotBlank @Size(max = 2000) String response, List<@NotNull @Positive Long> evidenceMediaIds) {}
