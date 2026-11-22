package com.shareloop.request.service;

import com.shareloop.notification.service.NotificationService;
import com.shareloop.request.entity.Request;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.repository.RequestRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Báo cho những người từng bị từ chối (REJECTED) khi bài họ xin mở lại vì giao dịch đã chọn bị huỷ. Dùng chung cho
 * các đường huỷ giao dịch ở RequestService và job huỷ giao dịch kẹt ở RequestJobService.
 */
@Component
public class ItemReopenedNotifier {

    private final RequestRepository requestRepository;
    private final NotificationService notificationService;

    public ItemReopenedNotifier(RequestRepository requestRepository, NotificationService notificationService) {
        this.requestRepository = requestRepository;
        this.notificationService = notificationService;
    }

    /**
     * Gửi thông báo REQUESTED_ITEM_REOPENED cho mỗi người có yêu cầu REJECTED tới bài, mỗi người đúng một lần. Bỏ qua
     * người nhận của giao dịch vừa huỷ vì họ đã nhận thông báo huỷ giao dịch. Không dùng ITEM_REOPENED vì loại đó đã
     * là thông báo cho chủ bài khi bài được hiện lại sau báo cáo (module report).
     *
     * @param itemId bài vừa được nhả về APPROVED
     * @param cancelledReceiverId người nhận của giao dịch vừa huỷ
     */
    public void notifyRejectedRequesters(long itemId, long cancelledReceiverId) {
        List<Request> rejectedRequests = requestRepository.findByItemIdAndStatus(itemId, RequestStatus.REJECTED);
        // Một người có thể bị từ chối nhiều lần ở cùng bài (gửi lại sau lần trước), chỉ báo một lần.
        Set<Long> receiverIds = new LinkedHashSet<>();
        for (Request request : rejectedRequests) {
            receiverIds.add(request.getReceiverId());
        }
        receiverIds.remove(cancelledReceiverId);
        for (Long receiverId : receiverIds) {
            notificationService.notify(
                    receiverId,
                    "REQUESTED_ITEM_REOPENED",
                    "Món bạn từng yêu cầu đã mở lại",
                    "Giao dịch trước của món này đã bị huỷ. Bạn có thể gửi yêu cầu mới nếu vẫn cần.",
                    "/items/" + itemId);
        }
    }
}
