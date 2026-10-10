package com.shareloop.listingfee.repository;

import com.shareloop.listingfee.entity.ItemFeeState;
import com.shareloop.listingfee.entity.ItemFeeType;

/**
 * Ảnh chụp chỉ-đọc của cột phí một bài, dùng để báo lỗi sớm trước khi khoá dòng. Interface projection: Spring Data
 * tự tạo đối tượng từ kết quả query (khớp tên alias với tên getter), không phải entity nên không vào persistence
 * context.
 */
public interface ItemFeeSnapshot {

    Long getDonorId();

    ItemFeeState getFeeState();

    int getPendingFee();

    // null khi bài chưa từng giữ phí.
    ItemFeeType getPendingFeeType();
}
