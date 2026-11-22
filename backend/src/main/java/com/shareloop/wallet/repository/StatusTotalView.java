package com.shareloop.wallet.repository;

import com.shareloop.wallet.entity.PaymentOrderStatus;

/** Số đơn và tổng tiền VND của một trạng thái đơn nạp. */
public interface StatusTotalView {
    PaymentOrderStatus getStatus();

    long getOrderCount();

    long getAmountVnd();
}
