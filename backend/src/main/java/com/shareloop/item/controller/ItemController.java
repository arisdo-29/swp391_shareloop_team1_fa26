package com.shareloop.item.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.item.dto.CreateItemRequest;
import com.shareloop.item.dto.ItemListPageResponse;
import com.shareloop.item.dto.ItemResponse;
import com.shareloop.item.dto.PostingQuotaResponse;
import com.shareloop.item.dto.UpdateItemRequest;
import com.shareloop.item.entity.ItemCondition;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.service.ItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller quản lý bài đăng: đăng bài, xem chi tiết, xem bài của tôi (UC-20, UC-22).
 */
@Tag(name = "Item", description = "Quản lý bài đăng cho tặng và trao đổi đồ")
@RestController
@Validated
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
    public ItemResponse create(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @CurrentUserId Long currentUserId,
            @Valid @RequestBody CreateItemRequest request) {
        return itemService.createItem(currentUserId, request);
    }

    @Operation(summary = "Sửa bài đăng và gửi duyệt lại")
    @PutMapping("/api/v1/items/{id}")
    public ItemResponse update(
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @CurrentUserId Long currentUserId,
            @Valid @RequestBody UpdateItemRequest request) {
        return itemService.updateItem(id, currentUserId, request);
    }

    @Operation(summary = "Gỡ bài đăng của tôi")
    @DeleteMapping("/api/v1/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @CurrentUserId Long currentUserId) {
        itemService.remove(id, currentUserId);
    }

    /**
     * Xem chi tiết một bài đăng (bài APPROVED ai cũng xem; chủ bài xem mọi trạng thái).
     */
    @Operation(summary = "Chi tiết một bài đăng")
    @GetMapping("/api/v1/items/{id}")
    public ItemResponse getById(
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        Long currentUserId = parseUserId(jwt);
        return itemService.getItem(id, currentUserId);
    }

    /**
     * Xem danh sách bài đăng của tôi (cần đăng nhập).
     */
    /**
     * Hạn mức đăng bài hiện tại (Cho tặng và Trao đổi) cho trang Đăng tin.
     */
    @Operation(summary = "Hạn mức đăng bài của tôi")
    @GetMapping("/api/v1/me/posting-quota")
    public PostingQuotaResponse getPostingQuota(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @CurrentUserId Long currentUserId) {
        return itemService.getPostingQuota(currentUserId);
    }

    @Operation(summary = "Danh sách bài đăng của tôi")
    @GetMapping("/api/v1/me/items")
    public List<ItemResponse> listMyItems(
            @io.swagger.v3.oas.annotations.Parameter(hidden = true) @CurrentUserId Long currentUserId) {
        return itemService.listMyItems(currentUserId);
    }

    @Operation(summary = "Danh sách bài đã duyệt")
    @GetMapping("/api/v1/items")
    public ItemListPageResponse listPublic(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ItemCondition condition,
            @RequestParam(required = false) OfferType offerType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return itemService.listPublicItems(categoryId, areaId, q, condition, offerType, page, size);
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
