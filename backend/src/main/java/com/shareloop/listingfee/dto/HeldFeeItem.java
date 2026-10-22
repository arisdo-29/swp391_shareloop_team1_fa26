package com.shareloop.listingfee.dto;

/**
 * Một bài đang giữ phí của người dùng (FR-15), hiện ở màn Ví.
 *
 * @param itemId id bài
 * @param title tên bài
 * @param offerType GIVE hoặc SWAP
 * @param status trạng thái bài hiện tại
 * @param pendingFee số Credit đang giữ cho bài này
 * @param feeType POST (phí đăng) hoặc EDIT (phí sửa)
 */
public record HeldFeeItem(Long itemId, String title, String offerType, String status, int pendingFee, String feeType) {}
