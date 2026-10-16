package com.shareloop.catalog.controller;

import com.shareloop.catalog.dto.AttributeResponse;
import com.shareloop.catalog.service.CategoryAttributeService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories/{categoryId}/attributes")
public class CategoryAttributeController {
    private final CategoryAttributeService service;

    public CategoryAttributeController(CategoryAttributeService service) {
        this.service = service;
    }

    @GetMapping
    public List<AttributeResponse> list(@PathVariable long categoryId) {
        return service.listPublic(categoryId);
    }
}
