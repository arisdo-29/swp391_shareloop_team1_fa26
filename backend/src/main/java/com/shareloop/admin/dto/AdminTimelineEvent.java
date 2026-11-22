package com.shareloop.admin.dto;

import java.time.Instant;

/**
 * Một mốc trên dòng thời gian của giao dịch.
 *
 * @param label tên mốc bằng tiếng Việt, ví dụ "Chủ bài xác nhận đã giao nhận"
 * @param note ghi chú đi kèm (lý do huỷ), null khi không có
 */
public record AdminTimelineEvent(String label, Instant at, String note) {}
