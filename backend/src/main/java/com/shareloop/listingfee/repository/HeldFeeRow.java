package com.shareloop.listingfee.repository;

import java.time.Instant;

/**
 * Một dòng kết quả của {@code ItemFeeRepository.findHeldFeeRows}: một bài đang giữ phí của chủ bài.
 * Interface projection: Spring Data tự tạo đối tượng từ kết quả query (khớp tên alias với tên getter).
 */
public interface HeldFeeRow {

    Long getId();

    String getTitle();

    String getOfferType();

    String getStatus();

    int getPendingFee();

    // POST hoặc EDIT.
    String getPendingFeeType();

    Instant getUpdatedAt();
}
