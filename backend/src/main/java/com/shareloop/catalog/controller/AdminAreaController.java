package com.shareloop.catalog.controller;

import com.shareloop.catalog.dto.AdminAreaActiveRequest;
import com.shareloop.catalog.dto.AdminAreaRequest;
import com.shareloop.catalog.dto.AdminAreaResponse;
import com.shareloop.catalog.service.AdminAreaService;
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
@RequestMapping("/api/v1/admin/areas")
public class AdminAreaController {
    private final AdminAreaService service;

    public AdminAreaController(AdminAreaService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminAreaResponse> list() {
        return service.listAll();
    }

    @PostMapping
    public ResponseEntity<AdminAreaResponse> create(@Valid @RequestBody AdminAreaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public AdminAreaResponse update(@PathVariable long id, @Valid @RequestBody AdminAreaRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public AdminAreaResponse setActive(@PathVariable long id, @Valid @RequestBody AdminAreaActiveRequest request) {
        return service.setActive(id, request.active());
    }
}
