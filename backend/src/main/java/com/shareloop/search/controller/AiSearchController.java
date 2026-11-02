package com.shareloop.search.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.search.dto.AiDescriptionRequest;
import com.shareloop.search.dto.AiDescriptionResponse;
import com.shareloop.search.dto.AiQuotaResponse;
import com.shareloop.search.dto.AiSearchRequest;
import com.shareloop.search.dto.AiSearchResponse;
import com.shareloop.search.service.AiDescriptionService;
import com.shareloop.search.service.AiQuotaService;
import com.shareloop.search.service.AiSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI", description = "Tìm đồ đang có và gợi ý bản nháp mô tả từ tên món")
@RestController
@Validated
public class AiSearchController {
    private final AiSearchService service;
    private final AiQuotaService quotaService;
    private final AiDescriptionService descriptionService;

    public AiSearchController(
            AiSearchService service, AiQuotaService quotaService, AiDescriptionService descriptionService) {
        this.service = service;
        this.quotaService = quotaService;
        this.descriptionService = descriptionService;
    }

    @Operation(summary = "Xem lượt AI miễn phí còn lại và phí cho lượt tiếp theo")
    @GetMapping("/api/v1/ai/quota")
    public AiQuotaResponse quota(@Parameter(hidden = true) @CurrentUserId Long currentUserId) {
        return quotaService.getQuota(currentUserId);
    }

    @Operation(summary = "Nhận một gợi ý đồ từ các bài đăng còn khả dụng, không giữ hội thoại")
    @PostMapping("/api/v1/ai/search")
    public AiSearchResponse search(
            @Parameter(hidden = true) @CurrentUserId Long currentUserId, @Valid @RequestBody AiSearchRequest request) {
        return service.search(currentUserId, request.query());
    }

    @Operation(summary = "Gợi ý bản nháp mô tả từ tên món đồ, không đọc ảnh")
    @PostMapping("/api/v1/ai/describe-item")
    public AiDescriptionResponse describeItem(
            @Parameter(hidden = true) @CurrentUserId Long currentUserId,
            @Valid @RequestBody AiDescriptionRequest request) {
        return descriptionService.describe(currentUserId, request.title());
    }
}
