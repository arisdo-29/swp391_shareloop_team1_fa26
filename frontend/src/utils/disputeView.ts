import type { RequestDispute } from '../services/requestApi';
import type { ReportReason } from '../services/reportApi';
import { disputeReasonLabels } from './disputeErrors';

// Câu chữ cho khối khiếu nại theo vai ở trang Tiến trình và khung chat (D-04).

/** Khiếu nại giao dịch dùng mã NO_SHOW cho mọi sự cố giao nhận, giống trang Khiếu nại của Admin. */
export function transactionDisputeReasonLabel(reason: ReportReason) {
  if (reason === 'NO_SHOW') return 'Sự cố giao nhận';
  return disputeReasonLabels[reason] ?? 'Lý do khác';
}

/** Thời gian còn lại tới hạn, dạng "còn 1 ngày 3 giờ" hoặc "còn 45 phút". Chỉ để hiển thị, server mới quyết định hạn. */
export function formatRemaining(dueAt: string, now = Date.now()) {
  const minutes = Math.max(0, Math.floor((new Date(dueAt).getTime() - now) / 60_000));
  const days = Math.floor(minutes / 1440);
  const hours = Math.floor((minutes % 1440) / 60);
  if (days) return `còn ${days} ngày ${hours} giờ`;
  if (hours) return `còn ${hours} giờ ${minutes % 60} phút`;
  return `còn ${minutes} phút`;
}

/**
 * Khiếu nại NO_SHOW mở từ "Giao nhận không thành công" khi bên kia đã bấm hoàn tất (GN-07). Trên giao diện gọi là
 * sự cố giao nhận chờ Admin xác minh, không gọi là khiếu nại.
 */
export function isHandoverIssue(dispute: RequestDispute) {
  return dispute.reason === 'NO_SHOW';
}

/** Câu mở đầu của một khiếu nại theo vai người xem. */
export function disputeHeadline(dispute: RequestDispute, partnerName: string) {
  if (isHandoverIssue(dispute)) {
    return dispute.openedByMe
      ? 'Bạn đã báo sự cố giao nhận, chờ Admin xác minh.'
      : `${partnerName} đã báo sự cố giao nhận, chờ Admin xác minh.`;
  }
  const reason = transactionDisputeReasonLabel(dispute.reason);
  return dispute.openedByMe
    ? `Bạn đã khiếu nại giao dịch này: ${reason}.`
    : `${partnerName} đã khiếu nại giao dịch này: ${reason}.`;
}

/** Câu báo Admin đã kết luận, dùng cho bên bị khiếu nại. */
export function disputeConcludedText(dispute: RequestDispute, partnerName: string) {
  if (isHandoverIssue(dispute)) return 'Admin đã xác minh sự cố giao nhận.';
  return `Admin đã kết luận khiếu nại ${transactionDisputeReasonLabel(dispute.reason).toLowerCase()} của ${partnerName}.`;
}

export function isDisputeConcluded(dispute: RequestDispute) {
  return dispute.status === 'RESOLVED' || dispute.status === 'DISMISSED';
}

/**
 * Bên bị khiếu nại đã quá hạn giải trình: backend không cho giải trình (canRespond = false) dù chưa giải trình và
 * khiếu nại chưa kết luận. Dựa vào cờ backend, không tự so giờ.
 */
export function isResponseOverdue(dispute: RequestDispute) {
  return !dispute.openedByMe && !dispute.canRespond && !dispute.respondedAt && !isDisputeConcluded(dispute);
}

/** Nhãn trạng thái ngắn cho bên khiếu nại. So giờ ở đây chỉ để chọn chữ, không quyết định quyền giải trình. */
export function disputeStatusLabel(dispute: RequestDispute, now = Date.now()) {
  if (isDisputeConcluded(dispute)) return 'Admin đã kết luận';
  if (dispute.respondedAt) return 'Đã giải trình, chờ Admin';
  if (dispute.responseDueAt && new Date(dispute.responseDueAt).getTime() < now) return 'Chờ Admin';
  return 'Chờ giải trình';
}
