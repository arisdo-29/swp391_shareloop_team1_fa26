package com.shareloop.setting.controller;

import com.shareloop.setting.dto.AdminLexiconActiveRequest;
import com.shareloop.setting.dto.AdminLexiconEntry;
import com.shareloop.setting.dto.AdminLexiconRequest;
import com.shareloop.setting.service.AdminLexiconService;
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
@RequestMapping("/api/v1/admin/lexicon")
public class AdminLexiconController {

    private final AdminLexiconService service;

    public AdminLexiconController(AdminLexiconService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminLexiconEntry> listAll() {
        return service.listAll();
    }

    @PostMapping
    public ResponseEntity<AdminLexiconEntry> create(@Valid @RequestBody AdminLexiconRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public AdminLexiconEntry update(@PathVariable long id, @Valid @RequestBody AdminLexiconRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public AdminLexiconEntry setActive(@PathVariable long id, @Valid @RequestBody AdminLexiconActiveRequest request) {
        return service.setActive(id, request.active());
    }
}
