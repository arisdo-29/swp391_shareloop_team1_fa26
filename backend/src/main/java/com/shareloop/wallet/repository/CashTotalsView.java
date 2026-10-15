package com.shareloop.wallet.repository;

/**
 * Kết quả tổng hợp đơn nạp đã thanh toán trong một khoảng ngày: số đơn, tổng tiền VND, tổng Credit của các đơn.
 * Interface projection như {@link PaymentOrderSnapshot}; truy vấn không GROUP BY nên luôn trả đúng một dòng.
 */
public interface CashTotalsView {

    long getOrderCount();

    long getAmountVnd();

    long getCredits();
}
