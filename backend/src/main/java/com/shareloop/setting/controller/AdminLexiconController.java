package com.shareloop.setting.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.setting.dto.AdminLexiconActiveRequest;
import com.shareloop.setting.dto.AdminLexiconEntry;
import com.shareloop.setting.dto.AdminLexiconRequest;
import com.shareloop.setting.dto.LexiconMatch;
import com.shareloop.setting.dto.LexiconTestRequest;
import com.shareloop.setting.service.AdminLexiconService;
import com.shareloop.setting.service.LexiconService;
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

@Tag(name = "admin-lexicon-controller", description = "Admin quản lý từ điển từ khóa cấm/nhạy cảm (lexicon)")
@RestController
@RequestMapping("/api/v1/admin/lexicon")
public class AdminLexiconController {

    private final AdminLexiconService service;
    private final LexiconService lexiconService;

    public AdminLexiconController(AdminLexiconService service, LexiconService lexiconService) {
        this.service = service;
        this.lexiconService = lexiconService;
    }

    @Operation(
            summary = "Danh sách từ khóa cấm/nhạy cảm",
            description =
                    "Lấy toàn bộ từ khóa trong từ điển theo thứ tự sắp xếp (sort_order), bao gồm cả từ khóa đang ẩn và hiện.")
    @ApiResponse(responseCode = "200", description = "Trả về danh sách từ khóa trong từ điển")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @GetMapping
    public List<AdminLexiconEntry> listAll() {
        return service.listAll();
    }

    @Operation(
            summary = "Kiểm tra văn bản với từ điển",
            description =
                    "Thử nghiệm quét một đoạn văn bản xem có khớp với các từ khóa đang kích hoạt hay không. Trả về chi tiết từ khóa khớp và dạng khớp (EXACT hoặc NORMALIZED).")
    @ApiResponse(responseCode = "200", description = "Trả về danh sách các từ khóa khớp")
    @ApiResponse(responseCode = "400", description = "Đoạn văn bản kiểm tra bị để trống")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @PostMapping("/test")
    public List<LexiconMatch> test(@Valid @RequestBody LexiconTestRequest request) {
        return lexiconService.match(request.text());
    }

    @Operation(
            summary = "Thêm từ khóa mới vào từ điển",
            description =
                    "Tạo một mục từ khóa mới với mức độ xử lý (BLOCK hoặc REVIEW), phương thức khớp (WORD, PHRASE, REGEX), phạm vi áp dụng (POST, CHAT, MESSAGE, BOTH) và danh sách ngoại lệ.")
    @ApiResponse(responseCode = "201", description = "Tạo từ khóa thành công")
    @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ (keyword rỗng, level/matchMode/scope sai)")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @ApiResponse(responseCode = "409", description = "Từ khóa bị trùng trong cùng nhóm và phạm vi")
    @PostMapping
    public ResponseEntity<AdminLexiconEntry> create(
            @Valid @RequestBody AdminLexiconRequest request, @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, adminId));
    }

    @Operation(
            summary = "Cập nhật từ khóa trong từ điển",
            description =
                    "Thay đổi nội dung từ khóa, nhóm phân loại, mức độ xử lý, phương thức khớp, ngoại lệ và phạm vi áp dụng theo ID.")
    @ApiResponse(responseCode = "200", description = "Cập nhật từ khóa thành công")
    @ApiResponse(responseCode = "400", description = "Dữ liệu không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy từ khóa")
    @ApiResponse(responseCode = "409", description = "Từ khóa bị trùng trong cùng nhóm và phạm vi")
    @PutMapping("/{id}")
    public AdminLexiconEntry update(
            @Parameter(description = "ID của từ khóa trong từ điển") @PathVariable long id,
            @Valid @RequestBody AdminLexiconRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return service.update(id, request, adminId);
    }

    @Operation(
            summary = "Bật/tắt trạng thái hoạt động của từ khóa",
            description =
                    "Ẩn hoặc kích hoạt lại từ khóa trong từ điển bằng cách cập nhật cờ active, không xóa cứng khỏi cơ sở dữ liệu.")
    @ApiResponse(responseCode = "200", description = "Cập nhật trạng thái thành công")
    @ApiResponse(responseCode = "400", description = "Thiếu trường active")
    @ApiResponse(responseCode = "401", description = "Chưa đăng nhập")
    @ApiResponse(responseCode = "403", description = "Không có quyền Admin")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy từ khóa")
    @PatchMapping("/{id}/active")
    public AdminLexiconEntry setActive(
            @Parameter(description = "ID của từ khóa trong từ điển") @PathVariable long id,
            @Valid @RequestBody AdminLexiconActiveRequest request,
            @Parameter(hidden = true) @CurrentUserId Long adminId) {
        return service.setActive(id, request.active(), adminId);
    }
}
