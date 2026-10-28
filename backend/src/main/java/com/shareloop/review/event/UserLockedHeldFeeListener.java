package com.shareloop.review.event;

import com.shareloop.listingfee.dto.HeldFeeItem;
import com.shareloop.listingfee.service.ListingFeeQueryService;
import com.shareloop.review.service.ReviewService;
import com.shareloop.user.event.UserLockedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Từ chối các bài chưa duyệt đang giữ phí của tài khoản vừa bị khoá và nhả phí về ví, đúng cơ chế khi Admin từ chối bài
 * (ReviewService.reject: bài sang REJECTED rồi nhả phí qua CreditService.release).
 */
@Component
public class UserLockedHeldFeeListener {

    static final String LOCK_REASON = "Tài khoản bị khoá";
    private static final String PENDING_REVIEW = "PENDING_REVIEW";

    private final ListingFeeQueryService listingFeeQueryService;
    private final ReviewService reviewService;

    public UserLockedHeldFeeListener(ListingFeeQueryService listingFeeQueryService, ReviewService reviewService) {
        this.listingFeeQueryService = listingFeeQueryService;
        this.reviewService = reviewService;
    }

    // @EventListener thường: chạy cùng transaction với lúc khoá, lỗi thì cả việc khoá cũng rollback.
    @EventListener
    public void onUserLocked(UserLockedEvent event) {
        for (HeldFeeItem heldItem : listingFeeQueryService.findHeldFees(event.userId())) {
            // Bài đã duyệt không còn ở hàng chờ nên không từ chối được; findHeldFees chỉ trả bài đang giữ phí
            if (!PENDING_REVIEW.equals(heldItem.status())) {
                continue;
            }
            reviewService.reject(heldItem.itemId(), LOCK_REASON, event.adminId());
            event.cleanup().addReleasedCredit(heldItem.pendingFee());
        }
    }
}
