package com.shareloop.request.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.request.dto.AdminRequestPageResponse;
import com.shareloop.request.dto.CancelTransactionRequest;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.service.AdminRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin xem danh sách giao dịch và huỷ giao dịch bị kẹt. SecurityConfig đã chỉ cho ADMIN vào /api/v1/admin/**. */
@Tag(name = "admin-request-controller", description = "Admin xem và huỷ giao dịch")
@Validated
@RestController
@RequestMapping("/api/v1/admin/requests")
public class AdminRequestController {

    private final AdminRequestService adminRequestService;

    public AdminRequestController(AdminRequestService adminRequestService) {
        this.adminRequestService = adminRequestService;
    }

    @Operation(
            summary = "Danh sách giao dịch",
            description = "Chỉ Admin. Lọc theo trạng thái, giao dịch bị kẹt và từ khoá; mới nhất trước.")
    @GetMapping
    public AdminRequestPageResponse list(
            @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) Boolean stuck,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return adminRequestService.list(status, stuck, q, page, size);
    }

    @Operation(summary = "Huỷ giao dịch", description = "Chỉ Admin. Bắt buộc có lý do, hành động được ghi nhật ký.")
    @PostMapping("/{id}/cancel")
    public RequestResponse cancel(
            @PathVariable long id, @CurrentUserId Long adminId, @Valid @RequestBody CancelTransactionRequest request) {
        return adminRequestService.cancel(adminId, id, request);
    }
}
