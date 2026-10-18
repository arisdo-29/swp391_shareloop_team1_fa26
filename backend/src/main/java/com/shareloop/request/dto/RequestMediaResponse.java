package com.shareloop.request.dto;

import java.time.Instant;

/**
 * DTO phản hồi thông tin ảnh/video minh chứng của yêu cầu giao dịch (FR-86, BR-T09).
 */
public record RequestMediaResponse(Long mediaId, String url, String stage, Long uploadedBy, Instant createdAt) {}
