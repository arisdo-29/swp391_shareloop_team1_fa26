package com.shareloop.request.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.request.dto.ConfirmLogisticsRequest;
import com.shareloop.request.dto.CreateRequest;
import com.shareloop.request.dto.HandoverConfirmRequest;
import com.shareloop.request.dto.RequestContactResponse;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.dto.ScheduleRequest;
import com.shareloop.request.service.RequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@Tag(name = "Request", description = "Quản lý yêu cầu giao dịch xin đồ và trao đổi đồ")
@RestController
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    /**
     * Gửi yêu cầu xin đồ hoặc đề nghị trao đổi tới một bài đăng (F10).
     */
    @Operation(summary = "Gửi yêu cầu xin đồ hoặc đề nghị trao đổi")
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
    @Operation(summary = "Chủ bài đăng xem danh sách các yêu cầu gửi tới bài của mình")
    @GetMapping("/api/v1/items/{itemId}/requests")
    public List<RequestResponse> listByItem(@PathVariable long itemId, @CurrentUserId Long currentUserId) {
        return requestService.listByItem(itemId, currentUserId);
    }

    /**
     * Người dùng xem danh sách các yêu cầu do chính mình gửi đi.
     */
    @Operation(summary = "Xem danh sách tất cả yêu cầu do chính mình gửi đi")
    @GetMapping("/api/v1/me/requests")
    public List<RequestResponse> listMyRequests(@CurrentUserId Long currentUserId) {
        return requestService.listMyRequests(currentUserId);
    }

    /**
     * Người gửi tự huỷ yêu cầu khi còn ở trạng thái PENDING.
     */
    @Operation(summary = "Người gửi tự huỷ yêu cầu khi còn ở trạng thái PENDING")
    @PostMapping("/api/v1/requests/{id}/cancel")
    public RequestResponse cancel(@PathVariable long id, @CurrentUserId Long currentUserId) {
        return requestService.cancelRequest(id, currentUserId);
    }

    /**
     * Chủ bài đăng chọn người nhận, các yêu cầu khác tự động bị từ chối.
     */
    @Operation(summary = "Chủ bài đăng chọn người nhận cho món đồ")
    @PostMapping("/api/v1/requests/{id}/select")
    public RequestResponse select(@PathVariable long id, @CurrentUserId Long currentUserId) {
        return requestService.selectReceiver(id, currentUserId);
    }

    /**
     * Xem thông tin chi tiết của một yêu cầu giao dịch.
     */
    @Operation(summary = "Xem thông tin chi tiết một yêu cầu giao dịch")
    @GetMapping("/api/v1/requests/{id}")
    public RequestResponse getDetail(@PathVariable long id, @CurrentUserId Long currentUserId) {
        return requestService.getDetail(id, currentUserId);
    }

    /**
     * Đề xuất hoặc cập nhật lịch hẹn giao nhận (FR-79, FR-80, BR-T01, BR-T02).
     */
    @Operation(summary = "Đề xuất hoặc cập nhật lịch hẹn giao nhận")
    @PostMapping("/api/v1/requests/{id}/schedule")
    public RequestResponse updateSchedule(
            @PathVariable long id, @CurrentUserId Long currentUserId, @Valid @RequestBody ScheduleRequest request) {
        return requestService.updateSchedule(id, currentUserId, request);
    }

    /**
     * Xác nhận lịch hẹn giao nhận và cam kết trách nhiệm (FR-80, FR-81, BR-T03, BR-T05).
     */
    @Operation(summary = "Xác nhận lịch hẹn giao nhận và cam kết trách nhiệm")
    @PostMapping("/api/v1/requests/{id}/confirm-logistics")
    public RequestResponse confirmLogistics(
            @PathVariable long id,
            @CurrentUserId Long currentUserId,
            @RequestBody(required = false) ConfirmLogisticsRequest request) {
        return requestService.confirmLogistics(id, currentUserId, request);
    }

    /**
     * Lấy thông tin liên lạc của đối tác khi giao dịch đã chốt lịch (FR-82, BR-T11).
     */
    @Operation(summary = "Lấy thông tin liên lạc của đối tác khi giao dịch đã chốt lịch")
    @GetMapping("/api/v1/requests/{id}/contact")
    public RequestContactResponse getContact(@PathVariable long id, @CurrentUserId Long currentUserId) {
        return requestService.getContactInfo(id, currentUserId);
    }

    /**
     * Xác nhận đã giao / đã nhận đồ và trả lời đúng mô tả (FR-84, FR-85, BR-D02, BR-R05, UC-27).
     */
    @Operation(summary = "Xác nhận đã giao hoặc đã nhận đồ và trả lời đúng mô tả")
    @PostMapping("/api/v1/requests/{id}/confirm-handover")
    public RequestResponse confirmHandover(
            @PathVariable long id,
            @CurrentUserId Long currentUserId,
            @RequestBody(required = false) HandoverConfirmRequest request) {
        return requestService.confirmHandover(id, currentUserId, request);
    }
}
