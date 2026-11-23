package com.shareloop.request.controller;

import com.shareloop.common.security.CurrentUserId;
import com.shareloop.request.dto.CancelTransactionRequest;
import com.shareloop.request.dto.ChangeOfferedItemRequest;
import com.shareloop.request.dto.ConfirmLogisticsRequest;
import com.shareloop.request.dto.CreateRequest;
import com.shareloop.request.dto.HandoverConfirmRequest;
import com.shareloop.request.dto.HandoverFailedRequest;
import com.shareloop.request.dto.RefuseHandoverRequest;
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
     * Mọi yêu cầu tới các bài của người gọi trong một lần (G2), có lọc theo trạng thái.
     */
    @Operation(summary = "Xem mọi yêu cầu gửi tới các bài của mình trong một lần, lọc theo trạng thái")
    @GetMapping("/api/v1/me/received-requests")
    public List<RequestResponse> listReceivedRequests(
            @CurrentUserId Long currentUserId,
            @org.springframework.web.bind.annotation.RequestParam(required = false)
                    java.util.Set<com.shareloop.request.entity.RequestStatus> status) {
        return requestService.listReceivedRequests(currentUserId, status);
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
     * Chủ bài hoặc người nhận huỷ giao dịch sau khi đã chọn người, bắt buộc nêu lý do.
     */
    @Operation(summary = "Huỷ giao dịch sau khi đã chọn người nhận")
    @PostMapping("/api/v1/requests/{id}/cancel-transaction")
    public RequestResponse cancelTransaction(
            @PathVariable long id,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody CancelTransactionRequest request) {
        return requestService.cancelTransaction(id, currentUserId, request);
    }

    /**
     * Chủ bài đăng chọn người nhận, các yêu cầu khác tự động bị từ chối.
     */
    @Operation(summary = "Chủ bài đăng chọn người nhận cho món đồ")
    @PostMapping("/api/v1/requests/{id}/select")
    public RequestResponse select(@PathVariable long id, @CurrentUserId Long currentUserId) {
        return requestService.selectReceiver(id, currentUserId);
    }

    @Operation(summary = "Người gửi yêu cầu thay món đề nghị trước khi chốt lịch")
    @PostMapping("/api/v1/requests/{id}/offered-item")
    public RequestResponse changeOfferedItem(
            @PathVariable long id,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody ChangeOfferedItemRequest request) {
        return requestService.changeOfferedItem(id, currentUserId, request.offeredItemId());
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
     * Đề xuất hoặc cập nhật lịch hẹn giao nhận (GN-01, GN-02).
     */
    @Operation(summary = "Đề xuất hoặc cập nhật lịch hẹn giao nhận")
    @PostMapping("/api/v1/requests/{id}/schedule")
    public RequestResponse updateSchedule(
            @PathVariable long id, @CurrentUserId Long currentUserId, @Valid @RequestBody ScheduleRequest request) {
        return requestService.updateSchedule(id, currentUserId, request);
    }

    /**
     * Xác nhận lịch hẹn giao nhận và cam kết trách nhiệm (GN-02, GN-03).
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
     * Lấy thông tin liên lạc của đối tác khi giao dịch đã chốt lịch (GN-04).
     */
    @Operation(summary = "Lấy thông tin liên lạc của đối tác khi giao dịch đã chốt lịch")
    @GetMapping("/api/v1/requests/{id}/contact")
    public RequestContactResponse getContact(@PathVariable long id, @CurrentUserId Long currentUserId) {
        return requestService.getContactInfo(id, currentUserId);
    }

    /**
     * Hoàn tất giao nhận từ phía người đang đăng nhập (GN-06, GN-08, UC-27).
     */
    @Operation(summary = "Hoàn tất giao nhận")
    @PostMapping("/api/v1/requests/{id}/confirm-handover")
    public RequestResponse confirmHandover(
            @PathVariable long id,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody(required = false) HandoverConfirmRequest request) {
        return requestService.confirmHandover(id, currentUserId, request);
    }

    /**
     * Giao nhận không thành công: huỷ giao dịch nếu bên kia chưa bấm hoàn tất, mở tranh chấp nếu đã bấm (GN-06, GN-07).
     */
    @Operation(summary = "Báo giao nhận không thành công")
    @PostMapping("/api/v1/requests/{id}/handover-failed")
    public RequestResponse handoverFailed(
            @PathVariable long id,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody HandoverFailedRequest request) {
        return requestService.handoverFailed(id, currentUserId, request);
    }

    /**
     * Từ chối nhận món đồ, mở khiếu nại "Không đúng mô tả" (GN-07, BC-09).
     */
    @Operation(summary = "Từ chối giao nhận")
    @PostMapping("/api/v1/requests/{id}/refuse-handover")
    public RequestResponse refuseHandover(
            @PathVariable long id,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody RefuseHandoverRequest request) {
        return requestService.refuseHandover(id, currentUserId, request);
    }

    /**
     * Đính kèm ảnh hoặc video minh chứng vào yêu cầu giao dịch (GN-10, UC-28).
     */
    @Operation(summary = "Đính kèm ảnh hoặc video minh chứng giao nhận")
    @PostMapping("/api/v1/requests/{id}/media")
    public List<com.shareloop.request.dto.RequestMediaResponse> attachMedia(
            @PathVariable long id,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody com.shareloop.request.dto.AttachMediaRequest request) {
        return requestService.attachMedia(id, currentUserId, request.mediaIds(), request.stage());
    }

    /**
     * Đọc nội dung một ảnh chat hoặc ảnh giao nhận của giao dịch (D2). Chỉ hai bên và Admin; người khác nhận 404.
     */
    @Operation(summary = "Xem ảnh chat hoặc ảnh giao nhận của giao dịch (chỉ hai bên và Admin)")
    @GetMapping("/api/v1/requests/{id}/media/{mediaId}")
    public org.springframework.http.ResponseEntity<byte[]> readMedia(
            @PathVariable long id, @PathVariable long mediaId, @CurrentUserId Long currentUserId) {
        var content = requestService.readMedia(id, mediaId, currentUserId);
        return org.springframework.http.ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(org.springframework.http.MediaType.parseMediaType(content.mimeType()))
                .body(content.bytes());
    }

    /**
     * Lấy danh sách ảnh hoặc video minh chứng của yêu cầu giao dịch (GN-10).
     * Chỉ hai bên tham gia giao dịch mới có quyền xem.
     */
    @Operation(summary = "Lấy danh sách ảnh hoặc video minh chứng giao nhận (chỉ 2 bên tham gia)")
    @GetMapping("/api/v1/requests/{id}/media")
    public List<com.shareloop.request.dto.RequestMediaResponse> getMediaList(
            @PathVariable long id, @CurrentUserId Long currentUserId) {
        return requestService.getMediaList(id, currentUserId);
    }
}
