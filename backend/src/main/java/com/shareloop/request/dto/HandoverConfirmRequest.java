package com.shareloop.request.dto;

/**
 * DTO yêu cầu xác nhận đã giao / đã nhận đồ (FR-84, FR-85, BR-D02, BR-R05, UC-27).
 *
 * @param asDescribed người nhận đánh giá món đồ có đúng như mô tả không (bắt buộc khi là bên nhận)
 * @param notAsDescribedReason lý do không đúng mô tả nếu asDescribed là false
 */
public record HandoverConfirmRequest(Boolean asDescribed, String notAsDescribedReason) {}
