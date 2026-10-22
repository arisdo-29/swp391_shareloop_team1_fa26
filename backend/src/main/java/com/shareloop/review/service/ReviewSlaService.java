package com.shareloop.review.service;

import com.shareloop.notification.service.NotificationService;
import com.shareloop.review.repository.ReviewQueueRepository;
import com.shareloop.setting.ConfigKey;
import com.shareloop.setting.service.ConfigService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cảnh báo Admin khi có bài chờ duyệt quá SLA (FR-121, BR-A02). Mỗi lượt chạy gửi một thông báo tổng hợp "N bài quá
 * SLA" thay vì từng bài, vì không có bảng lưu bài nào đã được cảnh báo; lượt sau còn bài quá hạn thì báo lại.
 */
@Service
public class ReviewSlaService {

    private final ReviewQueueRepository reviewQueueRepository;
    private final ConfigService configService;
    private final NotificationService notificationService;

    public ReviewSlaService(
            ReviewQueueRepository reviewQueueRepository,
            ConfigService configService,
            NotificationService notificationService) {
        this.reviewQueueRepository = reviewQueueRepository;
        this.configService = configService;
        this.notificationService = notificationService;
    }

    /**
     * Đếm bài PENDING_REVIEW đã chờ từ SLA giờ trở lên và báo cho mọi Admin nếu có.
     *
     * @param now mốc tính hạn (job truyền giờ hiện tại, test truyền mốc tự chọn)
     * @return số bài quá SLA; 0 thì không gửi thông báo
     */
    @Transactional
    public long alertOverdueReviews(Instant now) {
        int slaHours = configService.getInt(ConfigKey.REVIEW_SLA_HOURS);
        long overdue = reviewQueueRepository.countPendingReviewWaitingSince(now.minus(slaHours, ChronoUnit.HOURS));
        if (overdue > 0) {
            notificationService.notifyAdmins(
                    "REVIEW_SLA_OVERDUE",
                    "Bài chờ duyệt quá SLA",
                    overdue + " bài đã chờ duyệt từ " + slaHours + " giờ trở lên. Vui lòng xử lý hàng chờ duyệt.",
                    "/admin/moderation");
        }
        return overdue;
    }
}
