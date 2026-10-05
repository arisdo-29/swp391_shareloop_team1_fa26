package com.shareloop.item.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.item.dto.CreateItemRequest;
import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.service.ItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller quản lý bài đăng: đăng bài, xem chi tiết, xem bài của tôi (UC-20, UC-22).
 */
@Tag(name = "Item", description = "Quản lý bài đăng cho tặng và trao đổi đồ")
@RestController
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /**
     * Đăng bài mới (cần đăng nhập).
     */
    @Operation(summary = "Đăng bài mới")
    @PostMapping("/api/v1/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse create(@CurrentUserId Long currentUserId, @Valid @RequestBody CreateItemRequest request) {
        return itemService.createItem(currentUserId, request);
    }

    /**
     * Xem chi tiết một bài đăng (bài APPROVED ai cũng xem; chủ bài xem mọi trạng thái).
     */
    @Operation(summary = "Chi tiết một bài đăng")
    @GetMapping("/api/v1/items/{id}")
    public ItemResponse getById(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Long currentUserId = parseUserId(jwt);
        return itemService.getItem(id, currentUserId);
    }

    /**
     * Xem danh sách bài đăng của tôi (cần đăng nhập).
     */
    @Operation(summary = "Danh sách bài đăng của tôi")
    @GetMapping("/api/v1/me/items")
    public List<ItemResponse> listMyItems(@CurrentUserId Long currentUserId) {
        return itemService.listMyItems(currentUserId);
    }

    private Long parseUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            return null;
        }
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
