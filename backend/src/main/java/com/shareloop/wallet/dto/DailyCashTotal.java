package com.shareloop.wallet.dto;

import java.time.LocalDate;

/**
 * Tiền mặt thực thu của một ngày (giờ Việt Nam) trong báo cáo tài chính, dùng để vẽ biểu đồ thu theo ngày.
 *
 * @param date ngày theo lịch Việt Nam, dạng yyyy-MM-dd
 * @param amountVnd tổng tiền VND của các đơn nạp SUCCESS có paid_at trong ngày
 * @param orderCount số đơn nạp SUCCESS trong ngày
 */
public record DailyCashTotal(LocalDate date, long amountVnd, long orderCount) {}
