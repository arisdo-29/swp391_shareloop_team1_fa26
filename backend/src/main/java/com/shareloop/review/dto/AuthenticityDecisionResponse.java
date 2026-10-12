package com.shareloop.review.dto;

import com.shareloop.review.entity.AuthenticityStatus;

public record AuthenticityDecisionResponse(Long itemId, AuthenticityStatus status, String reason) {}
