package com.shareloop.review.dto;

import java.time.Instant;

public record ReviewQueueItem(
        Long id,
        Long donorId,
        String title,
        String aiRisk,
        String moderationFlag,
        String flagReason,
        short donorTrustStars,
        Instant createdAt) {}
