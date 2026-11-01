package com.shareloop.listingfee.repository;

import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.listingfee.entity.ItemFeeState;
import com.shareloop.listingfee.entity.ItemFeeType;

/**
 * Ảnh chụp chỉ-đọc của cột phí một bài, dùng để báo lỗi sớm trước khi khoá dòng và để tính báo giá sửa bài.
 * Interface projection: Spring Data tự tạo đối tượng từ kết quả query (khớp tên alias với tên getter), không phải
 * entity nên không vào persistence context.
 */
public interface ItemFeeSnapshot {

    Long getDonorId();

    ItemFeeState getFeeState();

    int getPendingFee();

    // null khi bài chưa từng giữ phí.
    ItemFeeType getPendingFeeType();

    // approved_at khác null: bài đã từng được duyệt ít nhất một lần (R36: xét cột này, không xét status hiện tại).
    boolean getEverApproved();

    boolean getFreeEditUsed();

    boolean getPostFeePaid();

    // Cột status thuộc module item; listingfee chỉ đọc để kiểm bài còn được gia hạn không, không bao giờ ghi.
    ItemStatus getStatus();

    // Cột category_id thuộc module item; chỉ đọc để hỏi catalog danh mục có cho đẩy bài không.
    Long getCategoryId();

    // is_active = false: bài đang bị ẩn (do report hoặc Admin), không được đẩy (UC-16).
    boolean getActive();

    // Cột offer_type thuộc module item; chỉ đọc để tính phí đăng: GIVE miễn phí, SWAP mất post_fee.
    OfferType getOfferType();
}
