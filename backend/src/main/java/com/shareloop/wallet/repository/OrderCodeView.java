package com.shareloop.wallet.repository;

/** Id và mã của một đơn nạp, để gắn mã đơn vào dòng sổ cái mà không tải cả đơn. */
public interface OrderCodeView {
    Long getId();

    String getOrderCode();
}
