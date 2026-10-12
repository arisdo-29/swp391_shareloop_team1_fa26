package com.shareloop.listingfee.entity;

/** Trạng thái khoản phí của bài ({@code items.fee_state}); đúng 4 giá trị của CHECK trong V1__init.sql. */
public enum ItemFeeState {
    /** Chưa giữ khoản phí nào (bài mới tạo, hoặc lượt sửa miễn phí). */
    NONE,
    /** Đang giữ Credit chờ Admin duyệt; chỉ trạng thái này mới được trừ hoặc nhả. */
    HELD,
    /** Admin đã duyệt, khoản giữ đã bị trừ và ghi credit_ledger. */
    CHARGED,
    /** Bị từ chối hoặc chủ bài rút/xoá bài, khoản giữ đã được nhả lại. */
    RELEASED
}
