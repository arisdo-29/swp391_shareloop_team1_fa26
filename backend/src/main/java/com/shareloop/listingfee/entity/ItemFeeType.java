package com.shareloop.listingfee.entity;

/** Loại khoản phí đang giữ ({@code items.pending_fee_type}); quyết định loại giao dịch ghi ledger khi trừ. */
public enum ItemFeeType {
    /** Phí đăng bài (C01, C02), trừ thành CreditTxType.POST_FEE. */
    POST,
    /** Phí sửa bài (C14b), trừ thành CreditTxType.EDIT_FEE. */
    EDIT
}
