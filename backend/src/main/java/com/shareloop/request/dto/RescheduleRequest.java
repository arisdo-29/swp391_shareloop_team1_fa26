package com.shareloop.request.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * DTO đề nghị dời lịch sau khi chốt (GN-11).
 *
 * @param meetingTime giờ hẹn mới, với Giao hàng là ngày gửi hàng dự kiến mới
 * @param meetingPlace địa điểm mới khi Gặp trực tiếp, bỏ trống nếu không đổi
 */
public record RescheduleRequest(
        @NotNull(message = "Thời gian hẹn không được để trống")
        Instant meetingTime,

        @Size(max = 255, message = "Địa điểm tối đa 255 ký tự.")
        String meetingPlace) {}
