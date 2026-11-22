package com.shareloop.wallet.dto;

import java.util.List;

/**
 * Một trang sổ cái kèm tổng trong cả khoảng lọc (không chỉ trang này).
 *
 * @param totalIn tổng Credit cộng vào (dòng dương)
 * @param totalOut tổng Credit trừ ra, giữ dấu âm
 */
public record AdminLedgerPage(
        List<AdminLedgerRow> items, int page, int size, long totalElements, long totalIn, long totalOut) {}
