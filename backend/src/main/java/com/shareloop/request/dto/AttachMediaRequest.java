package com.shareloop.request.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * DTO yêu cầu đính kèm ảnh/video minh chứng vào Request (FR-86, BR-T09, UC-28).
 *
 * @param mediaIds danh sách ID của các tệp media đã upload
 * @param stage giai đoạn đính kèm ('HANDOVER' hoặc 'DISPUTE', mặc định là 'HANDOVER')
 */
public record AttachMediaRequest(
        @NotEmpty(message = "Danh sách mediaIds không được rỗng")
        List<Long> mediaIds,

        String stage) {}
