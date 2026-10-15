package com.shareloop.catalog.controller;

import com.shareloop.catalog.dto.AdminCategoryActiveRequest;
import com.shareloop.catalog.dto.AdminCategoryRequest;
import com.shareloop.catalog.dto.AdminCategoryResponse;
import com.shareloop.catalog.service.AdminCategoryService;
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

@RestController
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryController {
    private final AdminCategoryService service;

    public AdminCategoryController(AdminCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminCategoryResponse> list() {
        return service.listAll();
    }

    @PostMapping
    public ResponseEntity<AdminCategoryResponse> create(@Valid @RequestBody AdminCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public AdminCategoryResponse update(@PathVariable long id, @Valid @RequestBody AdminCategoryRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public AdminCategoryResponse setActive(
            @PathVariable long id, @Valid @RequestBody AdminCategoryActiveRequest request) {
        return service.setActive(id, request.active());
    }
}
