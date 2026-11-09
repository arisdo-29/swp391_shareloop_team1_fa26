package com.shareloop.request.dto;

/**
 * DTO xác nhận lịch hẹn giao nhận và cam kết trách nhiệm (GN-02, GN-03).
 */
public record ConfirmLogisticsRequest(Boolean agreeToWaiver) {}
