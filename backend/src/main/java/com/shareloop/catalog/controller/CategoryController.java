package com.shareloop.catalog.controller;

import com.shareloop.catalog.dto.CategoryResponse;
import com.shareloop.catalog.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller mỏng: chỉ nhận request, gọi service, trả DTO thẳng (không bọc). Lỗi để GlobalExceptionHandler xử lý. */
@Tag(name = "category-controller", description = "Tra cứu danh mục đồ dùng công khai")
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    // GET công khai: nằm trong PUBLIC_GET của SecurityConfig, không cần token.
    @Operation(
            summary = "Xem danh sách danh mục đang hoạt động",
            description = "Không cần đăng nhập. Danh sách được sắp xếp theo thứ tự hiển thị tăng dần.")
    @GetMapping
    public List<CategoryResponse> list() {
        return categoryService.listActive();
    }

    @Operation(
            summary = "Xem chi tiết danh mục",
            description = "Không cần đăng nhập. Chỉ trả về danh mục đang hoạt động và chưa bị xoá.")
    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable long id) {
        return categoryService.getById(id);
    }
}
