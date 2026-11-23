package com.shareloop.request.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.dto.ItemCardDto;
import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.event.ItemUnavailableEvent;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.media.MediaErrorCode;
import com.shareloop.media.service.MediaService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.report.dto.RequestDisputeSummary;
import com.shareloop.report.service.ReportService;
import com.shareloop.reputation.dto.ReputationSummary;
import com.shareloop.reputation.entity.Rank;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.CancelRequestRequest;
import com.shareloop.request.dto.CancelTransactionRequest;
import com.shareloop.request.dto.ConfirmLogisticsRequest;
import com.shareloop.request.dto.CreateRequest;
import com.shareloop.request.dto.HandoverConfirmRequest;
import com.shareloop.request.dto.HandoverFailedRequest;
import com.shareloop.request.dto.OpenItemTransaction;
import com.shareloop.request.dto.RefuseHandoverRequest;
import com.shareloop.request.dto.RejectCancelRequest;
import com.shareloop.request.dto.RequestChatContext;
import com.shareloop.request.dto.RequestContactResponse;
import com.shareloop.request.dto.RequestItemInfo;
import com.shareloop.request.dto.RequestMediaResponse;
import com.shareloop.request.dto.RequestResponse;
import com.shareloop.request.dto.ScheduleRequest;
import com.shareloop.request.entity.CancelRequestCode;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestMediaMapping;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.event.CancelRequestRejectedEvent;
import com.shareloop.request.event.HandoverIssueReportedEvent;
import com.shareloop.request.event.NotAsDescribedReportedEvent;
import com.shareloop.request.event.RequestNegotiationEndedEvent;
import com.shareloop.request.event.SilentCancelRequestReportedEvent;
import com.shareloop.request.mapper.RequestMapper;
import com.shareloop.request.repository.RequestMediaRepository;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.user.dto.ContactInfo;
import com.shareloop.user.service.UserService;
import com.shareloop.wallet.entity.CreditTxType;
import com.shareloop.wallet.service.CreditService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý yêu cầu giao dịch (Request) và state machine.
 */
@Service
public class RequestService {

    // Giao dịch đang mở của một bài: từ lúc được chọn tới trước khi kết thúc, kể cả đang tranh chấp (F1).
    private static final Set<RequestStatus> OPEN_TRANSACTION_STATUSES = Set.of(
            RequestStatus.RESERVED,
            RequestStatus.AWAITING_LOGISTICS,
            RequestStatus.LOGISTICS_CONFIRMED,
            RequestStatus.AWAITING_HANDOVER,
            RequestStatus.DISPUTED);

    // Giao dịch đang chạy của người nhận, tính vào giới hạn max_active_transactions (C4).
    private static final Set<RequestStatus> RUNNING_STATUSES = Set.of(
            RequestStatus.RESERVED,
            RequestStatus.AWAITING_LOGISTICS,
            RequestStatus.LOGISTICS_CONFIRMED,
            RequestStatus.AWAITING_HANDOVER);

    // Lý do ghi khi người nhận tự huỷ yêu cầu đang chờ; dùng để nhận ra lần tự huỷ khi tính thời gian chờ gửi lại (C5).
    private static final String SELF_CANCEL_REASON = Request.CANCEL_BY_RECEIVER + "Người gửi tự huỷ yêu cầu";

    private static final DateTimeFormatter RESEND_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private static final Set<RequestStatus> ACTIVE_STATUSES = Set.of(
            RequestStatus.PENDING,
            RequestStatus.RESERVED,
            RequestStatus.AWAITING_LOGISTICS,
            RequestStatus.LOGISTICS_CONFIRMED,
            RequestStatus.AWAITING_HANDOVER,
            RequestStatus.DISPUTED);

    // Hai bên chỉ được tự huỷ bằng cancel-transaction khi chưa chốt lịch; từ LOGISTICS_CONFIRMED trở đi dùng
    // "giao nhận không thành công" (handover-failed) để không có hai nút trùng ý.
    private static final Set<RequestStatus> PARTY_CANCEL_STATUSES =
            Set.of(RequestStatus.RESERVED, RequestStatus.AWAITING_LOGISTICS);

    // Các trạng thái mà Admin được huỷ giao dịch đã chọn người
    private static final Set<RequestStatus> CANCELLABLE_TRANSACTION_STATUSES = Set.of(
            RequestStatus.RESERVED,
            RequestStatus.AWAITING_LOGISTICS,
            RequestStatus.LOGISTICS_CONFIRMED,
            RequestStatus.AWAITING_HANDOVER);

    // Giao dịch đang mở bị huỷ khi khoá tài khoản (DISPUTED để Admin phân xử nên không nằm trong danh sách)
    private static final Set<RequestStatus> LOCK_CANCEL_STATUSES = Set.of(
            RequestStatus.PENDING,
            RequestStatus.RESERVED,
            RequestStatus.AWAITING_LOGISTICS,
            RequestStatus.LOGISTICS_CONFIRMED,
            RequestStatus.AWAITING_HANDOVER);
    private static final String LOCKED_ACCOUNT_REASON = "Tài khoản bị khoá";

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
    private final ActivityLogService activityLogService;
    private final ReportService reportService;
    private final CreditService creditService;
    private final ItemReopenedNotifier itemReopenedNotifier;
    private final RequestDeadlineCalculator deadlineCalculator;

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
            @Autowired(required = false) ApplicationEventPublisher eventPublisher,
            @Autowired(required = false) ActivityLogService activityLogService,
            @Autowired(required = false) @Lazy ReportService reportService,
            @Autowired(required = false) CreditService creditService,
            @Autowired(required = false) ItemReopenedNotifier itemReopenedNotifier) {
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
        this.activityLogService = activityLogService;
        this.reportService = reportService;
        this.creditService = creditService;
        this.itemReopenedNotifier = itemReopenedNotifier;
        this.deadlineCalculator = new RequestDeadlineCalculator(configService);
    }

    public RequestService(
            RequestRepository requestRepository,
            NotificationService notificationService,
            ConfigService configService,
            ItemService itemService,
            ItemQueryService itemQueryService,
            UserService userService,
            ReputationService reputationService,
            JdbcClient jdbcClient,
            MediaService mediaService,
            RequestMediaRepository requestMediaRepository,
            ApplicationEventPublisher eventPublisher,
            ActivityLogService activityLogService) {
        this(
                requestRepository,
                notificationService,
                configService,
                itemService,
                itemQueryService,
                userService,
                reputationService,
                jdbcClient,
                mediaService,
                requestMediaRepository,
                eventPublisher,
                activityLogService,
                null,
                null,
                null);
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
                null,
                null,
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
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * Gửi yêu cầu xin đồ hoặc đổi đồ tới một bài đăng (F10 bước 1, UC-20).
     */
    @Transactional
    public RequestResponse createRequest(long itemId, long receiverId, CreateRequest requestDto) {
        // Admin không tham gia giao dịch (TK-13)
        if (userService != null && userService.isAdmin(receiverId)) {
            throw new BusinessException(RequestErrorCode.REQUEST_ADMIN_BLOCKED);
        }

        // 0. Vô hiệu quyền gửi yêu cầu khi TrustStars về 0 (YC-02, UT-04, KC-05) hoặc tài khoản bị khoá
        if (reputationService != null && !reputationService.canRequest(receiverId)) {
            throw new BusinessException(RequestErrorCode.REQUEST_REPUTATION_BLOCKED);
        }
        if (userService != null && userService.isLocked(receiverId)) {
            throw new BusinessException(RequestErrorCode.REQUEST_REPUTATION_BLOCKED);
        }

        // 1. Kiểm tra giới hạn số request PENDING cùng lúc (YC-03, đọc từ ConfigService)
        int maxPending = configService.getInt(ConfigKey.MAX_PENDING_REQUESTS);
        long pendingCount = requestRepository.countByReceiverIdAndStatus(receiverId, RequestStatus.PENDING);
        if (pendingCount >= maxPending) {
            throw new BusinessException(
                    RequestErrorCode.REQUEST_LIMIT_EXCEEDED,
                    "Bạn đã đạt giới hạn tối đa " + maxPending + " yêu cầu đang chờ xử lý.",
                    null,
                    null);
        }

        // 1a. Vừa tự huỷ yêu cầu cho đúng bài này thì phải chờ đủ giờ mới gửi lại (C5)
        requireResendCooldownPassed(itemId, receiverId);

        // 1b. Người nhận đang có quá nhiều giao dịch chưa xong thì chưa gửi thêm (C4)
        long runningCount = countRunningTransactions(receiverId);
        if (runningCount >= configService.getInt(ConfigKey.MAX_ACTIVE_TRANSACTIONS)) {
            throw new BusinessException(
                    RequestErrorCode.REQUEST_TOO_MANY_ACTIVE_TRANSACTIONS,
                    "Bạn đang có " + runningCount + " giao dịch chưa xong. Hoàn tất bớt rồi gửi thêm yêu cầu.",
                    null,
                    Map.of("activeTransactions", runningCount));
        }

        // 2. Kiểm tra không gửi trùng yêu cầu đang hoạt động cho cùng bài (YC-03)
        if (requestRepository.existsByItemIdAndReceiverIdAndStatusIn(itemId, receiverId, ACTIVE_STATUSES)) {
            throw new BusinessException(RequestErrorCode.REQUEST_ALREADY_EXISTS);
        }

        // 3. Đọc thông tin bài đăng từ database
        ItemSnapshot targetItem = findItemSnapshot(itemId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));

        // Không gửi cho bài của chính mình (YC-01)
        if (targetItem.donorId() == receiverId) {
            throw new BusinessException(RequestErrorCode.REQUEST_SELF_OFFER_NOT_ALLOWED);
        }

        // Bài phải ở trạng thái APPROVED và đang hoạt động (không bị ẩn)
        if (!"APPROVED".equals(targetItem.status()) || !targetItem.isActive()) {
            throw new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_AVAILABLE);
        }

        // Danh mục hoặc khu vực của bài bị Admin tắt: bài đã ẩn khỏi Tìm đồ nên không nhận yêu cầu mới (B3).
        if (itemQueryService != null && itemQueryService.hasDisabledCatalog(itemId)) {
            throw new BusinessException(RequestErrorCode.REQUEST_ITEM_CATEGORY_DISABLED);
        }

        // Không gửi yêu cầu tới bài của người dùng đang bị khoá tài khoản
        if (userService != null && userService.isLocked(targetItem.donorId())) {
            throw new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_AVAILABLE);
        }

        Long offeredItemId = requestDto != null ? requestDto.offeredItemId() : null;

        // CD-01: Cho phép gửi kèm món đổi khi xin bài Cho (đề nghị trao đổi),
        // và cho phép không kèm món đổi khi xin bài Trao đổi (xin cho không).
        if (offeredItemId != null) {
            requireValidOfferedItem(offeredItemId, receiverId, itemId);
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

        List<Request> requests = requestRepository.findByItemIdOrderByCreatedAtDesc(itemId);
        return buildOwnerView(requests, currentUserId);
    }

    /**
     * Mọi yêu cầu tới các bài của người gọi trong một lần (G2), có lọc theo trạng thái, mới nhất trước. Thay cho việc gọi
     * {@link #listByItem} từng bài ở trang Tin nhắn.
     *
     * @param statuses chỉ lấy các trạng thái này; null hoặc rỗng là lấy mọi trạng thái
     */
    @Transactional(readOnly = true)
    public List<RequestResponse> listReceivedRequests(long currentUserId, Set<RequestStatus> statuses) {
        List<Long> ownItemIds = itemQueryService.findItemIdsByDonor(currentUserId);
        if (ownItemIds.isEmpty()) {
            return List.of();
        }
        Set<RequestStatus> wanted = statuses == null || statuses.isEmpty() ? Set.of(RequestStatus.values()) : statuses;
        List<Request> requests = new ArrayList<>(requestRepository.findByItemIdInAndStatusIn(ownItemIds, wanted));
        requests.sort(java.util.Comparator.comparing(
                Request::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return buildOwnerView(requests, currentUserId);
    }

    // Dựng danh sách yêu cầu nhìn từ chủ bài. Tên và uy tín người xin tra một lần cho mỗi người (G2), thẻ bài tra một
    // lần cho mỗi bài.
    private List<RequestResponse> buildOwnerView(List<Request> requests, long currentUserId) {
        Map<Long, ItemCardDto> itemCards = new HashMap<>();
        // Một người xin gửi nhiều yêu cầu thì chỉ tra tên và uy tín một lần
        Map<Long, String> receiverNames = new HashMap<>();
        Map<Long, ReputationSummary> receiverReputations = new HashMap<>();
        List<Long> requestIds = requests.stream().map(Request::getId).toList();
        Map<Long, Instant> lastMessageMap = findLastMessageAtMap(requestIds);
        Map<Long, List<RequestDisputeSummary>> disputesMap = findDisputesMap(requestIds, currentUserId);
        Map<Long, RequestDisputeSummary> refusalMap = findRefusalMap(requestIds, currentUserId);
        List<Long> disputedRequestIds = requests.stream()
                .filter(r -> r.getStatus() == RequestStatus.DISPUTED)
                .map(Request::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, Instant> disputeDueMap = findDisputeResponseDueAtMap(disputedRequestIds);
        int complaintHours = complaintWindowHours();
        return requests.stream()
                .map(request -> {
                    String receiverName = receiverNames.computeIfAbsent(request.getReceiverId(), this::findUserName);
                    if (!receiverReputations.containsKey(request.getReceiverId())) {
                        receiverReputations.put(
                                request.getReceiverId(), findReceiverReputation(request.getReceiverId()));
                    }
                    ReputationSummary rep = receiverReputations.get(request.getReceiverId());
                    Integer trustStars = rep != null ? rep.trustStars() : 5;
                    String rank = rep != null && rep.rank() != null ? rep.rank().name() : Rank.BRONZE.name();
                    Instant complaintDeadline = ComplaintWindow.deadline(request.getCompletedAt(), complaintHours);
                    Instant lastMessageAt = request.getId() != null ? lastMessageMap.get(request.getId()) : null;
                    Instant disputeResponseDueAt =
                            (request.getStatus() == RequestStatus.DISPUTED && request.getId() != null)
                                    ? disputeDueMap.get(request.getId())
                                    : null;
                    RequestResponse response = RequestMapper.toResponse(
                            request,
                            receiverName,
                            trustStars,
                            rank,
                            findItemInfo(request, itemCards),
                            complaintDeadline,
                            lastMessageAt,
                            disputeResponseDueAt);
                    return addExtraInfo(request, response, null)
                            .withReports(disputesOf(disputesMap, request), refusalOf(refusalMap, request))
                            .withFlags(flagsOf(request));
                })
                .toList();
    }

    /**
     * Người dùng xem danh sách tất cả các yêu cầu do chính mình gửi đi (UC-20).
     */
    @Transactional(readOnly = true)
    public List<RequestResponse> listMyRequests(long currentUserId) {
        Map<Long, ItemCardDto> itemCards = new HashMap<>();
        List<Request> requests = requestRepository.findByReceiverIdOrderByCreatedAtDesc(currentUserId);
        List<Long> requestIds = requests.stream()
                .map(Request::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, Instant> lastMessageMap = findLastMessageAtMap(requestIds);
        Map<Long, List<RequestDisputeSummary>> disputesMap = findDisputesMap(requestIds, currentUserId);
        Map<Long, RequestDisputeSummary> refusalMap = findRefusalMap(requestIds, currentUserId);
        List<Long> disputedRequestIds = requests.stream()
                .filter(r -> r.getStatus() == RequestStatus.DISPUTED)
                .map(Request::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, Instant> disputeDueMap = findDisputeResponseDueAtMap(disputedRequestIds);
        int complaintHours = complaintWindowHours();
        return requests.stream()
                .map(request -> {
                    Instant complaintDeadline = ComplaintWindow.deadline(request.getCompletedAt(), complaintHours);
                    Instant lastMessageAt = request.getId() != null ? lastMessageMap.get(request.getId()) : null;
                    Instant disputeResponseDueAt =
                            (request.getStatus() == RequestStatus.DISPUTED && request.getId() != null)
                                    ? disputeDueMap.get(request.getId())
                                    : null;
                    RequestResponse response = RequestMapper.toResponse(
                            request,
                            findUserName(request.getReceiverId()),
                            null,
                            null,
                            findItemInfo(request, itemCards),
                            complaintDeadline,
                            lastMessageAt,
                            disputeResponseDueAt);
                    return addExtraInfo(request, response, null)
                            .withReports(disputesOf(disputesMap, request), refusalOf(refusalMap, request))
                            .withFlags(flagsOf(request));
                })
                .toList();
    }

    /**
     * Người gửi tự huỷ yêu cầu khi còn ở trạng thái PENDING (F10, YC-05, UC-21).
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

        // Huỷ ngay sau khi gửi (còn trong request_quiet_cancel_minutes): chủ bài không cần biết (C5)
        boolean quietCancel = isQuietCancel(request);
        request.cancel(SELF_CANCEL_REASON);
        Request saved = requestRepository.save(request);

        ItemSnapshot item = findItemSnapshot(saved.getItemId()).orElse(null);
        if (quietCancel && item != null) {
            // Không xoá thông báo "Có yêu cầu mới", chỉ đánh dấu đã đọc để chủ bài không thấy số chưa đọc vô nghĩa.
            notificationService.markAsReadByTypeAndLink(item.donorId(), "NEW_REQUEST", "/requests/" + saved.getId());
        } else if (item != null) {
            // Thông báo cho chủ bài đăng biết yêu cầu đã bị huỷ
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
     * Chủ bài đăng chọn đúng một người nhận (YC-07).
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

        // Khoá bài và món đổi trước mọi kiểm tra và ghi (C3): thao tác cùng lúc chạy lần lượt, luồng sau thấy dữ liệu
        // mới.
        itemService.lockForSelection(selectedRequest.getItemId(), selectedRequest.getOfferedItemId());

        ItemSnapshot item = findItemSnapshot(selectedRequest.getItemId())
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));

        if (item.donorId() != currentUserId) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }

        if (userService != null && userService.isLocked(currentUserId)) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }

        if (!item.isActive()) {
            throw new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_AVAILABLE);
        }

        if (userService != null && userService.isLocked(selectedRequest.getReceiverId())) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }

        if (selectedRequest.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }

        // Người nhận đang có quá nhiều giao dịch chưa xong: báo chủ bài để chọn người khác (C4).
        if (isReceiverTooBusy(selectedRequest.getReceiverId())) {
            throw new BusinessException(RequestErrorCode.REQUEST_RECEIVER_TOO_BUSY);
        }

        // Bài vừa sửa đang chờ duyệt lại: chưa chọn được người, báo rõ thay vì lỗi giữ chỗ chung chung.
        if ("PENDING_REVIEW".equals(item.status())) {
            throw new BusinessException(RequestErrorCode.REQUEST_ITEM_UNDER_REVIEW);
        }

        // Kiểm món đem đổi trước khi đổi hình thức và trừ phí, để món hỏng thì không trừ Credit của chủ bài.
        if (selectedRequest.getOfferedItemId() != null) {
            requireOfferedItemAvailable(selectedRequest.getOfferedItemId(), selectedRequest.getReceiverId());
        }

        // Chuyển đổi loại Cho ↔ Trao đổi khi chọn người nhận (CD-02, CD-04, CD-06)
        boolean isGiveToSwap = "GIVE".equals(item.offerType()) && selectedRequest.getOfferedItemId() != null;
        boolean isSwapToGive = "SWAP".equals(item.offerType()) && selectedRequest.getOfferedItemId() == null;

        if (isGiveToSwap) {
            convertGiveToSwap(selectedRequest, currentUserId);
        } else if (isSwapToGive) {
            // CD-04: Chuyển sang GIVE, không hoàn lại phí đăng bài đã nộp trước đó.
            itemService.convertOfferType(selectedRequest.getItemId(), OfferType.GIVE);
            selectedRequest.convertType("GIVE");
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

        // 2b. Huỷ các yêu cầu PENDING khác ở bài khác đang dùng cùng món đề nghị (YC-07, YC-04)
        cancelOtherPendingUsingOfferedItem(selectedRequest);

        // 3. Giữ chỗ bài đăng qua ItemService.reserve. Món mang đổi cũng bị giữ trong cùng transaction: nếu nó không
        // còn APPROVED thì reserve ném lỗi và cả lần chọn người nhận được hoàn tác.
        itemService.reserve(selectedRequest.getItemId());
        if (selectedRequest.getOfferedItemId() != null) {
            itemService.reserve(selectedRequest.getOfferedItemId());
        }

        // 4. Bắn thông báo cho người nhận được chọn
        notificationService.notify(
                selectedRequest.getReceiverId(),
                "REQUEST_ACCEPTED",
                "Yêu cầu được chấp nhận",
                "Chủ bài đã chọn bạn để trao tặng/trao đổi món đồ.",
                "/requests/" + selectedRequest.getId());

        return toResponse(saved);
    }

    /** Người gửi yêu cầu thay món đem đổi trước khi hai bên chốt lịch. */
    @Transactional
    public RequestResponse changeOfferedItem(long requestId, long currentUserId, long offeredItemId) {
        Request request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));
        if (request.getReceiverId() != currentUserId) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }
        if (request.getOfferedItemId() == null
                || (request.getStatus() != RequestStatus.PENDING
                        && request.getStatus() != RequestStatus.RESERVED
                        && request.getStatus() != RequestStatus.AWAITING_LOGISTICS)) {
            throw new BusinessException(RequestErrorCode.REQUEST_OFFERED_ITEM_CHANGE_LOCKED);
        }
        if (request.getOfferedItemId() == offeredItemId) {
            return toResponse(request);
        }
        requireValidOfferedItem(offeredItemId, currentUserId, request.getItemId());
        if (request.getStatus() != RequestStatus.PENDING) {
            itemService.reserve(offeredItemId);
            itemService.releaseIfReserved(request.getOfferedItemId());
        }
        request.changeOfferedItem(offeredItemId);
        Request saved = requestRepository.save(request);
        if (request.getStatus() != RequestStatus.PENDING) {
            cancelOtherPendingUsingOfferedItem(saved);
        }
        ItemSnapshot item = findItemSnapshot(request.getItemId())
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));
        // Chủ bài chọn người một phần vì món đem đổi, nên khi món đổi sau lúc đã chọn phải nói rõ quyền huỷ (HG-01).
        String body = request.getStatus() == RequestStatus.PENDING
                ? "Người gửi yêu cầu đã chọn món khác để trao đổi. Xem lại món mới trước khi chọn người."
                : "Người nhận đã thay món đem đổi. Lịch hẹn cần hai bên xác nhận lại. Nếu không đồng ý với món mới,"
                        + " bạn có thể huỷ giao dịch trước khi chốt lịch mà không bị trừ sao.";
        notificationService.notify(
                item.donorId(), "OFFERED_ITEM_CHANGED", "Món đề nghị đã thay đổi", body, "/requests/" + saved.getId());
        return toResponse(saved);
    }

    /**
     * Chủ bài đồng ý đề nghị đưa món của người nhận vào đổi trong chat: giao dịch Cho chuyển thành Trao đổi (CD-02,
     * CD-06). Chủ bài trả POST_FEE, bài chính chuyển SWAP, món đề nghị được giữ chỗ; mọi bước nằm trong một transaction
     * nên lỗi ở bước nào (kể cả giữ chỗ món) cũng hoàn tác cả tiền đã trừ. Không kiểm hạn mức bài theo hạng, giống
     * selectReceiver. Trạng thái đề nghị và tin nhắn hệ thống do module chat ghi.
     *
     * @throws BusinessException REQUEST_FORBIDDEN nếu không phải chủ bài, REQUEST_OFFER_CHANGE_NOT_ALLOWED nếu giao
     *     dịch không còn là Cho hoặc đã qua giai đoạn chốt lịch, REQUEST_ITEM_NOT_AVAILABLE nếu món đề nghị không còn
     *     hợp lệ, WALLET_INSUFFICIENT_BALANCE nếu chủ bài thiếu Credit
     */
    @Transactional
    public RequestResponse acceptOfferedItemInChat(long requestId, long donorId, long offeredItemId) {
        Request request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));
        ItemSnapshot item = findItemSnapshot(request.getItemId())
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));
        if (item.donorId() != donorId) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }

        // Kiểm lại lúc đồng ý vì từ lúc gửi đề nghị tới giờ giao dịch hoặc món đề nghị có thể đã đổi.
        requireOfferChangeAllowed(request);
        requireValidOfferedItem(offeredItemId, request.getReceiverId(), request.getItemId());

        convertGiveToSwap(request, donorId);
        request.attachOfferedItem(offeredItemId);
        Request saved = requestRepository.save(request);

        // Món đã giữ ở giao dịch khác thì reserve ném lỗi và cả transaction hoàn tác, kể cả phí vừa trừ.
        itemService.reserve(offeredItemId);
        cancelOtherPendingUsingOfferedItem(saved);

        notificationService.notify(
                saved.getReceiverId(),
                "OFFER_CHANGE_ACCEPTED",
                "Chủ bài đã đồng ý trao đổi",
                "Giao dịch của bạn đã chuyển sang Trao đổi với món bạn đề nghị.",
                "/requests/" + saved.getId());

        return toResponse(saved);
    }

    /**
     * Kiểm giao dịch và món trước khi người nhận gửi đề nghị đưa món vào đổi trong chat: giao dịch phải là Cho, đang
     * RESERVED hoặc AWAITING_LOGISTICS, và món đề nghị hợp lệ như CD-01. Quyền người gửi do module chat kiểm.
     *
     * @throws BusinessException REQUEST_OFFER_CHANGE_NOT_ALLOWED, REQUEST_ITEM_NOT_FOUND, REQUEST_ITEM_NOT_AVAILABLE
     *     hoặc REQUEST_SELF_OFFER_NOT_ALLOWED
     */
    @Transactional(readOnly = true)
    public void validateOfferChangeProposal(long requestId, long offeredItemId) {
        Request request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));
        requireOfferChangeAllowed(request);
        requireValidOfferedItem(offeredItemId, request.getReceiverId(), request.getItemId());
    }

    // Chỉ giao dịch Cho đang ở RESERVED hoặc AWAITING_LOGISTICS mới chuyển được: sau khi chốt lịch thì hai bên đã thống
    // nhất món giao nhận, đổi nữa sẽ lệch với lịch hẹn.
    private void requireOfferChangeAllowed(Request request) {
        boolean isGive = "GIVE".equals(request.getType());
        boolean isNegotiating = request.getStatus() == RequestStatus.RESERVED
                || request.getStatus() == RequestStatus.AWAITING_LOGISTICS;
        if (!isGive || !isNegotiating) {
            throw new BusinessException(RequestErrorCode.REQUEST_OFFER_CHANGE_NOT_ALLOWED);
        }
    }

    // Món đề nghị đổi phải khác bài chính, là của chính người nhận, đang APPROVED và đang hoạt động (YC-04,
    // CD-01). Bài đã xoá mềm thì findItemSnapshot không thấy nên ném REQUEST_ITEM_NOT_FOUND.
    // Dùng chung cho createRequest và đề nghị trong chat để hai nơi không lệch điều kiện.
    void requireValidOfferedItem(long offeredItemId, long receiverId, long itemId) {
        if (offeredItemId == itemId) {
            throw new BusinessException(RequestErrorCode.REQUEST_SELF_OFFER_NOT_ALLOWED);
        }
        ItemSnapshot offeredItem = findItemSnapshot(offeredItemId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));
        if (offeredItem.donorId() != receiverId
                || !"APPROVED".equals(offeredItem.status())
                || !offeredItem.isActive()) {
            throw new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_AVAILABLE);
        }
    }

    // Lúc chọn người, món đem đổi phải vẫn là của người gửi, APPROVED và đang hiển thị. Khác requireValidOfferedItem ở
    // mã lỗi: chủ bài cần biết lỗi nằm ở món của người kia chứ không phải bài của mình.
    // Cờ cảnh báo trên thẻ yêu cầu của chủ bài, để biết trước khi bấm chọn (C1). Chỉ yêu cầu đang chờ mới có cờ.
    private List<String> flagsOf(Request request) {
        List<String> flags = new ArrayList<>();
        if (request.getStatus() != RequestStatus.PENDING) {
            return flags;
        }
        if (request.getOfferedItemId() != null) {
            boolean offeredItemBusy = findItemSnapshot(request.getOfferedItemId())
                    .map(offered -> "RESERVED".equals(offered.status()))
                    .orElse(false);
            if (offeredItemBusy) {
                flags.add("OFFERED_ITEM_BUSY");
            }
        }
        if (isReceiverTooBusy(request.getReceiverId())) {
            flags.add("RECEIVER_TOO_BUSY");
        }
        return flags;
    }

    // Tự huỷ trong vòng request_quiet_cancel_minutes sau khi gửi thì coi như gửi nhầm (C5).
    private boolean isQuietCancel(Request request) {
        if (request.getCreatedAt() == null) {
            return false;
        }
        long quietMinutes = configService.getInt(ConfigKey.REQUEST_QUIET_CANCEL_MINUTES);
        return Instant.now().isBefore(request.getCreatedAt().plus(quietMinutes, ChronoUnit.MINUTES));
    }

    // Chỉ lần tự huỷ của người nhận mới bắt chờ. Huỷ do hệ thống (bài hết hạn, bị ẩn, khoá tài khoản) có lý do khác nên
    // không khớp và không tính thời gian chờ (C5).
    private void requireResendCooldownPassed(long itemId, long receiverId) {
        Optional<Request> lastSelfCancel =
                requestRepository.findFirstByItemIdAndReceiverIdAndStatusAndCancelReasonOrderByUpdatedAtDesc(
                        itemId, receiverId, RequestStatus.CANCELLED, SELF_CANCEL_REASON);
        if (lastSelfCancel.isEmpty() || lastSelfCancel.get().getUpdatedAt() == null) {
            return;
        }
        long cooldownHours = configService.getInt(ConfigKey.REQUEST_RESEND_COOLDOWN_HOURS);
        Instant canResendAt = lastSelfCancel.get().getUpdatedAt().plus(cooldownHours, ChronoUnit.HOURS);
        if (Instant.now().isBefore(canResendAt)) {
            throw new BusinessException(
                    RequestErrorCode.REQUEST_RESEND_COOLDOWN,
                    "Bạn vừa huỷ yêu cầu cho bài này. Có thể gửi lại sau " + RESEND_TIME_FORMAT.format(canResendAt)
                            + ".",
                    null,
                    Map.of("canResendAt", canResendAt.toString()));
        }
    }

    /**
     * Giao dịch đang mở của một bài, dù bài là bài chính hay món mang đổi (F1). Module report dùng khi bài bị báo cáo vi phạm.
     */
    @Transactional(readOnly = true)
    public Optional<OpenItemTransaction> findOpenTransactionOfItem(long itemId) {
        List<Request> asMainItem =
                requestRepository.findByItemIdInAndStatusIn(List.of(itemId), OPEN_TRANSACTION_STATUSES);
        if (!asMainItem.isEmpty()) {
            Request request = asMainItem.get(0);
            return Optional.of(new OpenItemTransaction(request.getId(), request.getReceiverId()));
        }
        List<Request> asOfferedItem =
                requestRepository.findByOfferedItemIdAndStatusIn(itemId, OPEN_TRANSACTION_STATUSES);
        if (asOfferedItem.isEmpty()) {
            return Optional.empty();
        }
        Request request = asOfferedItem.get(0);
        Long mainDonorId =
                findItemSnapshot(request.getItemId()).map(ItemSnapshot::donorId).orElse(null);
        return Optional.of(new OpenItemTransaction(request.getId(), mainDonorId));
    }

    /**
     * Báo hai bên bài trong giao dịch đang bị xem xét vi phạm nhưng giao dịch vẫn tiếp tục (F1). Dùng khi bài tự ẩn do đủ
     * báo cáo hoặc báo cáo được chấp thuận với lý do không thuộc nhóm nghiêm trọng.
     */
    @Transactional
    public void notifyItemUnderReview(long requestId) {
        Request request = requestRepository.findById(requestId).orElse(null);
        if (request == null || !OPEN_TRANSACTION_STATUSES.contains(request.getStatus())) {
            return;
        }
        String link = "/requests/" + request.getId();
        String message =
                "Món đồ trong giao dịch của bạn đang bị xem xét vi phạm. Giao dịch vẫn tiếp tục, bạn nên cẩn thận khi bàn giao.";
        notificationService.notify(
                request.getReceiverId(), "TRANSACTION_ITEM_UNDER_REVIEW", "Bài đang bị xem xét vi phạm", message, link);
        findItemSnapshot(request.getItemId())
                .ifPresent(item -> notificationService.notify(
                        item.donorId(), "TRANSACTION_ITEM_UNDER_REVIEW", "Bài đang bị xem xét vi phạm", message, link));
    }

    // Bài trong giao dịch đang mở bị ẩn (báo cáo vi phạm): trang Tiến trình hiện cảnh báo (F1).
    private List<String> detailFlagsOf(Request request, long itemId) {
        boolean open = OPEN_TRANSACTION_STATUSES.contains(request.getStatus());
        boolean itemHidden =
                findItemSnapshot(itemId).map(item -> !item.isActive()).orElse(false);
        return open && itemHidden ? List.of("ITEM_UNDER_REVIEW") : List.of();
    }

    /** Số giao dịch hoàn tất của một người, ở vai chủ bài hoặc người nhận (F2: độ tin cậy của người báo cáo). */
    @Transactional(readOnly = true)
    public long countCompletedTransactions(long userId) {
        if (jdbcClient == null) {
            return 0;
        }
        return jdbcClient
                .sql(
                        "SELECT count(*) FROM requests r JOIN items i ON i.id = r.item_id"
                                + " WHERE r.status = 'COMPLETED' AND r.is_deleted = false AND (r.receiver_id = :userId OR i.donor_id = :userId)")
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    private long countRunningTransactions(long receiverId) {
        return requestRepository.countByReceiverIdAndStatusIn(receiverId, RUNNING_STATUSES);
    }

    private boolean isReceiverTooBusy(long receiverId) {
        return countRunningTransactions(receiverId) >= configService.getInt(ConfigKey.MAX_ACTIVE_TRANSACTIONS);
    }

    private void requireOfferedItemAvailable(long offeredItemId, long receiverId) {
        ItemSnapshot offeredItem = findItemSnapshot(offeredItemId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_OFFERED_ITEM_UNAVAILABLE));
        // Món đang nằm trong giao dịch khác thì báo đúng nguyên nhân để chủ bài biết phải chờ hoặc chọn người khác
        // (C1).
        if (offeredItem.donorId() == receiverId && "RESERVED".equals(offeredItem.status())) {
            throw new BusinessException(RequestErrorCode.REQUEST_OFFERED_ITEM_IN_OTHER_TRANSACTION);
        }
        boolean available = offeredItem.donorId() == receiverId
                && "APPROVED".equals(offeredItem.status())
                && offeredItem.isActive();
        if (!available) {
            throw new BusinessException(RequestErrorCode.REQUEST_OFFERED_ITEM_UNAVAILABLE);
        }
    }

    // Cho → Trao đổi (CD-02, CD-06): chủ bài trả POST_FEE như khi đăng bài Trao đổi, rồi đổi loại bài và loại giao
    // dịch.
    // Trừ tiền trước để thiếu Credit thì ném WALLET_INSUFFICIENT_BALANCE khi chưa đổi gì.
    private void convertGiveToSwap(Request request, long donorId) {
        if (creditService != null) {
            int postFee = configService.getInt(ConfigKey.POST_FEE);
            creditService.spend(donorId, postFee, CreditTxType.POST_FEE, request.getItemId());
        }
        itemService.convertOfferType(request.getItemId(), OfferType.SWAP);
        request.convertType("SWAP");
    }

    // Báo cho module chat tự huỷ đề nghị đổi món còn PENDING khi giao dịch đã chốt lịch hoặc bị huỷ. Gọi cả khi giao
    // dịch đã qua giai đoạn thương lượng từ trước cũng không sao: lúc đó không còn đề nghị PENDING nào để huỷ.
    // eventPublisher null hoặc id null chỉ gặp ở unit test dựng service rút gọn hoặc entity chưa lưu.
    private void publishNegotiationEnded(Request request) {
        if (eventPublisher != null && request.getId() != null) {
            eventPublisher.publishEvent(new RequestNegotiationEndedEvent(request.getId()));
        }
    }

    // Món đem đổi đã được giữ cho giao dịch này nên các yêu cầu PENDING khác dùng cùng món phải huỷ (YC-07, YC-04).
    private void cancelOtherPendingUsingOfferedItem(Request request) {
        if (request.getOfferedItemId() == null) {
            return;
        }
        List<Request> otherSwapRequests =
                requestRepository.findByOfferedItemIdAndStatus(request.getOfferedItemId(), RequestStatus.PENDING);
        for (Request otherSwap : otherSwapRequests) {
            if (otherSwap.getId().equals(request.getId())) {
                continue;
            }
            otherSwap.cancel(
                    Request.CANCEL_BY_SYSTEM + "Món đồ bạn đề nghị đã được đem trao đổi trong một giao dịch khác.");
            requestRepository.save(otherSwap);
            notificationService.notify(
                    otherSwap.getReceiverId(),
                    "REQUEST_CANCELLED",
                    "Yêu cầu trao đổi bị huỷ",
                    "Món đồ bạn đề nghị đã được chọn trong một giao dịch khác.",
                    "/requests/" + otherSwap.getId());
            notifyTargetDonorOfferedItemTaken(otherSwap);
        }
    }

    // Chủ bài đích cũng cần biết yêu cầu tới bài mình đã biến mất, giống đường huỷ qua ItemUnavailableEvent.
    // Không tra được tên người gửi hoặc tiêu đề bài thì dùng chữ chung để thông báo vẫn đọc được.
    private void notifyTargetDonorOfferedItemTaken(Request otherSwap) {
        Optional<ItemSnapshot> targetItem = findItemSnapshot(otherSwap.getItemId());
        if (targetItem.isEmpty()) {
            return;
        }
        String senderName = findUserName(otherSwap.getReceiverId());
        String itemTitle = findItemTitle(otherSwap.getItemId());
        String senderText = senderName != null ? senderName : "một người dùng";
        String itemText = itemTitle != null ? "bài «" + itemTitle + "»" : "bài của bạn";
        String message = "Yêu cầu của " + senderText + " cho " + itemText
                + " đã tự huỷ vì món đề nghị đổi đã được chọn ở một giao dịch khác.";
        notificationService.notify(
                targetItem.get().donorId(),
                "REQUEST_CANCELLED",
                "Một yêu cầu đã tự huỷ",
                message,
                "/requests/" + otherSwap.getId());
    }

    /**
     * Chủ bài hoặc người nhận huỷ giao dịch sau khi đã chọn người, khi còn ở RESERVED hoặc AWAITING_LOGISTICS. Bài
     * chính và món mang đổi được nhả về APPROVED để mở lại cho người khác; phí đăng không bị tính lại vì bài không qua
     * duyệt lần nữa. Từ LOGISTICS_CONFIRMED trở đi hai bên dùng {@link #handoverFailed} thay cho hàm này.
     *
     * @param requestId id của giao dịch
     * @param currentUserId id người huỷ (phải là chủ bài hoặc người nhận)
     * @param requestDto lý do huỷ, 5 đến 500 ký tự
     * @return RequestResponse sau khi huỷ
     * @throws BusinessException REQUEST_NOT_FOUND, REQUEST_FORBIDDEN khi không phải một trong hai bên,
     *     REQUEST_INVALID_STATE_TRANSITION khi trạng thái không cho huỷ
     */
    @Transactional
    public RequestResponse cancelTransaction(long requestId, long currentUserId, CancelTransactionRequest requestDto) {
        Request request = loadRequest(requestId);
        ItemSnapshot item = loadItemSnapshot(request);
        boolean isDonor = requireParty(request, item, currentUserId);
        // DISPUTED vẫn có cạnh sang CANCELLED nhưng chỉ Admin phân xử mới được đi, nên chặn riêng ở đây
        if (!PARTY_CANCEL_STATUSES.contains(request.getStatus())) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }

        Request saved = cancelByParty(
                request, item, currentUserId, isDonor, requestDto.reason().trim());
        return toResponse(saved);
    }

    /**
     * Một bên báo giao nhận không thành công khi giao dịch đã chốt lịch (GN-06, GN-07).
     *
     * <p>Bên kia chưa bấm hoàn tất: giao dịch chưa huỷ, chỉ tạo đề nghị huỷ để bên kia đồng ý, không đồng ý hoặc im
     * lặng (xem {@link #acceptCancelRequest}). Trước giờ hẹn thì không dùng được, hai bên dùng "Đề nghị huỷ". Bên kia
     * đã bấm hoàn tất: giao dịch sang DISPUTED và mở khiếu nại NO_SHOW qua event cho module report; giao dịch DISPUTED
     * không bị job tự hoàn tất vì job chỉ xử lý AWAITING_HANDOVER.
     *
     * @throws BusinessException REQUEST_NOT_FOUND, REQUEST_FORBIDDEN khi không phải một trong hai bên,
     *     REQUEST_INVALID_STATE_TRANSITION khi sai trạng thái hoặc người báo đã bấm hoàn tất,
     *     REQUEST_BEFORE_MEETING_TIME khi chưa tới giờ hẹn, REQUEST_CANCEL_REASON_INVALID khi mã lý do không hợp lệ
     */
    @Transactional
    public RequestResponse handoverFailed(long requestId, long currentUserId, HandoverFailedRequest requestDto) {
        Request request = loadRequestForUpdate(requestId);
        ItemSnapshot item = loadItemSnapshot(request);
        boolean isDonor = requireParty(request, item, currentUserId);
        if (!request.isHandoverOpen() || request.hasConfirmedHandover(isDonor)) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }

        CancelRequestCode code = requestDto.code() != null ? requestDto.code() : CancelRequestCode.OTHER;
        String typedReason =
                requestDto.reason() == null ? "" : requestDto.reason().trim();
        if (!request.hasOtherPartyConfirmedHandover(isDonor)) {
            return toResponse(startCancelRequestAfterMeeting(
                    request, item, currentUserId, isDonor, code, typedReason, requestDto.evidenceMediaIds()));
        }

        // Bên kia đã bấm hoàn tất: giữ nhánh cũ, khiếu nại ngay. Không mã lý do thì nội dung là đúng chữ người nhập.
        String reason =
                requestDto.code() == null ? requireTypedReason(typedReason) : describeFailure(code, typedReason);
        request.openHandoverIssue(isDonor);
        Request saved = requestRepository.save(request);
        // Listener của module report chạy ngay trong transaction này để report và DISPUTED cùng thành công hoặc cùng
        // rollback.
        eventPublisher.publishEvent(
                new HandoverIssueReportedEvent(requestId, currentUserId, reason, requestDto.evidenceMediaIds()));
        return withReports(toResponse(saved), requestId, currentUserId);
    }

    /**
     * Bên nhận món đồ từ chối nhận vì món không đúng mô tả (GN-07, BC-09). Việc trao đồ không xảy ra nên giao dịch huỷ
     * ngay: món bị từ chối tạm ẩn, món của người từ chối mở lại, và module report mở báo cáo vi phạm nhắm vào chủ món
     * bị từ chối để Admin xem xét. Cho tặng chỉ người nhận được từ chối; Trao đổi cả hai bên đều nhận một món nên cả
     * hai được từ chối.
     *
     * @throws BusinessException REQUEST_NOT_FOUND, REQUEST_FORBIDDEN khi không phải một trong hai bên,
     *     REQUEST_REFUSE_NOT_ALLOWED khi chủ bài ở giao dịch Cho tặng, REQUEST_INVALID_STATE_TRANSITION khi sai trạng
     *     thái hoặc người từ chối đã bấm hoàn tất
     */
    @Transactional
    public RequestResponse refuseHandover(long requestId, long currentUserId, RefuseHandoverRequest requestDto) {
        Request request = loadRequest(requestId);
        ItemSnapshot item = loadItemSnapshot(request);
        boolean isDonor = requireParty(request, item, currentUserId);
        boolean isSwap = request.getOfferedItemId() != null;
        if (isDonor && !isSwap) {
            throw new BusinessException(RequestErrorCode.REQUEST_REFUSE_NOT_ALLOWED);
        }
        // GN-07: chưa tới giờ hẹn thì chưa ai nhận món nào để mà từ chối
        if (request.isBeforeMeetingTime(Instant.now())) {
            throw new BusinessException(RequestErrorCode.REQUEST_BEFORE_MEETING_TIME);
        }

        String reason = requestDto.reason().trim();
        request.refuseHandover(isDonor, reason);
        Request saved = requestRepository.save(request);
        publishNegotiationEnded(saved);

        // Người nhận từ chối thì món bị từ chối là bài chính; chủ bài từ chối (chỉ ở Trao đổi) thì là món đổi
        long refusedItemId = isDonor ? saved.getOfferedItemId() : saved.getItemId();
        long reportedUserId = isDonor ? saved.getReceiverId() : item.donorId();
        releaseReservedItems(saved);
        itemService.hide(refusedItemId);

        // Listener của module report chạy ngay trong transaction này để báo cáo và việc huỷ cùng thành công hoặc cùng
        // rollback.
        eventPublisher.publishEvent(new NotAsDescribedReportedEvent(
                requestId, currentUserId, refusedItemId, reportedUserId, reason, requestDto.evidenceMediaIds()));
        notifyRefusal(saved, currentUserId, reportedUserId);
        if (activityLogService != null) {
            activityLogService.log(
                    ActivityType.REQUEST_CANCEL.name(),
                    currentUserId,
                    "REQUEST",
                    saved.getId(),
                    (String) null,
                    saved.getCancelReason());
        }
        return withReports(toResponse(saved), requestId, currentUserId);
    }

    // Báo cả hai bên khi giao dịch huỷ vì từ chối nhận; bên bị báo cáo biết món đang tạm ẩn và có thể giải trình.
    private void notifyRefusal(Request request, long refuserId, long reportedUserId) {
        String link = "/requests/" + request.getId();
        notificationService.notify(
                refuserId,
                "TRANSACTION_CANCELLED",
                "Đã từ chối nhận",
                "Bạn đã từ chối nhận và gửi báo cáo. Giao dịch đã huỷ, bài của bạn được mở lại. Admin đang xem xét.",
                link);
        notificationService.notify(
                reportedUserId,
                "TRANSACTION_CANCELLED",
                "Đối phương từ chối nhận món đồ",
                "Đối phương đã từ chối nhận vì món không đúng mô tả. Giao dịch đã huỷ, món của bạn đang tạm ẩn chờ"
                        + " Admin xem xét. Bạn có thể gửi giải trình ở trang Tiến trình giao dịch.",
                link);
    }

    // ===== Đề nghị huỷ có xác nhận của bên kia (GN-06) =====

    /**
     * Một bên đề nghị huỷ giao dịch đã chốt lịch khi chưa tới giờ hẹn (GN-06). Giao dịch chưa huỷ: bên kia được báo và
     * có cancel_request_response_hours giờ để đồng ý, không đồng ý hoặc im lặng.
     *
     * @throws BusinessException REQUEST_INVALID_STATE_TRANSITION khi đã
     *     tới giờ hẹn hoặc sai trạng thái, REQUEST_CANCEL_REQUEST_PENDING khi đã có đề nghị, REQUEST_CANCEL_REASON_INVALID
     *     khi mã lý do không dùng được trước giờ hẹn hoặc thiếu chữ cho "Lý do khác"
     */
    @Transactional
    public RequestResponse requestCancel(long requestId, long currentUserId, CancelRequestRequest requestDto) {
        Request request = loadRequestForUpdate(requestId);
        ItemSnapshot item = loadItemSnapshot(request);
        boolean isDonor = requireParty(request, item, currentUserId);
        Instant now = Instant.now();
        // Từ giờ hẹn trở đi hai bên dùng "Giao nhận không thành công"
        if (!request.isBeforeMeetingTime(now)) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }
        if (!requestDto.code().isAllowedBeforeMeeting()) {
            throw new BusinessException(RequestErrorCode.REQUEST_CANCEL_REASON_INVALID);
        }
        String reason = cleanCancelReason(requestDto.code(), requestDto.reason());
        return toResponse(createCancelRequest(request, item, currentUserId, isDonor, requestDto.code(), reason, now));
    }

    // "Giao nhận không thành công" khi bên kia chưa bấm hoàn tất: chưa huỷ, chỉ tạo đề nghị (GN-06).
    private Request startCancelRequestAfterMeeting(
            Request request,
            ItemSnapshot item,
            long userId,
            boolean isDonor,
            CancelRequestCode code,
            String typedReason,
            List<Long> evidenceMediaIds) {
        Instant now = Instant.now();
        if (request.isBeforeMeetingTime(now)) {
            throw new BusinessException(RequestErrorCode.REQUEST_BEFORE_MEETING_TIME);
        }
        boolean shipping = "SHIPPING".equals(request.getDeliveryMethod());
        if (!code.isAllowedAfterMeeting(shipping)) {
            throw new BusinessException(RequestErrorCode.REQUEST_CANCEL_REASON_INVALID);
        }
        String reason = cleanCancelReason(code, typedReason);
        Request saved = createCancelRequest(request, item, userId, isDonor, code, reason, now);
        attachHandoverEvidence(saved, userId, evidenceMediaIds);
        return saved;
    }

    private Request createCancelRequest(
            Request request,
            ItemSnapshot item,
            long userId,
            boolean isDonor,
            CancelRequestCode code,
            String reason,
            Instant now) {
        request.requestCancel(userId, isDonor, code, reason, now);
        Request saved = requestRepository.save(request);

        int responseHours = configService.getInt(ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS);
        Instant dueAt = now.plus(responseHours, ChronoUnit.HOURS);
        long otherUserId = isDonor ? saved.getReceiverId() : item.donorId();
        notificationService.notify(
                otherUserId,
                "CANCEL_REQUESTED",
                "Đề nghị huỷ giao dịch",
                nameOrDefault(userId) + " đề nghị huỷ giao dịch vì " + code.phraseForOtherParty(reason)
                        + ". Bạn có đồng ý không? Hạn phản hồi " + VietnamTime.format(dueAt) + ".",
                "/requests/" + saved.getId());
        return saved;
    }

    // Lý do "Lý do khác" bắt buộc có chữ từ 5 ký tự; các mã còn lại chữ là phần ghi thêm, có thể bỏ trống.
    private String cleanCancelReason(CancelRequestCode code, String reason) {
        String text = reason == null ? "" : reason.trim();
        if (code == CancelRequestCode.OTHER && text.length() < 5) {
            throw new BusinessException(RequestErrorCode.REQUEST_CANCEL_REASON_INVALID);
        }
        return text.isEmpty() ? null : text;
    }

    private String requireTypedReason(String typedReason) {
        if (typedReason.length() < 5) {
            throw new BusinessException(RequestErrorCode.REQUEST_CANCEL_REASON_INVALID);
        }
        return typedReason;
    }

    // Nội dung khiếu nại khi có mã lý do: nhãn của mã, kèm chữ người dùng nhập nếu có.
    private String describeFailure(CancelRequestCode code, String typedReason) {
        if (code == CancelRequestCode.OTHER) {
            return requireTypedReason(typedReason);
        }
        return typedReason.isEmpty() ? code.label() : code.label() + ". " + typedReason;
    }

    private String nameOrDefault(long userId) {
        String name = findUserName(userId);
        return name == null || name.isBlank() ? "Đối phương" : name;
    }

    /**
     * Người đề nghị rút lại đề nghị huỷ trước khi bên kia phản hồi (GN-06).
     *
     * @throws BusinessException REQUEST_CANCEL_REQUEST_NOT_FOUND khi không có đề nghị đang chờ, REQUEST_FORBIDDEN khi
     *     không phải người đề nghị
     */
    @Transactional
    public RequestResponse withdrawCancelRequest(long requestId, long currentUserId) {
        Request request = loadRequestForUpdate(requestId);
        ItemSnapshot item = loadItemSnapshot(request);
        boolean isDonor = requireParty(request, item, currentUserId);
        request.withdrawCancelRequest(currentUserId);
        Request saved = requestRepository.save(request);

        long otherUserId = isDonor ? saved.getReceiverId() : item.donorId();
        notificationService.notify(
                otherUserId,
                "CANCEL_REQUEST_WITHDRAWN",
                "Đề nghị huỷ đã được rút lại",
                nameOrDefault(currentUserId) + " đã rút lại đề nghị huỷ. Giao dịch tiếp tục như thường.",
                "/requests/" + saved.getId());
        return toResponse(saved);
    }

    /**
     * Bên kia đồng ý đề nghị huỷ (GN-06): giao dịch huỷ, không phạt ai, bài chính và món đổi được nhả, báo hai bên.
     *
     * @throws BusinessException REQUEST_CANCEL_REQUEST_NOT_FOUND khi không có đề nghị đang chờ, REQUEST_FORBIDDEN khi
     *     người gọi chính là người đề nghị
     */
    @Transactional
    public RequestResponse acceptCancelRequest(long requestId, long currentUserId) {
        Request request = loadRequestForUpdate(requestId);
        ItemSnapshot item = loadItemSnapshot(request);
        boolean isDonor = requireParty(request, item, currentUserId);
        request.acceptCancelRequest(currentUserId, isDonor);

        Request saved = finishCancelByAgreement(request, currentUserId);
        String link = "/requests/" + saved.getId();
        notificationService.notify(
                saved.getCancelRequestedBy(),
                "TRANSACTION_CANCELLED",
                "Giao dịch đã được huỷ",
                nameOrDefault(currentUserId)
                        + " đã đồng ý huỷ giao dịch. Bài đăng được mở lại và không bên nào bị trừ sao.",
                link);
        notificationService.notify(
                currentUserId,
                "TRANSACTION_CANCELLED",
                "Giao dịch đã được huỷ",
                "Bạn đã đồng ý huỷ giao dịch. Bài đăng được mở lại và không bên nào bị trừ sao.",
                link);
        return toResponse(saved);
    }

    // Phần chung của huỷ theo đề nghị: lưu, nhả bài chính và món đổi (releaseReservedItems trả lại thời gian hiển thị),
    // báo bài mở lại, ghi nhật ký.
    private Request finishCancelByAgreement(Request request, long actorUserId) {
        Request saved = requestRepository.save(request);
        publishNegotiationEnded(saved);
        releaseReservedItems(saved);
        notifyItemReopened(saved);
        if (activityLogService != null) {
            activityLogService.log(
                    ActivityType.REQUEST_CANCEL.name(),
                    actorUserId,
                    "REQUEST",
                    saved.getId(),
                    (String) null,
                    saved.getCancelReason());
        }
        return saved;
    }

    /**
     * Bên kia không đồng ý đề nghị huỷ (GN-06): giao dịch sang DISPUTED và mở khiếu nại sự cố giao nhận gồm lý do của
     * hai bên. Giải trình là bắt buộc, ảnh không bắt buộc.
     *
     * @throws BusinessException REQUEST_CANCEL_REQUEST_NOT_FOUND khi không có đề nghị đang chờ, REQUEST_FORBIDDEN khi
     *     người gọi chính là người đề nghị
     */
    @Transactional
    public RequestResponse rejectCancelRequest(long requestId, long currentUserId, RejectCancelRequest requestDto) {
        Request request = loadRequestForUpdate(requestId);
        ItemSnapshot item = loadItemSnapshot(request);
        requireParty(request, item, currentUserId);
        Request saved = disputeCancelRequest(
                request, currentUserId, requestDto.explanation().trim(), requestDto.evidenceMediaIds());
        return withReports(toResponse(saved), requestId, currentUserId);
    }

    // Chuyển DISPUTED và báo module report mở khiếu nại. Dùng cho "Không đồng ý" và cho việc bên kia bấm hoàn tất khi
    // có đề nghị huỷ đang chờ (GN-06).
    private Request disputeCancelRequest(
            Request request, long rejecterId, String explanation, List<Long> evidenceMediaIds) {
        long requesterId = request.getCancelRequestedBy() == null ? 0 : request.getCancelRequestedBy();
        String summary = request.cancelRequestSummary();
        request.rejectCancelRequest(rejecterId);
        attachMediaInternal(request.getId(), rejecterId, evidenceMediaIds, "DISPUTE");
        Request saved = requestRepository.save(request);

        String note = nameOrDefault(requesterId) + " đề nghị huỷ giao dịch. " + summary
                + ". Bên kia không đồng ý và giải trình: " + explanation;
        // Listener của module report chạy ngay trong transaction này để khiếu nại và DISPUTED cùng thành công hoặc
        // cùng rollback.
        eventPublisher.publishEvent(new CancelRequestRejectedEvent(saved.getId(), requesterId, note));
        notificationService.notify(
                requesterId,
                "CANCEL_REQUEST_REJECTED",
                "Đề nghị huỷ không được đồng ý",
                nameOrDefault(rejecterId) + " không đồng ý huỷ giao dịch và đã giải trình. Giao dịch chuyển sang sự cố"
                        + " giao nhận, chờ Admin xem xét.",
                "/requests/" + saved.getId());
        return saved;
    }

    /**
     * Việc nền: đề nghị huỷ quá cancel_request_response_hours giờ mà bên kia im lặng thì huỷ như "Đồng ý" (GN-06).
     * Lý do "bên kia không đến điểm hẹn" hoặc "chưa gửi hàng" thì mở thêm báo cáo không đến hẹn nhắm vào bên im lặng để
     * Admin xem. Không tự trừ sao.
     *
     * @return true nếu đã huỷ, false nếu đề nghị đã được xử lý hoặc chưa tới hạn
     */
    @Transactional
    public boolean expireCancelRequest(long requestId, Instant now) {
        Request request = requestRepository.findByIdForUpdate(requestId).orElse(null);
        int responseHours = configService.getInt(ConfigKey.CANCEL_REQUEST_RESPONSE_HOURS);
        if (request == null
                || !request.hasPendingCancelRequest()
                || request.getCancelRequestedAt()
                        .plus(responseHours, ChronoUnit.HOURS)
                        .isAfter(now)) {
            return false;
        }

        long requesterId = request.getCancelRequestedBy();
        CancelRequestCode code = request.getCancelRequestCode();
        String note = request.cancelRequestSummary();
        request.expireCancelRequest("Bên kia không phản hồi đề nghị huỷ trong " + responseHours + " giờ.");
        Request saved = finishCancelByAgreement(request, requesterId);

        long silentUserId = findOtherPartyId(saved, requesterId);
        String link = "/requests/" + saved.getId();
        notificationService.notify(
                requesterId,
                "TRANSACTION_CANCELLED",
                "Giao dịch đã được huỷ",
                "Giao dịch đã huỷ vì bên kia không phản hồi đề nghị huỷ trong " + responseHours
                        + " giờ. Bài đăng được mở lại và không bên nào bị trừ sao.",
                link);
        notificationService.notify(
                silentUserId,
                "TRANSACTION_CANCELLED",
                "Giao dịch đã được huỷ",
                "Giao dịch đã huỷ vì bạn không phản hồi đề nghị huỷ trong " + responseHours
                        + " giờ. Bài đăng được mở lại và không bên nào bị trừ sao.",
                link);

        if (code == CancelRequestCode.COUNTERPART_NO_SHOW || code == CancelRequestCode.COUNTERPART_NOT_SHIPPED) {
            String issue = code == CancelRequestCode.COUNTERPART_NO_SHOW ? "không đến điểm hẹn" : "chưa gửi hàng";
            eventPublisher.publishEvent(
                    new SilentCancelRequestReportedEvent(saved.getId(), requesterId, silentUserId, issue, note));
        }
        return true;
    }

    // Bên còn lại của giao dịch so với một người đã biết (chủ bài hoặc người nhận).
    private long findOtherPartyId(Request request, long userId) {
        if (request.getReceiverId() == userId) {
            return loadItemSnapshot(request).donorId();
        }
        return request.getReceiverId();
    }

    // Khoá dòng giao dịch: dùng cho các thao tác đổi trạng thái sau khi chốt lịch để hai bên bấm cùng lúc không đè
    // nhau.
    private Request loadRequestForUpdate(long requestId) {
        return requestRepository
                .findByIdForUpdate(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));
    }

    private Request loadRequest(long requestId) {
        return requestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));
    }

    private ItemSnapshot loadItemSnapshot(Request request) {
        return findItemSnapshot(request.getItemId())
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_ITEM_NOT_FOUND));
    }

    /** Kiểm người thao tác là một trong hai bên; trả về true nếu là chủ bài, false nếu là người nhận. */
    private boolean requireParty(Request request, ItemSnapshot item, long currentUserId) {
        boolean isDonor = item.donorId() == currentUserId;
        boolean isReceiver = request.getReceiverId() == currentUserId;
        if (!isDonor && !isReceiver) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }
        return isDonor;
    }

    private void attachHandoverEvidence(Request request, long currentUserId, List<Long> mediaIds) {
        if (mediaIds != null && !mediaIds.isEmpty()) {
            attachMediaInternal(request.getId(), currentUserId, mediaIds, "HANDOVER");
        }
    }

    /** Huỷ giao dịch do một bên, nhả bài về APPROVED, báo bên kia và ghi nhật ký. Dùng chung cho hai đường huỷ. */
    private Request cancelByParty(
            Request request, ItemSnapshot item, long currentUserId, boolean isDonor, String reason) {
        String prefix = isDonor ? Request.CANCEL_BY_DONOR : Request.CANCEL_BY_RECEIVER;
        request.cancel(prefix + reason);
        Request saved = requestRepository.save(request);
        publishNegotiationEnded(saved);

        releaseReservedItems(saved);
        notifyItemReopened(saved);

        long otherUserId = isDonor ? saved.getReceiverId() : item.donorId();
        notificationService.notify(
                otherUserId,
                "TRANSACTION_CANCELLED",
                "Giao dịch đã bị huỷ",
                "Đối phương đã huỷ giao dịch. Lý do: " + reason,
                "/requests/" + saved.getId());

        if (activityLogService != null) {
            activityLogService.log(
                    ActivityType.REQUEST_CANCEL.name(),
                    currentUserId,
                    "REQUEST",
                    saved.getId(),
                    (String) null,
                    prefix + reason);
        }
        return saved;
    }

    /**
     * Đưa giao dịch DISPUTED ra khỏi trạng thái tranh chấp khi Admin kết luận khiếu nại sự cố giao nhận (GN-07: một
     * bên báo giao nhận không thành công khi bên kia đã bấm hoàn tất). Giao dịch không ở DISPUTED (ví dụ khiếu nại mở
     * sau khi đã COMPLETED) thì bỏ qua, không làm gì. Từ chối nhận không đi qua đây vì giao dịch đã huỷ ngay.
     *
     * <p>Chấp nhận: CANCELLED, nhả cả hai món về APPROVED; nếu bên vi phạm là chủ bài thì bài chính bị ẩn luôn
     * (is_active = false, giống BC-05). Bác bỏ: COMPLETED như một lần hoàn tất bình thường (hai món sang TRADED, cộng
     * điểm tích luỹ). Cả hai bên đều được thông báo kết quả.
     *
     * @param requestId giao dịch của khiếu nại
     * @param upheld true nếu Admin chấp nhận khiếu nại, false nếu bác bỏ
     * @return true nếu giao dịch đã được xử lý
     */
    @Transactional
    public boolean resolveDisputedRequest(long requestId, boolean upheld) {
        return resolveDisputedRequest(requestId, upheld, null);
    }

    /**
     * Như {@link #resolveDisputedRequest(long, boolean)}, kèm người bị kết luận vi phạm (người bị khiếu nại).
     *
     * @param violatorUserId người bị khiếu nại; null nếu không rõ (khi đó bài chính được mở lại như cũ)
     */
    @Transactional
    public boolean resolveDisputedRequest(long requestId, boolean upheld, Long violatorUserId) {
        Request request = requestRepository.findById(requestId).orElse(null);
        if (request == null || request.getStatus() != RequestStatus.DISPUTED) {
            return false;
        }

        String message;
        if (upheld) {
            message = cancelUpheldDispute(request, violatorUserId);
        } else {
            request.completeAfterDispute(Instant.now());
            requestRepository.save(request);
            itemService.markTraded(request.getItemId());
            if (request.getOfferedItemId() != null) {
                itemService.markTraded(request.getOfferedItemId());
            }
            if (reputationService != null) {
                reputationService.onCompleted(request.getId());
            }
            message =
                    "Admin đã xác minh sự cố giao nhận: việc giao nhận đã diễn ra, giao dịch được công nhận hoàn tất.";
        }

        notifyBothParties(request, upheld ? "TRANSACTION_CANCELLED" : "TRANSACTION_COMPLETED", message);
        return true;
    }

    /**
     * Huỷ giao dịch khi Admin chấp nhận khiếu nại sự cố giao nhận. Chủ bài vi phạm: ẩn bài chính, món đổi mở lại. Các
     * trường hợp còn lại: mở lại cả hai món.
     *
     * @return câu thông báo kết quả gửi cho hai bên
     */
    private String cancelUpheldDispute(Request request, Long violatorUserId) {
        request.cancel(Request.CANCEL_BY_SYSTEM + "Admin xác minh sự cố giao nhận, giao dịch bị huỷ");
        requestRepository.save(request);

        Long donorId =
                findItemSnapshot(request.getItemId()).map(ItemSnapshot::donorId).orElse(null);
        boolean donorViolated = violatorUserId != null && violatorUserId.equals(donorId);

        itemService.releaseIfReservedAfterCancel(request.getItemId(), request.getReservedAt());
        if (donorViolated) {
            itemService.hide(request.getItemId());
        }
        if (request.getOfferedItemId() != null) {
            itemService.releaseIfReservedAfterCancel(request.getOfferedItemId(), request.getReservedAt());
        }

        if (donorViolated) {
            return "Admin đã xác minh sự cố giao nhận: giao dịch bị huỷ. Bài đăng của chủ bài bị ẩn vì chủ bài là bên"
                    + " vi phạm, món đem đổi (nếu có) được mở lại.";
        }
        return "Admin đã xác minh sự cố giao nhận: giao dịch bị huỷ và bài đăng được mở lại.";
    }

    private void notifyBothParties(Request request, String type, String message) {
        String link = "/requests/" + request.getId();
        notificationService.notify(request.getReceiverId(), type, "Kết quả khiếu nại giao dịch", message, link);
        findItemSnapshot(request.getItemId())
                .ifPresent(item ->
                        notificationService.notify(item.donorId(), type, "Kết quả khiếu nại giao dịch", message, link));
    }

    /**
     * Huỷ mọi giao dịch đang mở (PENDING, RESERVED, AWAITING_LOGISTICS, LOGISTICS_CONFIRMED, AWAITING_HANDOVER) của một
     * tài khoản vừa bị khoá, dù họ là chủ bài hay người nhận. Bài và món mang đổi được nhả về APPROVED, bên còn lại được
     * thông báo. Dùng releaseIfReserved cho cả hai món để dữ liệu lệch không làm hỏng việc khoá tài khoản.
     *
     * @param lockedUserId tài khoản bị khoá
     * @return số giao dịch đã huỷ
     */
    @Transactional
    public int cancelOpenRequestsOfLockedUser(long lockedUserId) {
        Map<Long, Request> openRequests = new LinkedHashMap<>();
        for (Request request : requestRepository.findByReceiverIdAndStatusIn(lockedUserId, LOCK_CANCEL_STATUSES)) {
            openRequests.put(request.getId(), request);
        }
        List<Long> ownItemIds = itemQueryService.findItemIdsByDonor(lockedUserId);
        if (!ownItemIds.isEmpty()) {
            for (Request request : requestRepository.findByItemIdInAndStatusIn(ownItemIds, LOCK_CANCEL_STATUSES)) {
                openRequests.put(request.getId(), request);
            }
        }

        for (Request request : openRequests.values()) {
            cancelBecauseUserLocked(request, lockedUserId);
        }
        return openRequests.size();
    }

    /** Huỷ các yêu cầu PENDING khi bài đăng bị gỡ (D-13, BD-11). */
    @Transactional
    public int cancelPendingRequestsForItemRemoved(long itemId) {
        List<Request> pendingRequests = requestRepository.findByItemIdAndStatus(itemId, RequestStatus.PENDING);
        for (Request request : pendingRequests) {
            request.cancel(Request.CANCEL_BY_SYSTEM + "Bài đã bị gỡ");
            requestRepository.save(request);
            notificationService.notify(
                    request.getReceiverId(),
                    "REQUEST_CANCELLED",
                    "Yêu cầu đã bị huỷ",
                    "Bài đăng đã bị gỡ nên yêu cầu đang chờ của bạn đã bị huỷ.",
                    "/requests/" + request.getId());
        }
        return pendingRequests.size();
    }

    /**
     * Huỷ các yêu cầu PENDING đang dùng bài làm món đem đổi, khi bài đó không còn dùng được (bị giữ chỗ ở giao dịch
     * khác, bị xoá, ẩn, sửa hoặc hết hạn; YC-04, YC-07). Người gửi và chủ bài đích đều được báo. Yêu cầu vừa được chọn
     * đã là RESERVED nên không bị lấy ra.
     *
     * @return số yêu cầu đã huỷ
     */
    @Transactional
    public int cancelPendingRequestsOfferingItem(long offeredItemId) {
        List<Request> pendingRequests =
                requestRepository.findByOfferedItemIdAndStatus(offeredItemId, RequestStatus.PENDING);
        for (Request request : pendingRequests) {
            request.cancel(Request.CANCEL_BY_SYSTEM + "Món bạn đề nghị đổi không còn khả dụng");
            requestRepository.save(request);
            notifyOfferedItemUnavailable(request);
        }
        return pendingRequests.size();
    }

    private void notifyOfferedItemUnavailable(Request request) {
        String link = "/requests/" + request.getId();
        notificationService.notify(
                request.getReceiverId(),
                "REQUEST_CANCELLED",
                "Yêu cầu trao đổi bị huỷ",
                "Món bạn đề nghị đổi không còn khả dụng nên yêu cầu đã tự huỷ.",
                link);
        findItemSnapshot(request.getItemId())
                .ifPresent(item -> notificationService.notify(
                        item.donorId(),
                        "REQUEST_CANCELLED",
                        "Một yêu cầu đã tự huỷ",
                        "Món người gửi đề nghị đổi không còn khả dụng nên yêu cầu tới bài của bạn đã tự huỷ.",
                        link));
    }

    /**
     * Bài đang hiển thị vừa bị chủ bài sửa và chờ duyệt lại: báo người có yêu cầu PENDING tới bài. Không huỷ yêu cầu;
     * chủ bài chọn người được sau khi bài được duyệt lại.
     */
    @Transactional
    public void notifyPendingRequestersItemEdited(long itemId) {
        for (Request request : requestRepository.findByItemIdAndStatus(itemId, RequestStatus.PENDING)) {
            notificationService.notify(
                    request.getReceiverId(),
                    "REQUEST_ITEM_EDITED",
                    "Bài bạn xin vừa được sửa",
                    "Chủ bài vừa sửa bài, bài đang chờ duyệt lại.",
                    "/requests/" + request.getId());
        }
    }

    /**
     * Admin ẩn bài đang có yêu cầu chờ chọn: huỷ mọi yêu cầu PENDING tới bài và báo từng người xin. Người xin không
     * thấy lý do ẩn chi tiết, chỉ biết bài đã bị ẩn. Giao dịch đã chọn (RESERVED trở lên) không bị lấy ra vì
     * AdminItemService đã chặn ẩn bài đang có giao dịch chạy.
     *
     * @return số yêu cầu đã huỷ
     */
    @Transactional
    public int cancelPendingRequestsToItem(long itemId, String reason) {
        return cancelPendingRequestsToItem(itemId, reason, "Bài bạn xin đã bị quản trị viên ẩn nên yêu cầu đã tự huỷ.");
    }

    /**
     * Bài không còn chọn được (hết hạn, bị ẩn, bản sửa bị từ chối): huỷ các yêu cầu PENDING tới bài, ghi đúng nguyên nhân
     * và báo từng người xin (C1). Lý do khác (giữ chỗ, xoá, sửa) không đi qua đây nên trả 0.
     *
     * @return số yêu cầu đã huỷ
     */
    @Transactional
    public int cancelPendingRequestsToItem(long itemId, ItemUnavailableEvent.Reason reason) {
        switch (reason) {
            case EXPIRED:
                return cancelPendingRequestsToItem(
                        itemId,
                        "Bài đã hết thời gian hiển thị",
                        "Bài bạn xin đã hết thời gian hiển thị nên yêu cầu đã tự huỷ.");
            case HIDDEN:
                return cancelPendingRequestsToItem(
                        itemId, "Bài đang tạm ẩn", "Bài bạn xin đang tạm ẩn nên yêu cầu đã tự huỷ.");
            case HIDDEN_BY_ADMIN:
                return cancelPendingRequestsToItem(
                        itemId,
                        "Bài đã bị quản trị viên ẩn",
                        "Bài bạn xin đã bị quản trị viên ẩn nên yêu cầu đã tự huỷ.");
            case HIDDEN_BY_ACCOUNT:
                return cancelPendingRequestsToItem(
                        itemId,
                        "Chủ bài đang bị khoá tài khoản",
                        "Chủ bài đang bị khoá tài khoản nên yêu cầu bạn gửi đã tự huỷ.");
            case EDIT_REJECTED:
                return cancelPendingRequestsToItem(
                        itemId,
                        "Bài đang được chủ bài chỉnh sửa và chưa được duyệt",
                        "Bài bạn xin đang được chủ bài chỉnh sửa và chưa được duyệt nên yêu cầu đã tự huỷ.");
            default:
                return 0;
        }
    }

    // Dùng chung cho mọi nguyên nhân: huỷ yêu cầu PENDING tới bài, lưu lý do hệ thống và thông báo người xin.
    private int cancelPendingRequestsToItem(long itemId, String systemReason, String requesterMessage) {
        List<Request> pendingRequests = requestRepository.findByItemIdAndStatus(itemId, RequestStatus.PENDING);
        for (Request request : pendingRequests) {
            request.cancel(Request.CANCEL_BY_SYSTEM + systemReason);
            requestRepository.save(request);
            notificationService.notify(
                    request.getReceiverId(),
                    "REQUEST_CANCELLED",
                    "Yêu cầu của bạn bị huỷ",
                    requesterMessage,
                    "/requests/" + request.getId());
        }
        return pendingRequests.size();
    }

    private void cancelBecauseUserLocked(Request request, long lockedUserId) {
        boolean wasPending = request.getStatus() == RequestStatus.PENDING;
        request.cancel(Request.CANCEL_BY_SYSTEM + LOCKED_ACCOUNT_REASON);
        requestRepository.save(request);

        // Yêu cầu PENDING chưa giữ chỗ món nào và chưa mở chat nên không có gì để nhả hay huỷ đề nghị
        if (!wasPending) {
            itemService.releaseIfReservedAfterCancel(request.getItemId(), request.getReservedAt());
            if (request.getOfferedItemId() != null) {
                itemService.releaseIfReservedAfterCancel(request.getOfferedItemId(), request.getReservedAt());
            }
            publishNegotiationEnded(request);
        }

        long donorId =
                findItemSnapshot(request.getItemId()).map(ItemSnapshot::donorId).orElse(lockedUserId);
        long otherUserId = donorId == lockedUserId ? request.getReceiverId() : donorId;
        if (otherUserId == lockedUserId) {
            return;
        }
        notificationService.notify(
                otherUserId,
                "TRANSACTION_CANCELLED",
                "Giao dịch đã bị huỷ",
                "Giao dịch đã bị huỷ vì tài khoản của đối phương đã bị khoá.",
                "/requests/" + request.getId());
    }

    /**
     * Admin huỷ giao dịch đang mở hoặc đang DISPUTED (thường là giao dịch kẹt). Cùng cách huỷ như
     * {@link #cancelTransaction}: nhả bài chính và món mang đổi về APPROVED, nhưng thông báo cả hai bên và ghi
     * activity log với người thực hiện là Admin.
     *
     * @throws BusinessException REQUEST_NOT_FOUND, REQUEST_INVALID_STATE_TRANSITION khi giao dịch đã kết thúc hoặc
     *     chưa được chọn người
     */
    @Transactional
    public RequestResponse cancelByAdmin(long requestId, long adminId, CancelTransactionRequest requestDto) {
        Request request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));
        boolean cancellable = CANCELLABLE_TRANSACTION_STATUSES.contains(request.getStatus())
                || request.getStatus() == RequestStatus.DISPUTED;
        if (!cancellable) {
            throw new BusinessException(RequestErrorCode.REQUEST_INVALID_STATE_TRANSITION);
        }

        String reason = requestDto.reason().trim();
        request.cancel(Request.CANCEL_BY_ADMIN + reason);
        Request saved = requestRepository.save(request);
        releaseReservedItems(saved);
        publishNegotiationEnded(saved);
        notifyItemReopened(saved);

        notifyBothParties(saved, "TRANSACTION_CANCELLED", "Admin đã huỷ giao dịch. Lý do: " + reason);
        if (activityLogService != null) {
            activityLogService.log(
                    ActivityType.REQUEST_CANCEL.name(),
                    adminId,
                    "REQUEST",
                    saved.getId(),
                    (String) null,
                    Request.CANCEL_BY_ADMIN + reason);
        }
        return toResponse(saved);
    }

    // Bài chính vừa về APPROVED: báo những người từng bị từ chối ở bài này (mỗi người một lần) để họ xin lại.
    // itemReopenedNotifier null chỉ gặp ở unit test dựng service rút gọn.
    private void notifyItemReopened(Request request) {
        if (itemReopenedNotifier != null) {
            itemReopenedNotifier.notifyRejectedRequesters(request.getItemId(), request.getReceiverId());
        }
    }

    // Nhả bài chính về APPROVED. Món mang đổi dùng releaseIfReserved vì giao dịch SWAP cũ có thể chưa giữ chỗ nó.
    private void releaseReservedItems(Request request) {
        itemService.releaseAfterCancel(request.getItemId(), request.getReservedAt());
        if (request.getOfferedItemId() != null) {
            itemService.releaseIfReservedAfterCancel(request.getOfferedItemId(), request.getReservedAt());
        }
    }

    /**
     * Xem thông tin chi tiết của một yêu cầu giao dịch (F10; dùng chung cho các use case giao dịch UC-22 … UC-30).
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
        Instant complaintDeadline = ComplaintWindow.deadline(request.getCompletedAt(), complaintWindowHours());
        Instant lastMessageAt = findLastMessageAt(request.getId());
        Instant disputeResponseDueAt = findDisputeResponseDueAt(request);
        // Chi tiết là nơi duy nhất kèm ảnh giao nhận, để màn Tiến trình giao dịch không phải gọi thêm API.
        List<RequestMediaResponse> handoverMedia = findHandoverMedia(request.getId());
        if (item.donorId() == currentUserId) {
            ReputationSummary rep = findReceiverReputation(request.getReceiverId());
            Integer trustStars = rep != null ? rep.trustStars() : 5;
            String rank = rep != null && rep.rank() != null ? rep.rank().name() : Rank.BRONZE.name();
            RequestResponse response = RequestMapper.toResponse(
                    request,
                    receiverName,
                    trustStars,
                    rank,
                    itemInfo,
                    complaintDeadline,
                    lastMessageAt,
                    disputeResponseDueAt);
            return withReports(addExtraInfo(request, response, handoverMedia), request.getId(), currentUserId)
                    .withFlags(detailFlagsOf(request, request.getItemId()));
        }

        RequestResponse response = RequestMapper.toResponse(
                request, receiverName, null, null, itemInfo, complaintDeadline, lastMessageAt, disputeResponseDueAt);
        return withReports(addExtraInfo(request, response, handoverMedia), request.getId(), currentUserId)
                .withFlags(detailFlagsOf(request, request.getItemId()));
    }

    /**
     * Đề xuất hoặc cập nhật lịch hẹn giao nhận (GN-01, GN-02).
     *
     * <p>Chỉ bên tham gia giao dịch mới được đề xuất; chuyển sang AWAITING_LOGISTICS nếu đang RESERVED.
     */
    @Transactional
    public RequestResponse updateSchedule(long requestId, long currentUserId, ScheduleRequest requestDto) {
        Request request = requestRepository
                .findByIdForScheduleUpdate(requestId)
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

        request.proposeSchedule(
                requestDto.deliveryMethod(),
                requestDto.meetingPlace(),
                requestDto.meetingTime(),
                isDonor,
                Boolean.TRUE.equals(requestDto.agreeToWaiver()),
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
     * Xác nhận lịch hẹn giao nhận và cam kết trách nhiệm (GN-02, GN-03).
     *
     * <p>Khi cả hai bên đều đã đồng ý, Request chuyển sang LOGISTICS_CONFIRMED và khoá lịch.
     */
    @Transactional
    public RequestResponse confirmLogistics(long requestId, long currentUserId, ConfirmLogisticsRequest requestDto) {
        Request request = requestRepository
                .findByIdForScheduleUpdate(requestId)
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
            publishNegotiationEnded(saved);
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
     * Lấy thông tin liên lạc của đối tác khi giao dịch đã chốt lịch (GN-04).
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
     * Một bên bấm "Hoàn tất giao nhận" (GN-06, GN-08, UC-27).
     *
     * <p>Chỉ chủ bài đăng (donor) hoặc người nhận (receiver) mới có quyền xác nhận.
     * Khi một bên bấm, Request chuyển sang AWAITING_HANDOVER và tiếp tục chờ bên còn lại (GN-08).
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
     * @param requestDto ảnh hoặc video minh chứng (tuỳ chọn, có thể null)
     * @return RequestResponse sau khi xác nhận
     */
    @Transactional
    public RequestResponse confirmHandover(long requestId, long currentUserId, HandoverConfirmRequest requestDto) {
        // Khoá dòng để việc bấm hoàn tất và việc đồng ý đề nghị huỷ cùng lúc không đè nhau (GN-06)
        Request request = loadRequestForUpdate(requestId);
        ItemSnapshot item = loadItemSnapshot(request);
        boolean isDonor = requireParty(request, item, currentUserId);

        // Đính kèm ảnh hoặc video minh chứng khi giao nhận nếu có (GN-10)
        if (requestDto != null) {
            attachHandoverEvidence(request, currentUserId, requestDto.mediaIds());
        }

        boolean answersCancelRequest = request.hasPendingCancelRequest();
        if (answersCancelRequest && Long.valueOf(currentUserId).equals(request.getCancelRequestedBy())) {
            // Người đề nghị huỷ không thể vừa chờ huỷ vừa báo đã giao nhận xong: phải rút đề nghị trước
            throw new BusinessException(RequestErrorCode.REQUEST_CANCEL_REQUEST_PENDING);
        }

        request.confirmHandover(isDonor, Instant.now());
        if (answersCancelRequest) {
            // GN-06: bên kia bấm "Đã giao nhận xong" khi có đề nghị huỷ đang chờ thì coi như không đồng ý
            Request disputed = disputeCancelRequest(
                    request, currentUserId, "Tôi đã bấm đã giao nhận xong nên không đồng ý huỷ giao dịch.", null);
            return withReports(toResponse(disputed), requestId, currentUserId);
        }
        Request saved = requestRepository.save(request);

        long otherUserId = isDonor ? request.getReceiverId() : item.donorId();

        if (saved.getStatus() == RequestStatus.COMPLETED) {
            // 1. Chuyển bài đăng sang TRADED (GN-06, GN-08, #94)
            itemService.markTraded(saved.getItemId());
            if ("SWAP".equalsIgnoreCase(saved.getType()) && saved.getOfferedItemId() != null) {
                itemService.markTraded(saved.getOfferedItemId());
            }

            long targetRequestId = saved.getId() != null ? saved.getId() : requestId;

            // 2. Chữ ký thông báo sang ReputationService (BC-12, UT-02, UT-05, #94)
            if (reputationService != null) {
                reputationService.onCompleted(targetRequestId);
            }

            // 3. Gửi thông báo COMPLETED cho cả hai bên, ghi rõ hạn khiếu nại (U08)
            int complaintHours = complaintWindowHours();
            Instant completedAt = saved.getCompletedAt() != null ? saved.getCompletedAt() : Instant.now();
            String completedBody = "Giao dịch đã được hai bên xác nhận hoàn tất thành công. "
                    + ComplaintWindow.sentence(complaintHours, ComplaintWindow.deadline(completedAt, complaintHours));
            notificationService.notify(
                    currentUserId,
                    "TRANSACTION_COMPLETED",
                    "Giao dịch hoàn tất",
                    completedBody,
                    "/requests/" + targetRequestId);
            notificationService.notify(
                    otherUserId,
                    "TRANSACTION_COMPLETED",
                    "Giao dịch hoàn tất",
                    completedBody,
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
     * Đính kèm ảnh hoặc video minh chứng vào yêu cầu giao dịch (GN-10, UC-28).
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

        // Minh chứng giao nhận chỉ được đính kèm ở giai đoạn giao nhận trở đi (GN-10)
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
     * Lấy danh sách ảnh hoặc video minh chứng của yêu cầu giao dịch (GN-10).
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

        // Chỉ hai bên tham gia giao dịch mới có quyền xem minh chứng (GN-10)
        if (request.getReceiverId() != currentUserId && item.donorId() != currentUserId) {
            throw new BusinessException(RequestErrorCode.REQUEST_FORBIDDEN);
        }

        return findHandoverMedia(requestId);
    }

    /** Minh chứng giao nhận của một giao dịch, cũ trước, cho trang chi tiết của Admin (Admin không phải một bên của giao dịch). */
    @Transactional(readOnly = true)
    public List<RequestMediaResponse> getMediaListForAdmin(long requestId) {
        return findHandoverMedia(requestId);
    }

    // Đường dẫn đọc ảnh riêng tư của một giao dịch (D2): kiểm quyền ở readMedia, không dùng đường công khai.
    static String mediaPath(long requestId, long mediaId) {
        return "/api/v1/requests/" + requestId + "/media/" + mediaId;
    }

    /**
     * Đọc nội dung ảnh chat hoặc ảnh giao nhận của một giao dịch (D2). Chỉ hai bên và Admin xem được, và ảnh phải thuộc
     * đúng giao dịch này. Người không có quyền hoặc ảnh không thuộc giao dịch đều nhận MEDIA_NOT_FOUND (404) chứ không phải
     * 403, để không lộ ảnh có tồn tại.
     */
    @Transactional(readOnly = true)
    public MediaService.MediaContent readMedia(long requestId, long mediaId, long currentUserId) {
        Request request = requestRepository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(MediaErrorCode.MEDIA_NOT_FOUND));
        ItemSnapshot item = findItemSnapshot(request.getItemId())
                .orElseThrow(() -> new BusinessException(MediaErrorCode.MEDIA_NOT_FOUND));
        boolean isParty = request.getReceiverId() == currentUserId || item.donorId() == currentUserId;
        boolean isAdmin = userService != null && userService.isAdmin(currentUserId);
        if ((!isParty && !isAdmin) || !isMediaOfRequest(requestId, mediaId)) {
            throw new BusinessException(MediaErrorCode.MEDIA_NOT_FOUND);
        }
        return mediaService.readPrivateAttached(mediaId);
    }

    // Ảnh thuộc giao dịch khi là ảnh giao nhận hoặc ảnh trong tin nhắn đã qua kiểm duyệt của giao dịch đó.
    private boolean isMediaOfRequest(long requestId, long mediaId) {
        if (jdbcClient == null) {
            return false;
        }
        return Boolean.TRUE.equals(jdbcClient
                .sql(
                        "SELECT EXISTS (SELECT 1 FROM request_media_mappings WHERE request_id = :requestId AND media_id = :mediaId)"
                                + " OR EXISTS (SELECT 1 FROM messages WHERE request_id = :requestId AND media_id = :mediaId AND moderation = 'PASS')")
                .param("requestId", requestId)
                .param("mediaId", mediaId)
                .query(Boolean.class)
                .single());
    }

    // Đọc minh chứng của một yêu cầu, cũ trước. Không kiểm quyền: nơi gọi đã kiểm người xem là một trong hai bên.
    private List<RequestMediaResponse> findHandoverMedia(Long requestId) {
        if (requestId == null) {
            return List.of();
        }
        if (requestMediaRepository != null) {
            return requestMediaRepository.findByRequestIdOrderByCreatedAtAsc(requestId).stream()
                    .map(m -> new RequestMediaResponse(
                            m.getMediaId(),
                            mediaPath(requestId, m.getMediaId()),
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
                            mediaPath(requestId, rs.getLong("media_id")),
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
            // Ảnh giao nhận riêng tư: chỉ hai bên và Admin xem được (D2)
            mediaService.attachPrivate(mediaIds, currentUserId);
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
     * Kiểm tra người dùng có đang trong giai đoạn giao nhận hay không (F03, TK-11).
     */
    @Transactional(readOnly = true)
    public boolean hasActiveDelivery(long userId) {
        return requestRepository.hasActiveDelivery(userId);
    }

    /**
     * Kiểm tra bài đăng đã có yêu cầu ở trạng thái RESERVED trở lên hay chưa (F07 bước 1, BD-10).
     */
    @Transactional(readOnly = true)
    public boolean hasReservedOrHigherRequest(long itemId) {
        return requestRepository.hasReservedOrHigherRequest(itemId);
    }

    /**
     * Lấy thông tin ngữ cảnh yêu cầu giao dịch dùng cho xác thực truy cập và trạng thái chat (CH-01, GN-05).
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
                request.getOfferedItemId(),
                request.getType());
    }

    // Số giờ được khiếu nại sau hoàn tất (U08), đọc ConfigService để không viết cứng 24 giờ.
    private int complaintWindowHours() {
        return configService.getInt(ConfigKey.COMPLAINT_WINDOW_HOURS);
    }

    // Phản hồi của các thao tác (tạo, huỷ, chọn, chốt lịch...) cũng kèm tên người xin và tên món đồ, để giao diện
    // không bị mất tên khi thay yêu cầu cũ bằng phản hồi mới.
    private RequestResponse toResponse(Request request) {
        Instant complaintDeadline = request != null && request.getCompletedAt() != null
                ? ComplaintWindow.deadline(request.getCompletedAt(), complaintWindowHours())
                : null;
        Instant lastMessageAt = request != null ? findLastMessageAt(request.getId()) : null;
        Instant disputeResponseDueAt = findDisputeResponseDueAt(request);
        RequestResponse response = RequestMapper.toResponse(
                request,
                findUserName(request.getReceiverId()),
                null,
                null,
                findItemInfo(request, new HashMap<>()),
                complaintDeadline,
                lastMessageAt,
                disputeResponseDueAt);
        return addExtraInfo(request, response, null);
    }

    // Gắn ảnh đại diện hai bên, cờ hết hạn khiếu nại và phí chuyển sang Trao đổi; handoverMedia chỉ truyền ở chi tiết,
    // danh sách để null.
    private RequestResponse addExtraInfo(
            Request request, RequestResponse response, List<RequestMediaResponse> handoverMedia) {
        if (response == null) {
            return null;
        }
        String donorAvatarUrl = findAvatarUrl(response.donorId());
        String receiverAvatarUrl = findAvatarUrl(response.receiverId());
        boolean disputeExpired = isComplaintWindowClosed(response.complaintDeadline());
        Integer conversionFee = conversionFeeOf(response.itemOfferType());
        return response.withExtraInfo(donorAvatarUrl, receiverAvatarUrl, disputeExpired, handoverMedia, conversionFee)
                .withDeadlines(deadlineCalculator.of(request));
    }

    // Phí chủ bài trả nếu giao dịch chuyển sang Trao đổi (CD-02): bài đang GIVE thì trả POST_FEE như khi đăng bài
    // Trao đổi, bài đã là SWAP thì không trả thêm. Không tra được loại bài thì trả null để frontend không hiện sai số.
    private Integer conversionFeeOf(String itemOfferType) {
        if (OfferType.GIVE.name().equals(itemOfferType)) {
            return configService.getInt(ConfigKey.POST_FEE);
        }
        if (OfferType.SWAP.name().equals(itemOfferType)) {
            return 0;
        }
        return null;
    }

    // Hết hạn khiếu nại khi đã có hạn (giao dịch đã hoàn tất) và hiện tại không còn trước hạn đó (U08).
    // complaintDeadline đã tính từ ConfigKey.COMPLAINT_WINDOW_HOURS nên ở đây không đọc lại cấu hình.
    private static boolean isComplaintWindowClosed(Instant complaintDeadline) {
        return complaintDeadline != null && !Instant.now().isBefore(complaintDeadline);
    }

    // userService null chỉ gặp ở unit test dựng service rút gọn; user không còn thì UserService trả null.
    private String findAvatarUrl(Long userId) {
        if (userId == null || userService == null) {
            return null;
        }
        return userService.findAvatarUrl(userId);
    }

    // Hạn giải trình của khiếu nại đang mở thuộc giao dịch (U5-BE, D-04, GN-07).
    // Chỉ có giá trị khi status == DISPUTED, ngược lại là null.
    private Instant findDisputeResponseDueAt(Request request) {
        if (request == null || request.getStatus() != RequestStatus.DISPUTED || request.getId() == null) {
            return null;
        }
        return reportService != null ? reportService.findDisputeResponseDueAt(request.getId()) : null;
    }

    // Khiếu nại theo vai của người xem (D-04). Không có module report (unit test rút gọn) thì trả danh sách rỗng.
    private List<RequestDisputeSummary> findDisputes(Long requestId, long viewerId) {
        if (reportService == null || requestId == null) {
            return List.of();
        }
        return reportService.findDisputesForRequest(requestId, viewerId);
    }

    private Map<Long, List<RequestDisputeSummary>> findDisputesMap(List<Long> requestIds, long viewerId) {
        if (reportService == null || requestIds == null || requestIds.isEmpty()) {
            return Map.of();
        }
        return reportService.findDisputesForRequests(requestIds, viewerId);
    }

    // Gắn khiếu nại theo vai (D-04) và báo cáo từ chối nhận (GN-07) của người xem vào một giao dịch.
    private RequestResponse withReports(RequestResponse response, Long requestId, long viewerId) {
        RequestDisputeSummary refusalReport = requestId == null
                ? null
                : findRefusalMap(List.of(requestId), viewerId).get(requestId);
        return response.withReports(findDisputes(requestId, viewerId), refusalReport);
    }

    private Map<Long, RequestDisputeSummary> findRefusalMap(List<Long> requestIds, long viewerId) {
        if (reportService == null || requestIds == null || requestIds.isEmpty()) {
            return Map.of();
        }
        return reportService.findRefusalReportsForRequests(requestIds, viewerId);
    }

    private static RequestDisputeSummary refusalOf(Map<Long, RequestDisputeSummary> refusalMap, Request request) {
        // Map.of() ném lỗi khi tra khoá null, nên Request chưa có id thì trả null luôn
        return request.getId() == null ? null : refusalMap.get(request.getId());
    }

    private static List<RequestDisputeSummary> disputesOf(
            Map<Long, List<RequestDisputeSummary>> disputesMap, Request request) {
        // Map.of() ném lỗi khi tra khoá null, nên Request chưa có id thì trả rỗng luôn
        if (request.getId() == null) {
            return List.of();
        }
        return disputesMap.getOrDefault(request.getId(), List.of());
    }

    private Map<Long, Instant> findDisputeResponseDueAtMap(List<Long> disputedRequestIds) {
        if (disputedRequestIds == null || disputedRequestIds.isEmpty() || reportService == null) {
            return Map.of();
        }
        return reportService.findDisputeResponseDueAtMap(disputedRequestIds);
    }

    // Tên món đồ, ảnh đầu tiên và tên chủ bài để giao diện hiện tên thay vì id. Cần vì GET /items/{id} chỉ cho xem
    // bài APPROVED hoặc của chính mình, còn người xin thì vẫn phải thấy tên món đã được chọn (RESERVED trở lên).
    // itemCards giữ thẻ bài đã tra trong cùng một lần gọi để danh sách nhiều yêu cầu không tra lại cùng một bài.
    private RequestItemInfo findItemInfo(Request request, Map<Long, ItemCardDto> itemCards) {
        ItemCardDto card = findItemCard(request.getItemId(), itemCards);
        String offeredItemTitle = findItemTitle(request.getOfferedItemId());
        if (card == null) {
            return new RequestItemInfo(null, null, null, null, offeredItemTitle, null);
        }
        String itemOfferType = card.offerType() != null ? card.offerType().name() : null;
        return new RequestItemInfo(
                card.title(),
                card.imageUrl(),
                card.donorId(),
                findUserName(card.donorId()),
                offeredItemTitle,
                itemOfferType);
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

    // Chỉ lấy họ tên để hiển thị; email, số điện thoại chỉ trao qua getContactInfo khi đã chốt lịch (GN-04).
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

    // Lấy thông tin uy tín (TrustStars, hạng, tỉ lệ đúng mô tả) qua ReputationService (YC-06, Issue #110).
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
                            summary.status() != null ? summary.status().name() : null,
                            summary.active()));
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
                    .sql(
                            "SELECT donor_id, offer_type, status, is_active FROM items WHERE id = :id AND is_deleted = false")
                    .param("id", itemId)
                    .query((rs, rowNum) -> new ItemSnapshot(
                            rs.getLong("donor_id"),
                            rs.getString("offer_type"),
                            rs.getString("status"),
                            rs.getBoolean("is_active")))
                    .optional();
        }
        return Optional.empty();
    }

    private Instant findLastMessageAt(Long requestId) {
        if (jdbcClient == null || requestId == null) {
            return null;
        }
        try {
            return jdbcClient
                    .sql("SELECT MAX(created_at) FROM messages WHERE request_id = :requestId")
                    .param("requestId", requestId)
                    .query(Instant.class)
                    .optional()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<Long, Instant> findLastMessageAtMap(List<Long> requestIds) {
        if (jdbcClient == null || requestIds == null || requestIds.isEmpty()) {
            return new HashMap<>();
        }
        List<Long> validIds =
                requestIds.stream().filter(java.util.Objects::nonNull).toList();
        if (validIds.isEmpty()) {
            return new HashMap<>();
        }
        try {
            Map<Long, Instant> map = new HashMap<>();
            jdbcClient
                    .sql(
                            "SELECT request_id, MAX(created_at) AS last_message_at FROM messages WHERE request_id IN (:requestIds) GROUP BY request_id")
                    .param("requestIds", validIds)
                    .query((rs, rowNum) -> {
                        long reqId = rs.getLong("request_id");
                        java.sql.Timestamp ts = rs.getTimestamp("last_message_at");
                        if (ts != null) {
                            map.put(reqId, ts.toInstant());
                        }
                        return null;
                    })
                    .list();
            return map;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    record ItemSnapshot(long donorId, String offerType, String status, boolean isActive) {
        ItemSnapshot(long donorId, String offerType, String status) {
            this(donorId, offerType, status, true);
        }
    }
}
