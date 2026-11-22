package com.shareloop.wallet.repository;

/** Tổng Credit của một bài theo một loại phí; số âm vì là khoản trừ. */
public interface ItemFeeTotalView {
    Long getItemId();

    Long getTotal();
}
