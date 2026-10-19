package com.shareloop.listingfee.repository;

import java.time.Instant;

/**
 * Một dòng kết quả của {@code ItemFeeRepository.findDisplayWindowRows}: hạn hiển thị và hạn đẩy của một bài.
 * Interface projection: Spring Data tự tạo đối tượng từ kết quả query (khớp tên alias với tên getter).
 */
public interface ItemDisplayWindowRow {

    Long getId();

    // null khi bài chưa từng được duyệt.
    Instant getExpireAt();

    // null khi bài chưa từng được đẩy.
    Instant getBoostedUntil();
}
