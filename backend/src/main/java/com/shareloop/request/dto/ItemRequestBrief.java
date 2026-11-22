package com.shareloop.request.dto;

import java.time.Instant;

/** Tóm tắt một yêu cầu tới một bài, cho trang chi tiết bài của Admin. */
public record ItemRequestBrief(
        long id, long receiverId, String type, String status, Long offeredItemId, Instant createdAt) {}
