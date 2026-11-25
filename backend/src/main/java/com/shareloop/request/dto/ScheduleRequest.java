package com.shareloop.request.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * DTO đề xuất / cập nhật lịch hẹn giao nhận (GN-01, GN-02, UC-26).
 *
 * <p>Gặp trực tiếp: meetingPlace bắt buộc, meetingTime là giờ hẹn. Giao hàng: meetingPlace không dùng, meetingTime là
 * ngày gửi hàng dự kiến và shippingAddress là địa chỉ nhận hàng của chính người gọi (GN-12).
 */
public record ScheduleRequest(
        @NotBlank(message = "Hình thức giao nhận không được để trống")
        @Pattern(regexp = "IN_PERSON|SHIPPING", message = "Hình thức giao nhận phải là IN_PERSON hoặc SHIPPING")
        String deliveryMethod,

        @Size(max = 255, message = "Địa điểm tối đa 255 ký tự.")
        String meetingPlace,

        @NotNull(message = "Thời gian hẹn không được để trống")
        Instant meetingTime,

        @Schema(description = "Phải là true khi lưu lịch giao nhận", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean agreeToWaiver,

        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự.")
        @Schema(description = "Địa chỉ nhận hàng của chính người gọi, chỉ dùng khi Giao hàng")
        String shippingAddress) {

    // Dạng 4 tham số (chưa có địa chỉ nhận hàng) để code và test đang gọi vẫn compile.
    public ScheduleRequest(String deliveryMethod, String meetingPlace, Instant meetingTime, Boolean agreeToWaiver) {
        this(deliveryMethod, meetingPlace, meetingTime, agreeToWaiver, null);
    }

    // Gặp trực tiếp bắt buộc có địa điểm; Giao hàng không dùng địa điểm.
    @JsonIgnore
    @AssertTrue(message = "Địa điểm không được để trống")
    public boolean isMeetingPlaceProvided() {
        return "SHIPPING".equals(deliveryMethod) || (meetingPlace != null && !meetingPlace.isBlank());
    }
}
