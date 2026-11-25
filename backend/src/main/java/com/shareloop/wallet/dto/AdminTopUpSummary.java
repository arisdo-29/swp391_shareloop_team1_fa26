package com.shareloop.wallet.dto;

import com.shareloop.wallet.entity.PaymentOrderStatus;
import java.util.List;

/**
 * Khối tổng hợp trên đầu trang Giao dịch nạp tiền.
 *
 * @param byStatus số đơn và tổng tiền theo từng trạng thái, đủ cả năm trạng thái
 * @param needsReconcileCount số đơn cần đối soát trong khoảng ngày
 */
public record AdminTopUpSummary(List<StatusTotal> byStatus, long needsReconcileCount) {

    /** Số đơn và tổng tiền VND của một trạng thái. */
    public record StatusTotal(PaymentOrderStatus status, long orderCount, long amountVnd) {}
}
