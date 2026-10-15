package com.shareloop.request.dto;

/**
 * DTO xác nhận lịch hẹn giao nhận và cam kết trách nhiệm (FR-80, FR-81, BR-T03, BR-T05).
 */
public record ConfirmLogisticsRequest(Boolean agreeToWaiver) {}
