package com.shareloop.request.service;

import com.shareloop.item.dto.ItemSummary;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.item.service.ItemService;
import com.shareloop.notification.service.NotificationService;
import com.shareloop.reputation.service.ReputationService;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Service xử lý các tác vụ nền tự động theo vòng đời Request (Milestone R4, Issue #109):
 * <ul>
 *   <li>Huỷ tự động các yêu cầu PENDING quá hạn (FR-119, BR-R06).</li>
 *   <li>Huỷ tự động các yêu cầu RESERVED/AWAITING_LOGISTICS quá hạn chưa chốt lịch và đóng chat (FR-120, BR-T10, FR-77, BR-C03).</li>
 *   <li>Tự động hoàn tất (COMPLETED) khi một bên đã xác nhận giao nhận mà bên kia im lặng quá hạn (FR-87, BR-T06).</li>
 * </ul>
 */
@Service
public class RequestJobService {

    private static final Logger log = LoggerFactory.getLogger(RequestJobService.class);

    private final RequestRepository requestRepository;
    private final ItemService itemService;
    private final NotificationService notificationService;
    private final ConfigService configService;
    private final ReputationService reputationService;
    private final ItemQueryService itemQueryService;
    private final JdbcClient jdbcClient;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public RequestJobService(
            RequestRepository requestRepository,
            ItemService itemService,
            NotificationService notificationService,
            ConfigService configService,
            @Autowired(required = false) ReputationService reputationService,
            @Autowired(required = false) ItemQueryService itemQueryService,
            @Autowired(required = false) JdbcClient jdbcClient,
            TransactionTemplate transactionTemplate) {
        this.requestRepository = requestRepository;
        this.itemService = itemService;
        this.notificationService = notificationService;
        this.configService = configService;
        this.reputationService = reputationService;
        this.itemQueryService = itemQueryService;
        this.jdbcClient = jdbcClient;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Tự động huỷ các yêu cầu PENDING quá hạn do chủ bài chưa chọn (FR-119, BR-R06).
     *
     * @param now thời điểm hiện tại
     * @return số lượng yêu cầu đã huỷ
     */
    public int expirePendingRequests(Instant now) {
        int pendingDays = configService.getInt(ConfigKey.REQUEST_PENDING_DAYS);
        Instant cutoff = now.minus(pendingDays, ChronoUnit.DAYS);

        List<Request> expiredList = requestRepository.findByStatusAndCreatedAtBefore(RequestStatus.PENDING, cutoff);
        int count = 0;
        for (Request request : expiredList) {
            if (processInOwnTransaction(() -> expirePendingInTransaction(request.getId(), cutoff))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Tự động huỷ các yêu cầu RESERVED hoặc AWAITING_LOGISTICS quá 10 ngày chưa chốt lịch (FR-120, BR-T10, FR-77, BR-C03).
     * Đóng khung chat (do Request sang CANCELLED) và nhả bài đăng về APPROVED qua ItemService.release.
     *
     * @param now thời điểm hiện tại
     * @return số lượng yêu cầu đã huỷ
     */
    public int expireSilentReservedRequests(Instant now) {
        int chatDays = configService.getInt(ConfigKey.CHAT_OPEN_DAYS);
        Instant cutoff = now.minus(chatDays, ChronoUnit.DAYS);

        List<Request> silentList = requestRepository.findSilentReservedRequests(cutoff);
        int count = 0;
        for (Request request : silentList) {
            if (processInOwnTransaction(() -> expireSilentReservedInTransaction(request.getId(), cutoff))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Tự động xác nhận hoàn tất giao dịch (COMPLETED) khi một bên đã xác nhận mà bên kia im lặng quá 3 ngày (FR-87, BR-T06).
     *
     * @param now thời điểm hiện tại
     * @return số lượng yêu cầu đã tự động hoàn tất
     */
    public int autoConfirmHandoverRequests(Instant now) {
        int autoConfirmDays = configService.getInt(ConfigKey.AUTO_CONFIRM_DAYS);
        Instant cutoff = now.minus(autoConfirmDays, ChronoUnit.DAYS);

        List<Request> overdueList = requestRepository.findOverdueHandoverRequests(cutoff);
        int count = 0;
        for (Request request : overdueList) {
            if (processInOwnTransaction(() -> autoConfirmInTransaction(request.getId(), cutoff, now))) {
                count++;
            }
        }
        return count;
    }

    private boolean expirePendingInTransaction(long requestId, Instant cutoff) {
        Request request = requestRepository.findById(requestId).orElse(null);
        if (request == null) {
            return false;
        }

        if (request.getStatus() != RequestStatus.PENDING
                || !request.getCreatedAt().isBefore(cutoff)) {
            return false;
        }

        request.cancel("Hết hạn do chủ bài chưa chọn");
        requestRepository.save(request);

        notificationService.notify(
                request.getReceiverId(),
                "REQUEST_EXPIRED",
                "Yêu cầu hết hạn",
                "Yêu cầu giao dịch của bạn đã tự động hết hạn do chủ bài không phản hồi trong 3 ngày.",
                "/items/" + request.getItemId());

        return true;
    }

    private boolean expireSilentReservedInTransaction(long requestId, Instant cutoff) {
        Request request = requestRepository.findById(requestId).orElse(null);
        if (request == null) {
            return false;
        }

        boolean validStatus = request.getStatus() == RequestStatus.RESERVED
                || request.getStatus() == RequestStatus.AWAITING_LOGISTICS;
        if (!validStatus
                || request.getReservedAt() == null
                || !request.getReservedAt().isBefore(cutoff)) {
            return false;
        }

        request.cancel("Huỷ tự động do quá hạn 10 ngày không chốt lịch");
        requestRepository.save(request);

        // Nhả giữ chỗ bài đăng về APPROVED (BR-T10)
        itemService.release(request.getItemId());

        Long donorId = findDonorId(request.getItemId());
        if (donorId != null) {
            notificationService.notify(
                    donorId,
                    "REQUEST_CANCELLED",
                    "Giao dịch bị huỷ tự động",
                    "Giao dịch đã tự động huỷ do quá 10 ngày không chốt lịch hẹn. Bài đăng đã mở lại.",
                    "/items/" + request.getItemId());
        }
        notificationService.notify(
                request.getReceiverId(),
                "REQUEST_CANCELLED",
                "Giao dịch bị huỷ tự động",
                "Giao dịch đã tự động huỷ do quá 10 ngày không chốt lịch hẹn.",
                "/items/" + request.getItemId());

        return true;
    }

    private boolean autoConfirmInTransaction(long requestId, Instant cutoff, Instant now) {
        Request request = requestRepository.findById(requestId).orElse(null);
        if (request == null) {
            return false;
        }

        if (request.getStatus() != RequestStatus.AWAITING_HANDOVER) {
            return false;
        }

        boolean donorOverdue = request.getDonorHandoverAt() != null
                && request.getReceiverHandoverAt() == null
                && request.getDonorHandoverAt().isBefore(cutoff);
        boolean receiverOverdue = request.getReceiverHandoverAt() != null
                && request.getDonorHandoverAt() == null
                && request.getReceiverHandoverAt().isBefore(cutoff);

        if (!donorOverdue && !receiverOverdue) {
            return false;
        }

        request.autoConfirm(now);
        requestRepository.save(request);

        // Chuyển bài đăng sang TRADED (FR-87, #94)
        itemService.markTraded(request.getItemId());
        if ("SWAP".equalsIgnoreCase(request.getType()) && request.getOfferedItemId() != null) {
            itemService.markTraded(request.getOfferedItemId());
        }

        // Tích luỹ điểm uy tín nếu có module Reputation (FR-91, FR-94)
        if (reputationService != null) {
            reputationService.onCompleted(request.getId());
        }

        Long donorId = findDonorId(request.getItemId());
        if (donorId != null) {
            notificationService.notify(
                    donorId,
                    "TRANSACTION_COMPLETED",
                    "Giao dịch đã tự động hoàn tất",
                    "Giao dịch đã được hệ thống tự động hoàn tất do đối phương không phản hồi sau 3 ngày.",
                    "/requests/" + request.getId());
        }
        notificationService.notify(
                request.getReceiverId(),
                "TRANSACTION_COMPLETED",
                "Giao dịch đã tự động hoàn tất",
                "Giao dịch đã được hệ thống tự động hoàn tất do đối phương không phản hồi sau 3 ngày.",
                "/requests/" + request.getId());

        return true;
    }

    private boolean processInOwnTransaction(TransactionSupplier action) {
        try {
            Boolean result = transactionTemplate.execute(status -> action.execute());
            return Boolean.TRUE.equals(result);
        } catch (RuntimeException exception) {
            log.warn(
                    "Request background processing failed in individual transaction, skipping: {}",
                    exception.getMessage(),
                    exception);
            return false;
        }
    }

    private Long findDonorId(long itemId) {
        if (itemQueryService != null) {
            try {
                ItemSummary summary = itemQueryService.getSummary(itemId);
                if (summary != null) {
                    return summary.donorId();
                }
            } catch (Exception ignored) {
            }
        }
        if (jdbcClient != null) {
            return jdbcClient
                    .sql("SELECT donor_id FROM items WHERE id = :id AND is_deleted = false")
                    .param("id", itemId)
                    .query(Long.class)
                    .optional()
                    .orElse(null);
        }
        return null;
    }

    @FunctionalInterface
    private interface TransactionSupplier {
        boolean execute();
    }
}
