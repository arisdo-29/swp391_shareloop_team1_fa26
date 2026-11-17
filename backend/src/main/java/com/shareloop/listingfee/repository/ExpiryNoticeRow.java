package com.shareloop.listingfee.repository;

import java.time.Instant;

/**
 * Một dòng kết quả của query tìm bài sắp hết hạn hiển thị (ItemExpiryNotificationService). Interface projection: Spring
 * Data tự tạo đối tượng từ kết quả query (khớp tên alias với tên getter).
 */
public interface ExpiryNoticeRow {

    Long getId();

    Long getDonorId();

    String getTitle();

    Instant getExpireAt();
}
