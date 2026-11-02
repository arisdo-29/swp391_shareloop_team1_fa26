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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @Operation(summary = "Nhận một gợi ý đồ từ các bài đăng còn khả dụng, không giữ hội thoại")
    @PostMapping("/api/v1/ai/search")
    public AiSearchResponse search(
            @Parameter(hidden = true) @CurrentUserId Long currentUserId, @Valid @RequestBody AiSearchRequest request) {
        return service.search(currentUserId, request.query());
    }
}
