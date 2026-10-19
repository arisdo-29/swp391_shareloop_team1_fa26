package com.shareloop.catalog.controller;

import com.shareloop.catalog.dto.AdminAttributeActiveRequest;
import com.shareloop.catalog.dto.AdminAttributeRequest;
import com.shareloop.catalog.dto.AdminAttributeResponse;
import com.shareloop.catalog.service.AdminCategoryAttributeService;
import com.shareloop.common.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "admin-category-attribute-controller", description = "Admin quản lý thuộc tính của danh mục")
@RestController
@RequestMapping("/api/v1/admin/categories/{categoryId}/attributes")
public class AdminCategoryAttributeController {
    private final AdminCategoryAttributeService service;

    public AdminCategoryAttributeController(AdminCategoryAttributeService service) {
        this.service = service;
    }

    @Operation(
            summary = "Xem danh sách thuộc tính để quản lý",
            description = "Chỉ Admin. Trả về mọi thuộc tính chưa bị xoá của danh mục, kể cả thuộc tính đang ẩn.")
    @GetMapping
    public List<AdminAttributeResponse> list(@PathVariable long categoryId) {
        return service.listAll(categoryId);
    }

    @Operation(
            summary = "Tạo thuộc tính cho danh mục",
            description =
                    "Chỉ Admin. Danh mục phải đang hoạt động. Thuộc tính kiểu SELECT phải có danh sách tùy chọn không rỗng và không trùng.")
    @PostMapping
    public ResponseEntity<AdminAttributeResponse> create(
            @PathVariable long categoryId,
            @Valid @RequestBody AdminAttributeRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(categoryId, request, adminId));
    }

    @Operation(
            summary = "Cập nhật thuộc tính của danh mục",
            description =
                    "Chỉ Admin. Sửa tên, kiểu dữ liệu, tùy chọn, cờ bắt buộc và thứ tự. Thuộc tính trạng thái hoạt động bắt buộc của đồ điện tử chỉ được đổi thứ tự.")
    @PutMapping("/{attributeId}")
    public AdminAttributeResponse update(
            @PathVariable long categoryId,
            @PathVariable long attributeId,
            @Valid @RequestBody AdminAttributeRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return service.update(categoryId, attributeId, request, adminId);
    }

    @Operation(
            summary = "Ẩn hoặc hiện thuộc tính của danh mục",
            description = "Chỉ Admin. Không được ẩn thuộc tính trạng thái hoạt động bắt buộc của đồ điện tử.")
    @PatchMapping("/{attributeId}/active")
    public AdminAttributeResponse setActive(
            @PathVariable long categoryId,
            @PathVariable long attributeId,
            @Valid @RequestBody AdminAttributeActiveRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return service.setActive(categoryId, attributeId, request.active(), adminId);
    }
}
