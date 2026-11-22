package com.shareloop.listingfee.service;

import com.shareloop.audit.ActivityType;
import com.shareloop.audit.service.ActivityLogService;
import com.shareloop.listingfee.entity.ItemFee;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cộng bù thời gian hiển thị cho chủ bài khi bài phải nằm chờ vì lý do không phải lỗi của họ. Gom một chỗ để mọi
 * đường dùng chung cách cộng và cách ghi nhật ký.
 *
 * <p>Hiện có một đường: bản sửa của bài đã duyệt được duyệt lại thì cộng số giờ chờ duyệt (B1). Các hàm nhận ItemFee
 * đã được người gọi khoá dòng.
 */
@Service
// REQUIRED: chạy trong transaction của người gọi (duyệt bài), lỗi thì hoàn tác cả khoản cộng bù.
@Transactional
public class DisplayTimeService {

    private final Clock clock;
    private final ActivityLogService activityLogService;

    public DisplayTimeService(Clock clock, ActivityLogService activityLogService) {
        this.clock = clock;
        this.activityLogService = activityLogService;
    }

    /**
     * Bản sửa của bài đã từng duyệt vừa được duyệt (B1, BD-09): cộng số giờ từ lúc gửi bản sửa tới giờ vào hạn hiển
     * thị, và vào hạn đẩy nếu đợt đẩy còn chạy lúc gửi. Xoá mốc sau khi cộng. Bài duyệt lần đầu không có mốc nên
     * không được cộng gì.
     */
    public void compensateEditReview(ItemFee itemFee) {
        Instant submittedAt = itemFee.getEditSubmittedAt();
        if (submittedAt == null) {
            return;
        }
        Duration waited = Duration.between(submittedAt, clock.instant());
        itemFee.clearEditSubmitted();
        if (waited.isNegative() || waited.isZero()) {
            return;
        }
        itemFee.addDisplayTime(waited, submittedAt);
        activityLogService.log(
                ActivityType.ITEM_DISPLAY_COMPENSATED.name(),
                null,
                "ITEM",
                itemFee.getId(),
                Map.of("hours", waited.toHours(), "reason", "EDIT_REVIEW"),
                "Cộng bù " + waited.toHours() + " giờ chờ duyệt bản sửa vào hạn hiển thị");
    }
}
