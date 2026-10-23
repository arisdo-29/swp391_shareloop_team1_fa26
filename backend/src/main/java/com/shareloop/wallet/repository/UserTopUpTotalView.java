package com.shareloop.wallet.repository;

/** Tổng Credit các đơn nạp đã thanh toán của một người dùng; interface projection như {@link CashTotalsView}. */
public interface UserTopUpTotalView {

    Long getUserId();

    long getCredits();
}
