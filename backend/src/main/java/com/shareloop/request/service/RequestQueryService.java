package com.shareloop.request.service;

import com.shareloop.request.entity.Request;
import com.shareloop.request.repository.RequestRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only request rules used by other modules without depending on RequestService. */
@Service
public class RequestQueryService {
    private final RequestRepository repository;

    public RequestQueryService(RequestRepository repository) {
        this.repository = repository;
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
}
