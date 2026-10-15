package com.shareloop.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * DTO đề xuất / cập nhật lịch hẹn giao nhận (FR-79, FR-80, UC-26).
 */
public record ScheduleRequest(
        @NotBlank(message = "Hình thức giao nhận không được để trống")
        @Pattern(regexp = "IN_PERSON|SHIPPING", message = "Hình thức giao nhận phải là IN_PERSON hoặc SHIPPING")
        String deliveryMethod,

        @NotBlank(message = "Địa điểm không được để trống") @Size(max = 255, message = "Địa điểm tối đa 255 ký tự")
        String meetingPlace,

        @NotNull(message = "Thời gian hẹn không được để trống")
        Instant meetingTime,

        Boolean agreeToWaiver) {}
