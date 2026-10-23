package com.shareloop.wallet.repository;

import java.time.Instant;

/** Thời điểm thanh toán và số tiền của một đơn nạp đã thanh toán; dùng để gom tiền thu theo ngày. */
public interface PaidOrderView {

    Instant getPaidAt();

    long getAmountVnd();
}
