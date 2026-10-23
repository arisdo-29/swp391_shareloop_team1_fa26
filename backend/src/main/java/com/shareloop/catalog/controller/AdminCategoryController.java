package com.shareloop.catalog.controller;

import com.shareloop.catalog.dto.AdminCategoryActiveRequest;
import com.shareloop.catalog.dto.AdminCategoryReorderRequest;
import com.shareloop.catalog.dto.AdminCategoryRequest;
import com.shareloop.catalog.dto.AdminCategoryResponse;
import com.shareloop.catalog.service.AdminCategoryService;
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

@Tag(name = "admin-category-controller", description = "Admin quản lý danh mục đồ dùng")
@RestController
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryController {
    private final AdminCategoryService service;

    public AdminCategoryController(AdminCategoryService service) {
        this.service = service;
    }

    @Operation(
            summary = "Xem danh sách danh mục để quản lý",
            description = "Chỉ Admin. Bao gồm danh mục đang hoạt động và đang ẩn, sắp theo thứ tự hiển thị rồi ID.")
    @GetMapping
    public List<AdminCategoryResponse> list() {
        return service.listAll();
    }

    @Operation(
            summary = "Tạo danh mục mới",
            description =
                    "Chỉ Admin. Thiết lập tên, danh mục cha, cờ hạn chế và quyền đẩy tin. Không gửi sortOrder thì danh mục nằm cuối danh sách.")
    @PostMapping
    public ResponseEntity<AdminCategoryResponse> create(
            @Valid @RequestBody AdminCategoryRequest request, @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, adminId));
    }

    @Operation(
            summary = "Sắp xếp lại thứ tự danh mục",
            description =
                    "Chỉ Admin. Gửi danh sách id của mọi danh mục theo thứ tự mới, mỗi danh mục một lần. Thứ tự lưu là vị trí trong danh sách, bắt đầu từ 1.")
    @PutMapping("/reorder")
    public List<AdminCategoryResponse> reorder(
            @Valid @RequestBody AdminCategoryReorderRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return service.reorder(request, adminId);
    }

    @Operation(
            summary = "Cập nhật thông tin danh mục",
            description =
                    "Chỉ Admin. Sửa tên, danh mục cha, cờ hạn chế, quyền đẩy tin và thứ tự; danh mục cha phải đang hoạt động và không tạo vòng lặp.")
    @PutMapping("/{id}")
    public AdminCategoryResponse update(
            @PathVariable long id,
            @Valid @RequestBody AdminCategoryRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return service.update(id, request, adminId);
    }

    @Operation(
            summary = "Ẩn hoặc hiện danh mục",
            description = "Chỉ Admin. Ẩn danh mục cha sẽ ẩn các danh mục con đang hoạt động.")
    @PatchMapping("/{id}/active")
    public AdminCategoryResponse setActive(
            @PathVariable long id,
            @Valid @RequestBody AdminCategoryActiveRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return service.setActive(id, request.active(), adminId);
    }
}
