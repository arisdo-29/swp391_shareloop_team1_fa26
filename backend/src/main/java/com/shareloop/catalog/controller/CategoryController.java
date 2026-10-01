package com.shareloop.catalog.controller;

import com.shareloop.catalog.dto.CategoryResponse;
import com.shareloop.catalog.service.CategoryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller mỏng: chỉ nhận request, gọi service, trả DTO thẳng (không bọc). Lỗi để GlobalExceptionHandler xử lý. */
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    // GET công khai: nằm trong PUBLIC_GET của SecurityConfig, không cần token.
    @GetMapping
    public List<CategoryResponse> list() {
        return categoryService.listActive();
    }

    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable long id) {
        return categoryService.getById(id);
    }
}
