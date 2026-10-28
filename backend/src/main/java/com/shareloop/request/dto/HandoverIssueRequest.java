package com.shareloop.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * DTO báo "Tôi chưa nhận được đồ" / "Tôi chưa giao được" khi bên kia đã xác nhận bàn giao còn mình thì chưa.
 *
 * @param note mô tả sự cố, 5 đến 1000 ký tự; trở thành nội dung khiếu nại
 * @param evidenceMediaIds id ảnh hoặc video minh chứng đã tải lên (tuỳ chọn)
 */
public record HandoverIssueRequest(
        @NotBlank(message = "Ghi chú sự cố không được để trống")
        @Size(min = 5, max = 1000, message = "Ghi chú sự cố phải từ 5 đến 1000 ký tự")
        String note,

        List<Long> evidenceMediaIds) {}
