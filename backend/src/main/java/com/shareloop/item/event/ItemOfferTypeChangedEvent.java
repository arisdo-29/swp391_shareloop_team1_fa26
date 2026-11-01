package com.shareloop.item.event;

/** Được phát trong transaction sửa bài khi chủ bài đổi giữa Cho tặng và Trao đổi. */
public record ItemOfferTypeChangedEvent(long itemId) {}
