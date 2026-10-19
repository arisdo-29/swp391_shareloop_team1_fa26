package com.shareloop.setting.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.setting.dto.AdminChecklistActiveRequest;
import com.shareloop.setting.dto.AdminChecklistEntry;
import com.shareloop.setting.dto.AdminChecklistReorderRequest;
import com.shareloop.setting.dto.AdminChecklistRequest;
import com.shareloop.setting.service.AdminChecklistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

@Tag(name = "admin-checklist-controller", description = "Admin quản lý danh sách câu hỏi kiểm duyệt (checklist)")
@RestController
@RequestMapping("/api/v1/admin/checklist")
public class AdminChecklistController {

    private final AdminChecklistService service;

    public AdminChecklistController(AdminChecklistService service) {
        this.service = service;
    }

    @Operation(
            summary = "Danh sách câu hỏi checklist",
            description =
                    "Lấy toàn bộ danh sách câu hỏi checklist kiểm duyệt bài đăng theo thứ tự sắp xếp, bao gồm cả câu hỏi đang ẩn/hiện.")
    @ApiResponse(responseCode = "200", description = "Trả về danh sách câu hỏi checklist")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @GetMapping
    public List<AdminChecklistEntry> listAll() {
        return service.listAll();
    }

    @Operation(
            summary = "Thêm câu hỏi checklist mới",
            description = "Thêm một câu hỏi kiểm duyệt mới vào cuối danh sách checklist.")
    @ApiResponse(responseCode = "201", description = "Tạo câu hỏi checklist thành công")
    @ApiResponse(responseCode = "400", description = "Nội dung câu hỏi rỗng hoặc vượt quá giới hạn")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @PostMapping
    public ResponseEntity<AdminChecklistEntry> create(
            @Valid @RequestBody AdminChecklistRequest request, @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, adminId));
    }

    @Operation(summary = "Cập nhật câu hỏi checklist", description = "Sửa nội dung của một câu hỏi checklist theo ID.")
    @ApiResponse(responseCode = "200", description = "Cập nhật câu hỏi thành công")
    @ApiResponse(responseCode = "400", description = "Nội dung câu hỏi không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy câu hỏi checklist")
    @PutMapping("/{id}")
    public AdminChecklistEntry update(
            @Parameter(description = "ID của câu hỏi checklist") @PathVariable long id,
            @Valid @RequestBody AdminChecklistRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return service.update(id, request, adminId);
    }

    @Operation(
            summary = "Bật/tắt trạng thái câu hỏi checklist",
            description = "Ẩn hoặc kích hoạt câu hỏi checklist mà không xóa cứng khỏi cơ sở dữ liệu.")
    @ApiResponse(responseCode = "200", description = "Cập nhật trạng thái thành công")
    @ApiResponse(responseCode = "400", description = "Thiếu trường active")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy câu hỏi checklist")
    @PatchMapping("/{id}/active")
    public AdminChecklistEntry setActive(
            @Parameter(description = "ID của câu hỏi checklist") @PathVariable long id,
            @Valid @RequestBody AdminChecklistActiveRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return service.setActive(id, request.active(), adminId);
    }

    @Operation(
            summary = "Sắp xếp lại thứ tự câu hỏi checklist",
            description = "Cập nhật lại thứ tự sắp xếp (sort_order) cho danh sách câu hỏi checklist theo danh sách ID.")
    @ApiResponse(responseCode = "200", description = "Sắp xếp lại thành công")
    @ApiResponse(responseCode = "400", description = "Danh sách ID không hợp lệ hoặc không khớp số lượng")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @PutMapping("/reorder")
    public List<AdminChecklistEntry> reorder(
            @Valid @RequestBody AdminChecklistReorderRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return service.reorder(request, adminId);
    }
}
