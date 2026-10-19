package com.shareloop.search.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.search.dto.AiQuotaResponse;
import com.shareloop.search.dto.AiSearchRequest;
import com.shareloop.search.dto.AiSearchResponse;
import com.shareloop.search.service.AiQuotaService;
import com.shareloop.search.service.AiSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI Search", description = "Trợ lý tìm đồ bằng bộ lọc do AI tách và dữ liệu bài đăng thật")
@RestController
@Validated
public class AiSearchController {
    private final AiSearchService service;
    private final AiQuotaService quotaService;

    public AiSearchController(AiSearchService service, AiQuotaService quotaService) {
        this.service = service;
        this.quotaService = quotaService;
    }

    @Operation(summary = "Xem lượt AI miễn phí còn lại và phí cho lượt tiếp theo")
    @GetMapping("/api/v1/ai/quota")
    public AiQuotaResponse quota(@Parameter(hidden = true) @CurrentUserId Long currentUserId) {
        return quotaService.getQuota(currentUserId);
    }

    @Operation(summary = "Tìm bài đã duyệt từ nhu cầu bằng câu tự nhiên")
    @PostMapping("/api/v1/ai/search")
    public AiSearchResponse search(
            @Parameter(hidden = true) @CurrentUserId Long currentUserId,
            @Valid @RequestBody AiSearchRequest request,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(currentUserId, request.query(), page, size);
    }
}
