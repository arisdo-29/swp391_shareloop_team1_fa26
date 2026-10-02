package com.shareloop.request.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.request.dto.CreateRequest;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.service.RequestService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller xử lý các yêu cầu xin đồ và trao đổi đồ (UC-20, UC-21, F10).
 */
@RestController
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    /**
     * Gửi yêu cầu xin đồ hoặc đề nghị trao đổi tới một bài đăng (F10).
     */
    @PostMapping("/api/v1/items/{itemId}/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestResponse create(
            @PathVariable long itemId,
            @CurrentUserId Long currentUserId,
            @RequestBody(required = false) CreateRequest request) {
        return requestService.createRequest(itemId, currentUserId, request);
    }

    /**
     * Chủ bài đăng xem danh sách các yêu cầu gửi tới bài của mình.
     */
    @GetMapping("/api/v1/items/{itemId}/requests")
    public List<RequestResponse> listByItem(@PathVariable long itemId, @CurrentUserId Long currentUserId) {
        return requestService.listByItem(itemId, currentUserId);
    }

    /**
     * Người dùng xem danh sách các yêu cầu do chính mình gửi đi.
     */
    @GetMapping("/api/v1/me/requests")
    public List<RequestResponse> listMyRequests(@CurrentUserId Long currentUserId) {
        return requestService.listMyRequests(currentUserId);
    }

    /**
     * Người gửi tự huỷ yêu cầu khi còn ở trạng thái PENDING.
     */
    @PostMapping("/api/v1/requests/{id}/cancel")
    public RequestResponse cancel(@PathVariable long id, @CurrentUserId Long currentUserId) {
        return requestService.cancelRequest(id, currentUserId);
    }
}
