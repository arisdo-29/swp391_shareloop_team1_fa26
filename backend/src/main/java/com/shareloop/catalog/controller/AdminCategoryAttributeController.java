package com.shareloop.catalog.controller;

import com.shareloop.catalog.dto.AdminAttributeActiveRequest;
import com.shareloop.catalog.dto.AdminAttributeRequest;
import com.shareloop.catalog.dto.AdminAttributeResponse;
import com.shareloop.catalog.service.AdminCategoryAttributeService;
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
@RequestMapping("/api/v1/admin/categories/{categoryId}/attributes")
public class AdminCategoryAttributeController {
    private final AdminCategoryAttributeService service;

    public AdminCategoryAttributeController(AdminCategoryAttributeService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminAttributeResponse> list(@PathVariable long categoryId) {
        return service.listAll(categoryId);
    }

    @PostMapping
    public ResponseEntity<AdminAttributeResponse> create(
            @PathVariable long categoryId, @Valid @RequestBody AdminAttributeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(categoryId, request));
    }

    @PutMapping("/{attributeId}")
    public AdminAttributeResponse update(
            @PathVariable long categoryId,
            @PathVariable long attributeId,
            @Valid @RequestBody AdminAttributeRequest request) {
        return service.update(categoryId, attributeId, request);
    }

    @PatchMapping("/{attributeId}/active")
    public AdminAttributeResponse setActive(
            @PathVariable long categoryId,
            @PathVariable long attributeId,
            @Valid @RequestBody AdminAttributeActiveRequest request) {
        return service.setActive(categoryId, attributeId, request.active());
    }
}
