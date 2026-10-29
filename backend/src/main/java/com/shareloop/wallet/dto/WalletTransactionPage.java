package com.shareloop.wallet.dto;

import java.util.List;

/**
 * Một trang lịch sử ví của người dùng, kèm số dòng theo tab. Có cùng bốn trường với PageResponse nên FE đọc như cũ;
 * trường thêm {@code counts} chỉ dành cho lịch sử "của tôi" (PageResponse nằm ở common/, dùng chung nên không thêm vào đó).
 */
public record WalletTransactionPage(
        List<WalletTransactionResponse> items, int page, int size, long totalElements, WalletHistoryCounts counts) {}
