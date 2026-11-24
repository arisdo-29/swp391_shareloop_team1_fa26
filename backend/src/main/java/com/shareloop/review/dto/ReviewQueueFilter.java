package com.shareloop.review.dto;

public record ReviewQueueFilter(String q, Long categoryId, RiskSignal risk, Boolean edited) {
    public enum RiskSignal {
        LOW_TRUST,
        REPEATED_PAIR,
        NEWCOMER
    }
}
