package com.shareloop.request.service;

import com.shareloop.common.exception.BusinessException;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.AdminRequestCore;
import com.shareloop.request.dto.CancelRequestInfo;
import com.shareloop.request.dto.ItemRequestBrief;
import com.shareloop.request.dto.RepeatedCancelerRow;
import com.shareloop.request.dto.RescheduleInfo;
import com.shareloop.request.dto.ShippingInfo;
import com.shareloop.request.entity.CancelRequestCode;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.repository.RequestRepository;
import com.shareloop.setting.service.ConfigService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only request rules used by other modules without depending on RequestService. */
@Service
public class RequestQueryService {
    private final RequestRepository repository;
    private final RequestDeadlineCalculator deadlineCalculator;

    public RequestQueryService(RequestRepository repository, ConfigService configService) {
        this.repository = repository;
        this.deadlineCalculator = new RequestDeadlineCalculator(configService);
    }

    /**
     * Người đề nghị huỷ từ threshold giao dịch trở lên sau khi đã thấy liên hệ, kể từ since (GN-06), cho màn Giám sát
     * bất thường của Admin. Chỉ tính giao dịch đã huỷ vì chính đề nghị đó.
     */
    @Transactional(readOnly = true)
    public List<RepeatedCancelerRow> findRepeatedCancelers(Instant since, int threshold) {
        return repository.findRepeatedCancelers(since, threshold);
    }

    @Transactional(readOnly = true)
    public boolean hasReservedOrHigherRequest(long itemId) {
        return repository.hasReservedOrHigherRequest(itemId);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveDelivery(long userId) {
        return repository.hasActiveDelivery(userId);
    }

    /**
     * Kiểm tra người dùng có phải là một bên tham gia trong giao dịch đang diễn ra của bài đăng hay không (G06).
     * Bài chính hoặc món mang đổi trong giao dịch đã được chọn (RESERVED trở lên) thì cả hai bên đều xem được chi tiết.
     */
    @Transactional(readOnly = true)
    public boolean isParticipantInActiveTransaction(long itemId, long userId) {
        return repository.isParticipantInActiveRequest(itemId, userId);
    }

    /**
     * Người dùng từng gửi yêu cầu tới bài này chưa, bất kể yêu cầu đang ở trạng thái nào. Người không được chọn vẫn xem
     * lại được bài mình đã xin ở chế độ chỉ xem. Người gọi: item.
     */
    @Transactional(readOnly = true)
    public boolean hasRequestedItem(long itemId, long userId) {
        return repository.existsByItemIdAndReceiverId(itemId, userId);
    }

    /**
     * Số giao dịch COMPLETED mà người dùng tham gia, tính cả vai cho và vai nhận. Dùng cho ô "Giao dịch hoàn tất"
     * trên hồ sơ công khai. Người gọi: user.
     */
    @Transactional(readOnly = true)
    public long countCompletedByUser(long userId) {
        return repository.countCompletedByUser(userId);
    }

    /**
     * Thời điểm hoàn tất giao dịch theo id bài, để hiện ngày trên thẻ bài đã giao dịch. Bài là bài chính hoặc món đề
     * nghị của giao dịch trao đổi đều tính. Bài chưa có giao dịch COMPLETED thì không có trong map. Người gọi: item.
     */
    @Transactional(readOnly = true)
    public Map<Long, Instant> findCompletedAtByItemIds(Collection<Long> itemIds) {
        Map<Long, Instant> completedAtByItemId = new HashMap<>();
        if (itemIds == null || itemIds.isEmpty()) {
            return completedAtByItemId;
        }
        for (Request request : repository.findCompletedByItemIds(itemIds)) {
            completedAtByItemId.put(request.getItemId(), request.getCompletedAt());
            if (request.getOfferedItemId() != null) {
                completedAtByItemId.put(request.getOfferedItemId(), request.getCompletedAt());
            }
        }
        return completedAtByItemId;
    }

    /** Số yêu cầu đang chờ chọn (PENDING) của từng bài. Bài không có yêu cầu nào thì không có trong map. */
    @Transactional(readOnly = true)
    public Map<Long, Long> countPendingByItemIds(Collection<Long> itemIds) {
        Map<Long, Long> counts = new HashMap<>();
        if (itemIds == null || itemIds.isEmpty()) {
            return counts;
        }
        for (Request request : repository.findByItemIdInAndStatusIn(itemIds, List.of(RequestStatus.PENDING))) {
            counts.merge(request.getItemId(), 1L, Long::sum);
        }
        return counts;
    }

    /** Mọi yêu cầu tới một bài, mới nhất trước, cho trang chi tiết bài của Admin. */
    @Transactional(readOnly = true)
    public List<ItemRequestBrief> findBriefsByItem(long itemId) {
        List<ItemRequestBrief> briefs = new ArrayList<>();
        for (Request request : repository.findByItemIdOrderByCreatedAtDesc(itemId)) {
            briefs.add(new ItemRequestBrief(
                    request.getId(),
                    request.getReceiverId(),
                    request.getType(),
                    request.getStatus().name(),
                    request.getOfferedItemId(),
                    request.getCreatedAt()));
        }
        return briefs;
    }

    /**
     * Toàn bộ mốc và thông tin giao nhận của một giao dịch cho trang chi tiết của Admin.
     *
     * @throws BusinessException REQUEST_NOT_FOUND khi không có giao dịch
     */
    @Transactional(readOnly = true)
    public AdminRequestCore getAdminCore(long requestId) {
        Request request = repository
                .findById(requestId)
                .orElseThrow(() -> new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));
        return new AdminRequestCore(
                request.getId(),
                request.getItemId(),
                request.getReceiverId(),
                request.getOfferedItemId(),
                request.getType(),
                request.getStatus().name(),
                request.getDeliveryMethod(),
                request.getMeetingPlace(),
                request.getMeetingTime(),
                request.getDonorWaiverAt(),
                request.getReceiverWaiverAt(),
                request.getDonorScheduleOkAt(),
                request.getReceiverScheduleOkAt(),
                request.getContactRevealedAt(),
                request.getDonorHandoverAt(),
                request.getReceiverHandoverAt(),
                request.isAutoConfirmed(),
                request.getReservedAt(),
                request.getCompletedAt(),
                request.getCancelReason(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                adminCancelRequestInfo(request),
                new RescheduleInfo(
                        request.getRescheduleProposedBy(),
                        request.getRescheduleProposedAt(),
                        request.getRescheduleMeetingTime(),
                        request.getRescheduleMeetingPlace(),
                        request.getRescheduleCount()),
                adminShippingInfo(request),
                deadlineCalculator.of(request));
    }

    // Admin thấy đề nghị huỷ cả khi đã xử lý (giao dịch huỷ hoặc sang DISPUTED), không chỉ khi đang chờ.
    private static CancelRequestInfo adminCancelRequestInfo(Request request) {
        if (request.getCancelRequestedBy() == null) {
            return CancelRequestInfo.EMPTY;
        }
        CancelRequestCode code = request.getCancelRequestCode();
        return new CancelRequestInfo(
                request.getCancelRequestedBy(),
                request.getCancelRequestedAt(),
                code == null ? null : code.name(),
                code == null ? null : code.label(),
                request.getCancelRequestReason());
    }

    // Admin thấy đủ địa chỉ ở mọi trạng thái.
    private static ShippingInfo adminShippingInfo(Request request) {
        if (!request.isShipping()) {
            return ShippingInfo.EMPTY;
        }
        return new ShippingInfo(
                request.getReceiverAddress(),
                request.getDonorAddress(),
                request.getDonorShippedAt(),
                request.getReceiverShippedAt(),
                request.getReceiverAddress() != null,
                request.getDonorAddress() != null);
    }
}
