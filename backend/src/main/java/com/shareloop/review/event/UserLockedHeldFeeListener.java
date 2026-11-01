package com.shareloop.review.event;

import com.shareloop.listingfee.dto.HeldFeeItem;
import com.shareloop.listingfee.service.ListingFeeQueryService;
import com.shareloop.review.service.ReviewService;
import com.shareloop.user.event.UserLockedEvent;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Từ chối mọi bài chờ duyệt của tài khoản vừa bị khoá hoặc bị cấm (L5), kể cả bài Cho tặng và bản sửa miễn phí, rồi nhả
 * phí đang giữ về ví, đúng cơ chế khi Admin từ chối bài (ReviewService: bài sang REJECTED, nhả phí qua
 * CreditService.release). Không bài nào của người bị khoá còn nằm trong hàng chờ để Admin lỡ duyệt.
 */
@Component
public class UserLockedHeldFeeListener {

    static final String LOCK_REASON = "[Hệ thống] Tài khoản bị khoá";

    private final ListingFeeQueryService listingFeeQueryService;
    private final ReviewService reviewService;

    public UserLockedHeldFeeListener(ListingFeeQueryService listingFeeQueryService, ReviewService reviewService) {
        this.listingFeeQueryService = listingFeeQueryService;
        this.reviewService = reviewService;
    }

    // @EventListener thường: chạy cùng transaction với lúc khoá, lỗi thì cả việc khoá cũng rollback.
    @EventListener
    @Order(20)
    public void onUserLocked(UserLockedEvent event) {
        // Đọc phí đang giữ TRƯỚC khi từ chối: từ chối xong thì phí đã nhả, không còn trong danh sách giữ phí
        Map<Long, Integer> heldFeeByItemId = new HashMap<>();
        for (HeldFeeItem heldItem : listingFeeQueryService.findHeldFees(event.userId())) {
            heldFeeByItemId.put(heldItem.itemId(), heldItem.pendingFee());
        }

        for (Long itemId : reviewService.rejectPendingItemsOfUser(event.userId(), LOCK_REASON, event.adminId())) {
            event.cleanup().addReleasedCredit(heldFeeByItemId.getOrDefault(itemId, 0));
        }
    }
}
