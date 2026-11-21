package com.shareloop.report.dto;

import com.shareloop.report.entity.ReportReason;
import com.shareloop.report.entity.ReportStatus;
import java.time.Instant;

/**
 * Một khiếu nại của giao dịch nhìn từ phía người đang xem (D-04, BC-08). Mỗi bên khiếu nại tối đa một lần nên một giao
 * dịch có tối đa hai phần tử. Backend tính sẵn openedByMe và canRespond để frontend không tự so giờ.
 *
 * @param id mã khiếu nại, dùng cho POST /api/v1/disputes/{id}/response
 * @param reason lý do khiếu nại (mã)
 * @param openedByMe true nếu người xem là bên khiếu nại
 * @param status trạng thái khiếu nại
 * @param responseDueAt hạn giải trình, null nếu không có hạn
 * @param respondedAt thời điểm bên bị khiếu nại đã giải trình, null nếu chưa
 * @param canRespond true khi người xem là bên bị khiếu nại, chưa giải trình, khiếu nại chưa kết luận và còn trong hạn
 */
public record RequestDisputeSummary(
        Long id,
        ReportReason reason,
        boolean openedByMe,
        ReportStatus status,
        Instant responseDueAt,
        Instant respondedAt,
        boolean canRespond) {}
