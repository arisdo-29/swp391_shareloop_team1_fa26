package com.shareloop.request.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.CreateRequest;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.mapper.RequestMapper;
import com.shareloop.request.repository.RequestRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý yêu cầu giao dịch (Request) và state machine (Lộ trình 4.6, 4.7).
 */
@Service
public class RequestService {

    private static final Set<RequestStatus> ACTIVE_STATUSES = Set.of(
            RequestStatus.PENDING,
            RequestStatus.RESERVED,
            RequestStatus.AWAITING_LOGISTICS,
            RequestStatus.LOGISTICS_CONFIRMED,
            RequestStatus.AWAITING_HANDOVER,
            RequestStatus.DISPUTED);

    private final RequestRepository requestRepository;
    private final NotificationService notificationService;
    private final JdbcClient jdbcClient;

    public RequestService(
            RequestRepository requestRepository, NotificationService notificationService, JdbcClient jdbcClient) {
        this.requestRepository = requestRepository;
        this.notificationService = notificationService;
        this.jdbcClient = jdbcClient;
    }

    /**
     * Gửi yêu cầu xin đồ hoặc đổi đồ tới một bài đăng (F10 bước 1, UC-20).
     */
    @Transactional
    public RequestResponse createRequest(long itemId, long receiverId, CreateRequest requestDto) {
        // 1. Kiểm tra giới hạn 5 request PENDING cùng lúc (BR-U02)
        long pendingCount = requestRepository.countByReceiverIdAndStatus(receiverId, RequestStatus.PENDING);
        if (pendingCount >= 5) {
            throw new BusinessException(RequestErrorCode.REQUEST_LIMIT_EXCEEDED);
        }

        // 2. Kiểm tra không gửi trùng yêu cầu đang hoạt động cho cùng bài (BR-R02)
        if (requestRepository.existsByItemIdAndReceiverIdAndStatusIn(itemId, receiverId, ACTIVE_STATUSES)) {
            throw new BusinessException(RequestErrorCode.REQUEST_ALREADY_EXISTS);
        }

        // 3. Đọc thông tin bài đăng từ database
        ItemSnapshot targetItem = findItemSnapshot(itemId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));

        // Không gửi cho bài của chính mình (BR-R01)
        if (targetItem.donorId() == receiverId) {
            throw new BusinessException(RequestErrorCode.REQUEST_SELF_OFFER_NOT_ALLOWED);
        }

        // Bài phải ở trạng thái APPROVED
        if (!"APPROVED".equals(targetItem.status())) {
            throw new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_AVAILABLE);
        }

        Long offeredItemId = requestDto != null ? requestDto.offeredItemId() : null;

        // Nếu bài là SWAP, bắt buộc phải chọn món đề nghị (BR-S02)
        if ("SWAP".equals(targetItem.offerType())) {
            if (offeredItemId == null) {
                throw new BusinessException(RequestErrorCode.REQUEST_SWAP_OFFERED_ITEM_REQUIRED);
            }
            ItemSnapshot offeredItem = findItemSnapshot(offeredItemId)
                    .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));

            // Món đề nghị phải là của chính người gửi và đang APPROVED (BR-S01, BR-S03)
            if (offeredItem.donorId() != receiverId || !"APPROVED".equals(offeredItem.status())) {
                throw new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_AVAILABLE);
            }
        }

        // 4. Lưu Request mới ở trạng thái PENDING
        Request request = new Request(itemId, receiverId, offeredItemId, targetItem.offerType());
        Request saved = requestRepository.save(request);

        // 5. Gửi thông báo cho chủ bài đăng
        try {
            notificationService.notify(
                    targetItem.donorId(),
                    "NEW_REQUEST",
                    "Có yêu cầu mới",
                    "Một thành viên đã gửi yêu cầu tới bài đăng của bạn.",
                    "/requests/" + saved.getId());
        } catch (UnsupportedOperationException ignored) {
            // Khi NotificationService còn ở dạng stub chưa triển khai
        }

        return RequestMapper.toResponse(saved);
    }

    /**
     * Chủ bài đăng xem danh sách các yêu cầu gửi tới bài của mình (F10 bước 2, UC-22).
     */
    @Transactional(readOnly = true)
    public List<RequestResponse> listByItem(long itemId, long currentUserId) {
        ItemSnapshot item = findItemSnapshot(itemId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));

        if (item.donorId() != currentUserId) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }

        return requestRepository.findByItemIdOrderByCreatedAtDesc(itemId).stream()
                .map(RequestMapper::toResponse)
                .toList();
    }

    /**
     * Người dùng xem danh sách tất cả các yêu cầu do chính mình gửi đi (UC-20).
     */
    @Transactional(readOnly = true)
    public List<RequestResponse> listMyRequests(long currentUserId) {
        return requestRepository.findByReceiverIdOrderByCreatedAtDesc(currentUserId).stream()
                .map(RequestMapper::toResponse)
                .toList();
    }

    /**
     * Người gửi tự huỷ yêu cầu khi còn ở trạng thái PENDING (F10, BR-R04, UC-21).
     */
    @Transactional
    public RequestResponse cancelRequest(long requestId, long currentUserId) {
        Request request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));

        if (!request.getReceiverId().equals(currentUserId)) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }

        request.cancel("Người gửi tự huỷ yêu cầu");
        Request saved = requestRepository.save(request);
        return RequestMapper.toResponse(saved);
    }

    /**
     * Kiểm tra người dùng có đang trong giai đoạn giao nhận hay không (F03, BR-U10).
     */
    @Transactional(readOnly = true)
    public boolean hasActiveDelivery(long userId) {
        return requestRepository.hasActiveDelivery(userId);
    }

    /**
     * Kiểm tra bài đăng đã có yêu cầu ở trạng thái RESERVED trở lên hay chưa (F07 bước 1, BR-D04).
     */
    @Transactional(readOnly = true)
    public boolean hasReservedOrHigherRequest(long itemId) {
        return requestRepository.hasReservedOrHigherRequest(itemId);
    }

    Optional<ItemSnapshot> findItemSnapshot(long itemId) {
        return jdbcClient
                .sql("SELECT donor_id, offer_type, status FROM items WHERE id = :id AND is_deleted = false")
                .param("id", itemId)
                .query((rs, rowNum) ->
                        new ItemSnapshot(rs.getLong("donor_id"), rs.getString("offer_type"), rs.getString("status")))
                .optional();
    }

    record ItemSnapshot(long donorId, String offerType, String status) {}
}
