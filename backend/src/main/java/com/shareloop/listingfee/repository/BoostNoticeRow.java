package com.shareloop.listingfee.repository;

import java.time.Instant;

/**
 * Một dòng kết quả của các query tìm bài cần thông báo về đợt đẩy (BoostNotificationService). Interface projection:
 * Spring Data tự tạo đối tượng từ kết quả query (khớp tên alias với tên getter).
 */
public interface BoostNoticeRow {

    Long getId();

    Long getDonorId();

    String getTitle();

    Instant getBoostedUntil();

    // Đã lấy last_boosted_at thay khi bài đẩy trước lúc có cột boost_started_at.
    Instant getBoostStartedAt();

    // null với bài đẩy rất cũ (trước khi có cột last_boosted_at).
    Instant getLastBoostedAt();
}
