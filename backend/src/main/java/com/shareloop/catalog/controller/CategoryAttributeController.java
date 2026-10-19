package com.shareloop.catalog.controller;

import com.shareloop.catalog.dto.AttributeResponse;
import com.shareloop.catalog.service.CategoryAttributeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "category-attribute-controller", description = "Tra cứu thuộc tính của danh mục công khai")
@RestController
@RequestMapping("/api/v1/categories/{categoryId}/attributes")
public class CategoryAttributeController {
    private final CategoryAttributeService service;

    public CategoryAttributeController(CategoryAttributeService service) {
        this.service = service;
    }

    @Operation(
            summary = "Xem các thuộc tính đang hoạt động của danh mục",
            description =
                    "Không cần đăng nhập. Danh mục phải đang hoạt động; thuộc tính được sắp theo thứ tự hiển thị rồi ID.")
    @GetMapping
    public List<AttributeResponse> list(@PathVariable long categoryId) {
        return service.listPublic(categoryId);
    }
}
