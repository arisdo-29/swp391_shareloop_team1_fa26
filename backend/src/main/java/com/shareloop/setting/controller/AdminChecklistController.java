package com.shareloop.setting.controller;

import com.shareloop.setting.dto.AdminChecklistActiveRequest;
import com.shareloop.setting.dto.AdminChecklistEntry;
import com.shareloop.setting.dto.AdminChecklistReorderRequest;
import com.shareloop.setting.dto.AdminChecklistRequest;
import com.shareloop.setting.service.AdminChecklistService;
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
@RequestMapping("/api/v1/admin/checklist")
public class AdminChecklistController {

    private final AdminChecklistService service;

    public AdminChecklistController(AdminChecklistService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminChecklistEntry> listAll() {
        return service.listAll();
    }

    @PostMapping
    public ResponseEntity<AdminChecklistEntry> create(@Valid @RequestBody AdminChecklistRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public AdminChecklistEntry update(@PathVariable long id, @Valid @RequestBody AdminChecklistRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public AdminChecklistEntry setActive(
            @PathVariable long id, @Valid @RequestBody AdminChecklistActiveRequest request) {
        return service.setActive(id, request.active());
    }

    @PutMapping("/reorder")
    public List<AdminChecklistEntry> reorder(@Valid @RequestBody AdminChecklistReorderRequest request) {
        return service.reorder(request);
    }
}
