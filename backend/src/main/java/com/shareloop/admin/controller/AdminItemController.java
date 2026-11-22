package com.shareloop.admin.controller;

import com.shareloop.admin.dto.AdminItemFullDetail;
import com.shareloop.admin.dto.AdminItemRow;
import com.shareloop.admin.dto.HideItemRequest;
import com.shareloop.admin.service.AdminItemService;
import com.shareloop.common.api.PageResponse;
import com.shareloop.common.security.CurrentUserId;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Quản lý bài đăng của Admin. Chỉ Admin truy cập được (/api/v1/admin/** do SecurityConfig bảo vệ). */
@RestController
@RequestMapping("/api/v1/admin/items")
@Validated
public class AdminItemController {

    private final AdminItemService adminItemService;

    public AdminItemController(AdminItemService adminItemService) {
        this.adminItemService = adminItemService;
    }

    @GetMapping
    public PageResponse<AdminItemRow> list(
            @RequestParam(required = false) ItemStatus status,
            // true chỉ bài đang hiện, false chỉ bài đang ẩn, bỏ trống là cả hai.
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) OfferType offerType,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long ownerId,
            // q khớp một phần tiêu đề, tên hoặc email chủ bài.
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean boosted,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminItemService.list(status, active, offerType, categoryId, ownerId, q, from, to, boosted, page, size);
    }

    @GetMapping("/{id}")
    public AdminItemFullDetail detail(@PathVariable long id) {
        return adminItemService.getDetail(id);
    }

    @PostMapping("/{id}/hide")
    public AdminItemFullDetail hide(
            @PathVariable long id, @CurrentUserId Long adminId, @Valid @RequestBody HideItemRequest request) {
        return adminItemService.hide(adminId, id, request.reason());
    }

    @PostMapping("/{id}/unhide")
    public AdminItemFullDetail unhide(
            @PathVariable long id, @CurrentUserId Long adminId, @Valid @RequestBody HideItemRequest request) {
        return adminItemService.unhide(adminId, id, request.reason());
    }
}
