package com.shareloop.catalog.controller;

import com.shareloop.catalog.dto.AdminAreaActiveRequest;
import com.shareloop.catalog.dto.AdminAreaRequest;
import com.shareloop.catalog.dto.AdminAreaResponse;
import com.shareloop.catalog.service.AdminAreaService;
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

@Tag(name = "admin-area-controller", description = "Admin quản lý khu vực")
@RestController
@RequestMapping("/api/v1/admin/areas")
public class AdminAreaController {
    private final AdminAreaService service;

    public AdminAreaController(AdminAreaService service) {
        this.service = service;
    }

    @Operation(
            summary = "Xem danh sách khu vực để quản lý",
            description = "Chỉ Admin. Bao gồm khu vực đang hoạt động và đang ẩn, chưa bị xoá.")
    @GetMapping
    public List<AdminAreaResponse> list() {
        return service.listAll();
    }

    @Operation(
            summary = "Tạo khu vực mới",
            description =
                    "Chỉ Admin. Không có khu vực cha thì tạo cấp thành phố; cha cấp thành phố đang hoạt động thì tạo khu vực cấp hai.")
    @PostMapping
    public ResponseEntity<AdminAreaResponse> create(
            @Valid @RequestBody AdminAreaRequest request, @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, adminId));
    }

    @Operation(
            summary = "Cập nhật thông tin khu vực",
            description = "Chỉ Admin. Sửa tên hoặc khu vực cha; không thể chuyển khu vực có con thành khu vực cấp hai.")
    @PutMapping("/{id}")
    public AdminAreaResponse update(
            @PathVariable long id,
            @Valid @RequestBody AdminAreaRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return service.update(id, request, adminId);
    }

    @Operation(
            summary = "Ẩn hoặc hiện khu vực",
            description =
                    "Chỉ Admin. Ẩn khu vực cha sẽ ẩn các khu vực con; hiện lại cha không tự hiện các con. Chỉ hiện con khi cha đang hoạt động.")
    @PatchMapping("/{id}/active")
    public AdminAreaResponse setActive(
            @PathVariable long id,
            @Valid @RequestBody AdminAreaActiveRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return service.setActive(id, request.active(), adminId);
    }
}
