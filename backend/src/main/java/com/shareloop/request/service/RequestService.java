package com.shareloop.request.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.dto.ItemCardDto;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.media.service.MediaService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.reputation.dto.ReputationSummary;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.ConfirmLogisticsRequest;
import com.shareloop.request.dto.CreateRequest;
import com.shareloop.request.dto.HandoverConfirmRequest;
import com.shareloop.request.dto.RequestChatContext;
import com.shareloop.request.dto.RequestContactResponse;
import com.shareloop.request.dto.RequestItemInfo;
import com.shareloop.request.dto.RequestMediaResponse;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.dto.ScheduleRequest;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestMediaMapping;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.event.NotAsDescribedReportedEvent;
import com.shareloop.request.mapper.RequestMapper;
import com.shareloop.request.repository.RequestMediaRepository;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.dto.ContactInfo;
import com.shareloop.user.service.UserService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ItemQueryService itemQueryService;
    private final UserService userService;
    private final ReputationService reputationService;
    private final JdbcClient jdbcClient;
    private final MediaService mediaService;
    private final RequestMediaRepository requestMediaRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public RequestService(
            RequestRepository requestRepository,
            NotificationService notificationService,
            ConfigService configService,
            ItemService itemService,
            @Autowired(required = false) ItemQueryService itemQueryService,
            UserService userService,
            @Autowired(required = false) ReputationService reputationService,
            @Autowired(required = false) JdbcClient jdbcClient,
            @Autowired(required = false) MediaService mediaService,
            @Autowired(required = false) RequestMediaRepository requestMediaRepository,
            @Autowired(required = false) ApplicationEventPublisher eventPublisher) {
        this.requestRepository = requestRepository;
        this.notificationService = notificationService;
        this.configService = configService;
        this.itemService = itemService;
        this.itemQueryService = itemQueryService;
        this.userService = userService;
        this.reputationService = reputationService;
        this.jdbcClient = jdbcClient;
        this.mediaService = mediaService;
        this.requestMediaRepository = requestMediaRepository;
        this.eventPublisher = eventPublisher;
    }

    public RequestService(
            RequestRepository requestRepository,
            NotificationService notificationService,
            ConfigService configService,
            ItemService itemService,
            @Autowired(required = false) ItemQueryService itemQueryService,
            UserService userService,
            @Autowired(required = false) ReputationService reputationService,
            @Autowired(required = false) JdbcClient jdbcClient) {
        this(
                requestRepository,
                notificationService,
                configService,
                itemService,
                itemQueryService,
                userService,
                reputationService,
                jdbcClient,
                null,
                null,
                null);
    }

    public RequestService(
            RequestRepository requestRepository,
            NotificationService notificationService,
            ConfigService configService,
            ItemService itemService,
            UserService userService,
            JdbcClient jdbcClient) {
        this(
                requestRepository,
                notificationService,
                configService,
                itemService,
                null,
                userService,
                null,
                jdbcClient,
                null,
                null,
                null);
    }

    /**
     * Gửi yêu cầu xin đồ hoặc đổi đồ tới một bài đăng (F10 bước 1, UC-20).
     */
    @Transactional
    public RequestResponse createRequest(long itemId, long receiverId, CreateRequest requestDto) {
        // 0. Vô hiệu quyền gửi yêu cầu khi TrustStars về 0 (FR-92, BR-U05)
        if (reputationService != null && !reputationService.canRequest(receiverId)) {
            throw new BusinessException(RequestErrorCode.REQUEST_REPUTATION_BLOCKED);
        }

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
        notificationService.notify(
                targetItem.donorId(),
                "NEW_REQUEST",
                "Có yêu cầu mới",
                "Một thành viên đã gửi yêu cầu tới bài đăng của bạn.",
                "/requests/" + saved.getId());

        return toResponse(saved);
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

        // Mọi yêu cầu trong danh sách cùng một bài nên tra thẻ bài đăng một lần rồi dùng lại.
        Map<Long, ItemCardDto> itemCards = new HashMap<>();
        return requestRepository.findByItemIdOrderByCreatedAtDesc(itemId).stream()
                .map(request -> {
                    String receiverName = findUserName(request.getReceiverId());
                    ReputationSummary rep = findReceiverReputation(request.getReceiverId());
                    Integer trustStars = rep != null ? rep.trustStars() : 5;
                    String rank = rep != null && rep.rank() != null ? rep.rank().name() : Rank.BRONZE.name();
                    Double asDescribedRate = rep != null ? rep.asDescribedRate() : null;
                    return RequestMapper.toResponse(
                            request, receiverName, trustStars, rank, asDescribedRate, findItemInfo(request, itemCards));
                })
                .toList();
    }

    /**
     * Người dùng xem danh sách tất cả các yêu cầu do chính mình gửi đi (UC-20).
     */
    @Transactional(readOnly = true)
    public List<RequestResponse> listMyRequests(long currentUserId) {
        Map<Long, ItemCardDto> itemCards = new HashMap<>();
        return requestRepository.findByReceiverIdOrderByCreatedAtDesc(currentUserId).stream()
                .map(request -> RequestMapper.toResponse(
                        request,
                        findUserName(request.getReceiverId()),
                        null,
                        null,
                        null,
                        findItemInfo(request, itemCards)))
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

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }

        request.cancel("Người gửi tự huỷ yêu cầu");
        Request saved = requestRepository.save(request);

        // Thông báo cho chủ bài đăng biết yêu cầu đã bị huỷ
        ItemSnapshot item = findItemSnapshot(saved.getItemId()).orElse(null);
        if (item != null) {
            notificationService.notify(
                    item.donorId(),
                    "REQUEST_CANCELLED",
                    "Yêu cầu đã bị huỷ",
                    "Người gửi đã tự huỷ yêu cầu với bài đăng của bạn.",
                    "/items/" + saved.getItemId());
        }

        return toResponse(saved);
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
                notificationService.notify(
                        other.getReceiverId(),
                        "REQUEST_REJECTED",
                        "Yêu cầu không được chọn",
                        "Chủ bài đã chọn người nhận khác cho món đồ này.",
                        "/requests/" + other.getId());
            }
        }

        // 2b. Huỷ các yêu cầu PENDING khác ở bài khác đang dùng cùng món đề nghị (FR-66, BR-S03)
        if (selectedRequest.getOfferedItemId() != null) {
            List<Request> otherSwapRequests = requestRepository.findByOfferedItemIdAndStatus(
                    selectedRequest.getOfferedItemId(), RequestStatus.PENDING);
            for (Request otherSwap : otherSwapRequests) {
                if (!otherSwap.getId().equals(selectedRequest.getId())) {
                    otherSwap.cancel("Món đồ bạn đề nghị đã được đem trao đổi trong một giao dịch khác.");
                    requestRepository.save(otherSwap);
                    notificationService.notify(
                            otherSwap.getReceiverId(),
                            "REQUEST_CANCELLED",
                            "Yêu cầu trao đổi bị huỷ",
                            "Món đồ bạn đề nghị đã được chọn trong một giao dịch khác.",
                            "/requests/" + otherSwap.getId());
                }
            }
        }

        // 3. Giữ chỗ bài đăng qua ItemService.reserve
        itemService.reserve(selectedRequest.getItemId());

        // 4. Bắn thông báo cho người nhận được chọn
        notificationService.notify(
                selectedRequest.getReceiverId(),
                "REQUEST_ACCEPTED",
                "Yêu cầu được chấp nhận",
                "Chủ bài đã chọn bạn để trao tặng/trao đổi món đồ.",
                "/requests/" + selectedRequest.getId());

        return toResponse(saved);
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

        String receiverName = findUserName(request.getReceiverId());
        RequestItemInfo itemInfo = findItemInfo(request, new HashMap<>());
        if (item.donorId() == currentUserId) {
            ReputationSummary rep = findReceiverReputation(request.getReceiverId());
            Integer trustStars = rep != null ? rep.trustStars() : 5;
            String rank = rep != null && rep.rank() != null ? rep.rank().name() : Rank.BRONZE.name();
            Double asDescribedRate = rep != null ? rep.asDescribedRate() : null;
            return RequestMapper.toResponse(request, receiverName, trustStars, rank, asDescribedRate, itemInfo);
        }

        return RequestMapper.toResponse(request, receiverName, null, null, null, itemInfo);
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
        notificationService.notify(
                otherUserId,
                "SCHEDULE_UPDATED",
                "Cập nhật lịch hẹn",
                "Đối phương đã cập nhật lịch hẹn giao nhận.",
                "/requests/" + saved.getId());

        return toResponse(saved);
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
            notificationService.notify(
                    otherUserId,
                    "LOGISTICS_CONFIRMED",
                    "Lịch hẹn đã được chốt",
                    "Cả hai bên đã xác nhận lịch hẹn giao nhận thành công.",
                    "/requests/" + saved.getId());
        }

        return toResponse(saved);
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
     * Xác nhận đã giao hoặc đã nhận đồ và trả lời đúng mô tả (FR-84, FR-85, BR-D02, BR-R05, UC-27).
     *
     * <p>Chỉ chủ bài đăng (donor) hoặc người nhận (receiver) mới có quyền xác nhận.
     * Khi một bên bấm, Request chuyển sang AWAITING_HANDOVER và tiếp tục chờ bên còn lại (BR-D02).
     * Khi cả hai bên đều đã bấm xác nhận:
     * <ul>
     *   <li>Request chuyển sang COMPLETED (ghi nhận completedAt).</li>
     *   <li>Bài đăng chuyển sang TRADED qua ItemService.markTraded (cả 2 bài nếu là SWAP).</li>
     *   <li>Ghi nhận sự kiện hoàn tất giao dịch tới ReputationService.onCompleted.</li>
     *   <li>Gửi thông báo hoàn tất giao dịch tới cả hai bên.</li>
     * </ul>
     *
     * @param requestId id của yêu cầu giao dịch
     * @param currentUserId id của người đang thao tác
     * @param requestDto thông tin xác nhận (bắt buộc asDescribed và lý do nếu không đúng mô tả đối với bên nhận)
     * @return RequestResponse sau khi xác nhận
     */
    @Transactional
    public RequestResponse confirmHandover(long requestId, long currentUserId, HandoverConfirmRequest requestDto) {
        Request request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));

        ItemSnapshot item = findItemSnapshot(request.getItemId())
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));

        boolean isDonor = item.donorId() == currentUserId;
        boolean isReceiver = request.getReceiverId() == currentUserId;

        if (!isDonor && !isReceiver) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }

        Boolean asDescribed = null;
        if (isReceiver) {
            if (requestDto == null || requestDto.asDescribed() == null) {
                throw new BusinessException(RequestErrorCode.REQUEST_AS_DESCRIBED_REQUIRED);
            }
            if (Boolean.FALSE.equals(requestDto.asDescribed())
                    && (requestDto.notAsDescribedReason() == null
                            || requestDto.notAsDescribedReason().isBlank())) {
                throw new BusinessException(RequestErrorCode.REQUEST_NOT_AS_DESCRIBED_REASON_REQUIRED);
            }
            asDescribed = requestDto.asDescribed();
        }

        // Đính kèm ảnh hoặc video minh chứng khi giao nhận nếu có (FR-86, BR-T09)
        if (requestDto != null
                && requestDto.mediaIds() != null
                && !requestDto.mediaIds().isEmpty()) {
            attachMediaInternal(request.getId(), currentUserId, requestDto.mediaIds(), "HANDOVER");
        }

        request.confirmHandover(isDonor, asDescribed, Instant.now());
        Request saved = requestRepository.save(request);

        // Chọn "Không đúng mô tả" thì báo cho module report mở report (BR-R05). Event chỉ được xử lý sau khi
        // giao dịch này commit, nên lỗi mở report không làm mất câu trả lời của người nhận.
        if (Boolean.FALSE.equals(asDescribed) && eventPublisher != null) {
            eventPublisher.publishEvent(new NotAsDescribedReportedEvent(
                    requestId, currentUserId, requestDto.notAsDescribedReason().trim()));
        }

        long otherUserId = isDonor ? request.getReceiverId() : item.donorId();

        if (saved.getStatus() == RequestStatus.COMPLETED) {
            // 1. Chuyển bài đăng sang TRADED (FR-84, #94)
            itemService.markTraded(saved.getItemId());
            if ("SWAP".equalsIgnoreCase(saved.getType()) && saved.getOfferedItemId() != null) {
                itemService.markTraded(saved.getOfferedItemId());
            }

            long targetRequestId = saved.getId() != null ? saved.getId() : requestId;

            // 2. Chữ ký thông báo sang ReputationService (FR-91, FR-94, #94)
            if (reputationService != null) {
                reputationService.onCompleted(targetRequestId);
            }

            // 3. Gửi thông báo COMPLETED cho cả hai bên
            notificationService.notify(
                    currentUserId,
                    "TRANSACTION_COMPLETED",
                    "Giao dịch hoàn tất",
                    "Giao dịch đã được hai bên xác nhận hoàn tất thành công.",
                    "/requests/" + targetRequestId);
            notificationService.notify(
                    otherUserId,
                    "TRANSACTION_COMPLETED",
                    "Giao dịch hoàn tất",
                    "Giao dịch đã được hai bên xác nhận hoàn tất thành công.",
                    "/requests/" + targetRequestId);
        } else if (saved.getStatus() == RequestStatus.AWAITING_HANDOVER) {
            long targetRequestId = saved.getId() != null ? saved.getId() : requestId;
            notificationService.notify(
                    otherUserId,
                    "HANDOVER_CONFIRMED",
                    "Đối phương đã xác nhận giao nhận",
                    "Đối phương đã xác nhận giao nhận món đồ. Vui lòng kiểm tra và xác nhận.",
                    "/requests/" + targetRequestId);
        }

        return toResponse(saved);
    }

    /**
     * Đính kèm ảnh hoặc video minh chứng vào yêu cầu giao dịch (FR-86, BR-T09, UC-28).
     *
     * <p>Chỉ chủ bài đăng hoặc người nhận mới có quyền đính kèm.
     * Sử dụng MediaService.attach để chuyển trạng thái media sang ATTACHED.
     *
     * @param requestId id của yêu cầu giao dịch
     * @param currentUserId id của người đang thao tác
     * @param mediaIds danh sách id của các tệp media đã tải lên
     * @param stage giai đoạn đính kèm ('HANDOVER' hoặc 'DISPUTE')
     * @return danh sách minh chứng của yêu cầu sau khi đính kèm
     */
    @Transactional
    public List<RequestMediaResponse> attachMedia(
            long requestId, long currentUserId, List<Long> mediaIds, String stage) {
        Request request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));

        ItemSnapshot item = findItemSnapshot(request.getItemId())
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));

        if (request.getReceiverId() != currentUserId && item.donorId() != currentUserId) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }

        // Minh chứng giao nhận chỉ được đính kèm ở giai đoạn giao nhận trở đi (FR-86, BR-T09)
        boolean canAttachMedia = request.getStatus() == RequestStatus.LOGISTICS_CONFIRMED
                || request.getStatus() == RequestStatus.AWAITING_HANDOVER
                || request.getStatus() == RequestStatus.COMPLETED
                || request.getStatus() == RequestStatus.DISPUTED;
        if (!canAttachMedia) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }

        if (mediaIds == null || mediaIds.isEmpty()) {
            throw new BusinessException(RequestErrorCode.REQUEST_MEDIA_REQUIRED);
        }

        String effectiveStage = (stage != null && !stage.isBlank()) ? stage.toUpperCase() : "HANDOVER";
        if (!"HANDOVER".equals(effectiveStage) && !"DISPUTE".equals(effectiveStage)) {
            throw new BusinessException(RequestErrorCode.REQUEST_MEDIA_STAGE_INVALID);
        }

        attachMediaInternal(requestId, currentUserId, mediaIds, effectiveStage);

        return getMediaList(requestId, currentUserId);
    }

    /**
     * Lấy danh sách ảnh hoặc video minh chứng của yêu cầu giao dịch (FR-86, BR-T09).
     *
     * <p>Chỉ hai bên tham gia giao dịch (donor hoặc receiver) mới có quyền xem minh chứng.
     *
     * @param requestId id của yêu cầu giao dịch
     * @param currentUserId id của người đang gọi API
     * @return danh sách minh chứng đã đính kèm
     */
    @Transactional(readOnly = true)
    public List<RequestMediaResponse> getMediaList(long requestId, long currentUserId) {
        Request request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));

        ItemSnapshot item = findItemSnapshot(request.getItemId())
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));

        // Chỉ hai bên tham gia giao dịch mới có quyền xem minh chứng (BR-T09)
        if (request.getReceiverId() != currentUserId && item.donorId() != currentUserId) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }

        if (requestMediaRepository != null) {
            return requestMediaRepository.findByRequestIdOrderByCreatedAtAsc(requestId).stream()
                    .map(m -> new RequestMediaResponse(
                            m.getMediaId(),
                            "/api/v1/media/" + m.getMediaId(),
                            m.getStage(),
                            m.getUploadedBy(),
                            m.getCreatedAt()))
                    .toList();
        }

        if (jdbcClient != null) {
            return jdbcClient
                    .sql("SELECT media_id, stage, uploaded_by, created_at FROM request_media_mappings "
                            + "WHERE request_id = :requestId ORDER BY created_at ASC")
                    .param("requestId", requestId)
                    .query((rs, rowNum) -> new RequestMediaResponse(
                            rs.getLong("media_id"),
                            "/api/v1/media/" + rs.getLong("media_id"),
                            rs.getString("stage"),
                            rs.getLong("uploaded_by"),
                            rs.getTimestamp("created_at").toInstant()))
                    .list();
        }

        return List.of();
    }

    private void attachMediaInternal(long requestId, long currentUserId, List<Long> mediaIds, String stage) {
        if (mediaIds == null || mediaIds.isEmpty()) {
            return;
        }

        if (mediaService != null) {
            mediaService.attach(mediaIds, currentUserId);
        }

        String effectiveStage = (stage != null && !stage.isBlank()) ? stage.toUpperCase() : "HANDOVER";
        for (Long mediaId : mediaIds) {
            if (requestMediaRepository != null) {
                if (!requestMediaRepository.existsByRequestIdAndMediaId(requestId, mediaId)) {
                    requestMediaRepository.save(
                            new RequestMediaMapping(requestId, mediaId, effectiveStage, currentUserId));
                }
            } else if (jdbcClient != null) {
                jdbcClient
                        .sql(
                                "INSERT INTO request_media_mappings (request_id, media_id, stage, uploaded_by, created_at) "
                                        + "VALUES (:reqId, :mediaId, :stage, :userId, :now) ON CONFLICT DO NOTHING")
                        .param("reqId", requestId)
                        .param("mediaId", mediaId)
                        .param("stage", effectiveStage)
                        .param("userId", currentUserId)
                        .param("now", Instant.now())
                        .update();
            }
        }
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

        return new RequestChatContext(
                request.getId(),
                request.getStatus(),
                request.getReceiverId(),
                item.donorId(),
                request.getItemId(),
                request.getOfferedItemId());
    }

    // Phản hồi của các thao tác (tạo, huỷ, chọn, chốt lịch...) cũng kèm tên người xin và tên món đồ, để giao diện
    // không bị mất tên khi thay yêu cầu cũ bằng phản hồi mới.
    private RequestResponse toResponse(Request request) {
        return RequestMapper.toResponse(
                request,
                findUserName(request.getReceiverId()),
                null,
                null,
                null,
                findItemInfo(request, new HashMap<>()));
    }

    // Tên món đồ, ảnh đầu tiên và tên chủ bài để giao diện hiện tên thay vì id. Cần vì GET /items/{id} chỉ cho xem
    // bài APPROVED hoặc của chính mình, còn người xin thì vẫn phải thấy tên món đã được chọn (RESERVED trở lên).
    // itemCards giữ thẻ bài đã tra trong cùng một lần gọi để danh sách nhiều yêu cầu không tra lại cùng một bài.
    private RequestItemInfo findItemInfo(Request request, Map<Long, ItemCardDto> itemCards) {
        ItemCardDto card = findItemCard(request.getItemId(), itemCards);
        String offeredItemTitle = findItemTitle(request.getOfferedItemId());
        if (card == null) {
            return new RequestItemInfo(null, null, null, null, offeredItemTitle);
        }
        return new RequestItemInfo(
                card.title(), card.imageUrl(), card.donorId(), findUserName(card.donorId()), offeredItemTitle);
    }

    // Bài đã xoá hoặc service chưa sẵn sàng thì trả null để API vẫn trả được phần còn lại.
    private ItemCardDto findItemCard(long itemId, Map<Long, ItemCardDto> itemCards) {
        if (itemQueryService == null) {
            return null;
        }
        if (itemCards.containsKey(itemId)) {
            return itemCards.get(itemId);
        }
        ItemCardDto card;
        try {
            card = itemQueryService.getItemCard(itemId);
        } catch (BusinessException e) {
            card = null;
        }
        itemCards.put(itemId, card);
        return card;
    }

    private String findItemTitle(Long itemId) {
        if (itemId == null || itemQueryService == null) {
            return null;
        }
        return itemQueryService.findTitles(List.of(itemId)).get(itemId);
    }

    // Chỉ lấy họ tên để hiển thị; email, số điện thoại chỉ trao qua getContactInfo khi đã chốt lịch (BR-T11).
    // Tra tên lỗi thì trả null để danh sách yêu cầu vẫn hiện được, không làm hỏng cả API.
    private String findUserName(long userId) {
        if (userService == null) {
            return null;
        }
        try {
            ContactInfo contact = userService.findContact(userId);
            if (contact == null) {
                return null;
            }
            return contact.fullName();
        } catch (BusinessException e) {
            return null;
        }
    }

    // Lấy thông tin uy tín (TrustStars, hạng, tỉ lệ đúng mô tả) qua ReputationService (FR-65, Issue #110).
    // Tra lỗi hoặc service chưa sẵn sàng thì trả null để danh sách yêu cầu vẫn hiển thị an toàn.
    private ReputationSummary findReceiverReputation(long receiverId) {
        if (reputationService == null) {
            return null;
        }
        try {
            return reputationService.getReputationSummary(receiverId);
        } catch (Exception e) {
            return null;
        }
    }

    Optional<ItemSnapshot> findItemSnapshot(long itemId) {
        if (itemQueryService != null) {
            try {
                ItemSummary summary = itemQueryService.getSummary(itemId);
                if (summary != null) {
                    return Optional.of(new ItemSnapshot(
                            summary.donorId(),
                            summary.offerType() != null ? summary.offerType().name() : null,
                            summary.status() != null ? summary.status().name() : null));
                }
            } catch (BusinessException e) {
                if (e.getErrorCode() == ItemErrorCode.ITEM_NOT_FOUND) {
                    return Optional.empty();
                }
                throw e;
            }
        }
        if (jdbcClient != null) {
            return jdbcClient
                    .sql("SELECT donor_id, offer_type, status FROM items WHERE id = :id AND is_deleted = false")
                    .param("id", itemId)
                    .query((rs, rowNum) -> new ItemSnapshot(
                            rs.getLong("donor_id"), rs.getString("offer_type"), rs.getString("status")))
                    .optional();
        }
        return Optional.empty();
    }

    record ItemSnapshot(long donorId, String offerType, String status) {}
}
