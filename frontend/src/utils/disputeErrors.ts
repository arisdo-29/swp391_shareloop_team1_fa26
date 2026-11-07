import type { ApiError } from '../services/http';
import type { ReportReason } from '../services/reportApi';

// Dùng chung nhãn lý do cho biểu mẫu báo cáo và khiếu nại.
export const disputeReasonLabels: Record<ReportReason, string> = {
  NOT_AS_DESCRIBED: 'Không đúng mô tả',
  COUNTERFEIT: 'Hàng giả hoặc hàng nhái',
  PROHIBITED_ITEM: 'Mặt hàng bị cấm',
  NO_SHOW: 'Không đến theo lịch hẹn',
  WRONG_CONTACT_INFO: 'Thông tin liên hệ không đúng',
  HARASSMENT: 'Quấy rối',
  OTHER: 'Lý do khác',
};

const disputeErrorLabels: Record<string, string> = {
  VALIDATION_FAILED: 'Nội dung chưa hợp lệ. Hãy kiểm tra phần mô tả và ảnh minh chứng.',
  MEDIA_INVALID_FILE: 'Danh sách minh chứng có tệp không hợp lệ hoặc bị trùng.',
  UNAUTHORIZED: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
  DISPUTE_NOT_A_PARTY: 'Bạn không phải là một bên của giao dịch này.',
  DISPUTE_NOT_RESPONDENT: 'Chỉ người bị khiếu nại mới có thể gửi giải trình.',
  DISPUTE_REQUEST_NOT_COMPLETED: 'Giao dịch chưa hoàn tất nên chưa thể khiếu nại.',
  DISPUTE_WINDOW_EXPIRED: 'Đã quá thời hạn 24 giờ để khiếu nại giao dịch.',
  DISPUTE_RESPONSE_WINDOW_EXPIRED: 'Đã hết hạn gửi giải trình.',
  DISPUTE_NOT_FOUND: 'Không tìm thấy khiếu nại này.',
  DISPUTE_ALREADY_CONCLUDED: 'Khiếu nại đã được quản trị viên kết luận.',
  DISPUTE_ALREADY_RESPONDED: 'Bạn đã gửi giải trình cho khiếu nại này.',
  REPORT_TARGET_NOT_FOUND: 'Không tìm thấy giao dịch cần khiếu nại.',
  REPORT_TARGET_REQUIRED: 'Chưa chọn đối tượng cần báo cáo.',
  REPORT_CANNOT_REPORT_SELF: 'Bạn không thể tự báo cáo chính mình hoặc bài đăng của mình.',
  REPORT_NOT_PARTICIPANT: 'Bạn không phải là bên liên quan của khiếu nại.',
  REPORT_RESPONSE_NOT_ALLOWED: 'Hồ sơ này không còn nhận giải trình.',
  REPORT_ALREADY_REPORTED: 'Bạn đã gửi khiếu nại cho giao dịch này.',
  REPORT_NOT_FOUND: 'Không tìm thấy báo cáo hoặc khiếu nại.',
  REPORT_ALREADY_CONCLUDED: 'Báo cáo hoặc khiếu nại đã được kết luận.',
  MEDIA_NOT_OWNER: 'Có minh chứng không thuộc quyền sở hữu của bạn.',
  MEDIA_NOT_UPLOADED: 'Có ảnh minh chứng đã được dùng hoặc không còn hợp lệ.',
  MEDIA_NOT_FOUND: 'Không tìm thấy một hoặc nhiều minh chứng đã tải lên.',
  DISPUTE_INVALID_EVIDENCE: 'Minh chứng không hợp lệ hoặc không thuộc quyền sở hữu của bạn.',
};

export function disputeErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  if (apiError.code && disputeErrorLabels[apiError.code]) return disputeErrorLabels[apiError.code];
  if (apiError.status === 401) return disputeErrorLabels.UNAUTHORIZED;
  if (apiError.status === 0)
    return 'Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại.';
  return 'Không thể gửi thông tin lúc này. Vui lòng thử lại.';
}
