package com.shareloop.request.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.service.ItemService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.ConfirmLogisticsRequest;
import com.shareloop.request.dto.CreateRequest;
import com.shareloop.request.dto.RequestChatContext;
import com.shareloop.request.dto.RequestContactResponse;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.dto.ScheduleRequest;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.mapper.RequestMapper;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.dto.ContactInfo;
import com.shareloop.user.service.UserService;
import java.time.Instant;
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
    private final ConfigService configService;
    private final ItemService itemService;
    private final UserService userService;
    private final JdbcClient jdbcClient;

    public RequestService(
            RequestRepository requestRepository,
            NotificationService notificationService,
            ConfigService configService,
            ItemService itemService,
            UserService userService,
            JdbcClient jdbcClient) {
        this.requestRepository = requestRepository;
        this.notificationService = notificationService;
        this.configService = configService;
        this.itemService = itemService;
        this.userService = userService;
        this.jdbcClient = jdbcClient;
    }

    /**
     * Gửi yêu cầu xin đồ hoặc đổi đồ tới một bài đăng (F10 bước 1, UC-20).
     */
    @Transactional
    public RequestResponse createRequest(long itemId, long receiverId, CreateRequest requestDto) {
        // 1. Kiểm tra giới hạn số request PENDING cùng lúc (BR-U02, đọc từ ConfigService)
        int maxPending = configService.getInt(ConfigKey.MAX_PENDING_REQUESTS);
        long pendingCount = requestRepository.countByReceiverIdAndStatus(receiverId, RequestStatus.PENDING);
        if (pendingCount >= maxPending) {
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
     * Chủ bài đăng chọn đúng một người nhận (FR-66, BR-D01).
     *
     * <p>Các yêu cầu PENDING khác tự động REJECTED; bài đăng chuyển sang RESERVED qua ItemService.reserve.
     *
     * @param requestId id của yêu cầu được chọn
     * @param currentUserId id của người đang thao tác (phải là chủ bài đăng)
     * @return RequestResponse của yêu cầu sau khi được chọn
     */
    @Transactional
    public RequestResponse selectReceiver(long requestId, long currentUserId) {
        Request selectedRequest = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));

        ItemSnapshot item = findItemSnapshot(selectedRequest.getItemId())
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));

        if (item.donorId() != currentUserId) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }

        if (selectedRequest.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }

        // 1. Chuyển request được chọn sang RESERVED (qua canTransition)
        selectedRequest.select(Instant.now());
        Request saved = requestRepository.save(selectedRequest);

        // 2. Các yêu cầu PENDING khác cho cùng bài đăng tự động REJECTED
        List<Request> otherPendingRequests =
                requestRepository.findByItemIdAndStatus(selectedRequest.getItemId(), RequestStatus.PENDING);
        for (Request other : otherPendingRequests) {
            if (!other.getId().equals(selectedRequest.getId())) {
                other.reject();
                requestRepository.save(other);
                try {
                    notificationService.notify(
                            other.getReceiverId(),
                            "REQUEST_REJECTED",
                            "Yêu cầu không được chọn",
                            "Chủ bài đã chọn người nhận khác cho món đồ này.",
                            "/items/" + selectedRequest.getItemId());
                } catch (Exception ignored) {
                }
            }
        }

        // 3. Giữ chỗ bài đăng qua ItemService.reserve
        itemService.reserve(selectedRequest.getItemId());

        // 4. Bắn thông báo cho người nhận được chọn
        try {
            notificationService.notify(
                    selectedRequest.getReceiverId(),
                    "REQUEST_ACCEPTED",
                    "Yêu cầu được chấp nhận",
                    "Chủ bài đã chọn bạn để trao tặng/trao đổi món đồ.",
                    "/requests/" + selectedRequest.getId());
        } catch (Exception ignored) {
        }

        return RequestMapper.toResponse(saved);
    }

    /**
     * Xem thông tin chi tiết của một yêu cầu giao dịch (F10, UC-26).
     *
     * <p>Chỉ chủ bài đăng (donor) hoặc người nhận (receiver) mới có quyền xem.
     */
    @Transactional(readOnly = true)
    public RequestResponse getDetail(long requestId, long currentUserId) {
        Request request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));

        ItemSnapshot item = findItemSnapshot(request.getItemId())
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));

        if (request.getReceiverId() != currentUserId && item.donorId() != currentUserId) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }

        return RequestMapper.toResponse(request);
    }

    /**
     * Đề xuất hoặc cập nhật lịch hẹn giao nhận (FR-79, FR-80, BR-T01, BR-T02).
     *
     * <p>Chỉ bên tham gia giao dịch mới được đề xuất; chuyển sang AWAITING_LOGISTICS nếu đang RESERVED.
     */
    @Transactional
    public RequestResponse updateSchedule(long requestId, long currentUserId, ScheduleRequest requestDto) {
        Request request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));

        ItemSnapshot item = findItemSnapshot(request.getItemId())
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));

        if (request.getReceiverId() != currentUserId && item.donorId() != currentUserId) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }

        if (requestDto.meetingTime().isBefore(Instant.now())) {
            throw new BusinessException(RequestErrorCode.REQUEST_SCHEDULE_PAST_TIME);
        }

        boolean isDonor = item.donorId() == currentUserId;
        boolean agreeToWaiver = Boolean.TRUE.equals(requestDto.agreeToWaiver());

        request.proposeSchedule(
                requestDto.deliveryMethod(),
                requestDto.meetingPlace(),
                requestDto.meetingTime(),
                isDonor,
                agreeToWaiver,
                Instant.now());

        Request saved = requestRepository.save(request);

        // Bắn thông báo cho bên còn lại
        long otherUserId = isDonor ? request.getReceiverId() : item.donorId();
        try {
            notificationService.notify(
                    otherUserId,
                    "SCHEDULE_UPDATED",
                    "Cập nhật lịch hẹn",
                    "Đối phương đã cập nhật lịch hẹn giao nhận.",
                    "/requests/" + saved.getId());
        } catch (Exception ignored) {
        }

        return RequestMapper.toResponse(saved);
    }

    /**
     * Xác nhận lịch hẹn giao nhận và cam kết trách nhiệm (FR-80, FR-81, BR-T03, BR-T05).
     *
     * <p>Khi cả hai bên đều đã đồng ý, Request chuyển sang LOGISTICS_CONFIRMED và khoá lịch.
     */
    @Transactional
    public RequestResponse confirmLogistics(long requestId, long currentUserId, ConfirmLogisticsRequest requestDto) {
        Request request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));

        ItemSnapshot item = findItemSnapshot(request.getItemId())
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));

        if (request.getReceiverId() != currentUserId && item.donorId() != currentUserId) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }

        boolean isDonor = item.donorId() == currentUserId;
        boolean agreeToWaiver = requestDto != null && Boolean.TRUE.equals(requestDto.agreeToWaiver());

        request.confirmLogistics(isDonor, agreeToWaiver, Instant.now());
        Request saved = requestRepository.save(request);

        // Nếu cả hai đã xác nhận và sang LOGISTICS_CONFIRMED, thông báo cho bên còn lại
        if (saved.getStatus() == RequestStatus.LOGISTICS_CONFIRMED) {
            long otherUserId = isDonor ? request.getReceiverId() : item.donorId();
            try {
                notificationService.notify(
                        otherUserId,
                        "LOGISTICS_CONFIRMED",
                        "Lịch hẹn đã được chốt",
                        "Cả hai bên đã xác nhận lịch hẹn giao nhận thành công.",
                        "/requests/" + saved.getId());
            } catch (Exception ignored) {
            }
        }

        return RequestMapper.toResponse(saved);
    }

    /**
     * Lấy thông tin liên lạc của đối tác khi giao dịch đã chốt lịch (FR-82, BR-T11, BR-T12).
     *
     * <p>Chỉ thành viên trong giao dịch mới được gọi và chỉ trả về khi giao dịch đã ở trạng thái
     * chốt lịch hẹn (contactRevealedAt != null, tức từ LOGISTICS_CONFIRMED trở lên).
     *
     * @param requestId id của yêu cầu giao dịch
     * @param currentUserId id của người đang gọi
     * @return thông tin liên lạc (họ tên, email, số điện thoại) của đối tác
     */
    @Transactional(readOnly = true)
    public RequestContactResponse getContactInfo(long requestId, long currentUserId) {
        Request request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));

        ItemSnapshot item = findItemSnapshot(request.getItemId())
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));

        if (request.getReceiverId() != currentUserId && item.donorId() != currentUserId) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }

        if (request.getContactRevealedAt() == null) {
            throw new BusinessException(RequestErrorCode.REQUEST_CONTACT_NOT_REVEALED);
        }

        long partnerId = (currentUserId == request.getReceiverId()) ? item.donorId() : request.getReceiverId();
        ContactInfo contact = userService.findContact(partnerId);

        return new RequestContactResponse(contact.userId(), contact.fullName(), contact.email(), contact.phone());
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

    /**
     * Lấy thông tin ngữ cảnh yêu cầu giao dịch dùng cho xác thực truy cập và trạng thái chat (FR-68, BR-C03).
     */
    @Transactional(readOnly = true)
    public RequestChatContext getChatContext(long requestId) {
        Request request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));

        ItemSnapshot item = findItemSnapshot(request.getItemId())
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));

        return new RequestChatContext(request.getId(), request.getStatus(), request.getReceiverId(), item.donorId());
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
