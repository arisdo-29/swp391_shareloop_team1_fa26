package com.shareloop.media.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.media.dto.MediaResponse;
import com.shareloop.media.service.MediaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {
    private final MediaService service;

    public MediaController(MediaService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MediaResponse upload(@RequestPart("file") MultipartFile file, @CurrentUserId Long userId) {
        return service.upload(file, userId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> read(@PathVariable long id) {
        MediaService.MediaContent content = service.readPublic(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .body(content.bytes());
    }
}
