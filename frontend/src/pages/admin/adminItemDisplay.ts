import type { LedgerTxType } from '../../services/adminFinanceApi';
import type { AdminItemReportRow, HiddenSource } from '../../services/adminItemApi';
import type { ItemStatus } from '../../services/itemApi';

// Nhãn dùng chung cho trang Quản lý bài đăng và chi tiết bài của Admin. Mã lạ giữ nguyên để Admin vẫn thấy giá trị thật.
export const itemStatusLabels: Record<ItemStatus, string> = {
  PENDING_REVIEW: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Bị từ chối',
  RESERVED: 'Đã giữ chỗ',
  TRADED: 'Đã giao dịch',
  EXPIRED: 'Hết hạn',
};

export const hiddenSourceLabels: Record<HiddenSource, string> = {
  ADMIN: 'Admin đã ẩn',
  OWNER_LOCKED: 'Chủ bài bị khoá',
  OTHER: 'Do báo cáo hoặc hệ thống',
};

export const reportReasonLabels: Record<string, string> = {
  NOT_AS_DESCRIBED: 'Không đúng mô tả',
  COUNTERFEIT: 'Hàng giả',
  PROHIBITED_ITEM: 'Hàng cấm',
  NO_SHOW: 'Không đến hẹn',
  WRONG_CONTACT_INFO: 'Sai thông tin liên hệ',
  HARASSMENT: 'Quấy rối',
  OTHER: 'Khác',
};

export const reportStatusLabels: Record<AdminItemReportRow['status'], string> = {
  OPEN: 'Đang mở',
  AWAITING_RESPONSE: 'Chờ giải trình',
  RESOLVED: 'Đã chấp nhận',
  DISMISSED: 'Đã bác bỏ',
};

export const ledgerTypeLabels: Record<LedgerTxType, string> = {
  TOP_UP: 'Nạp Credit',
  POST_FEE: 'Phí đăng bài',
  EDIT_FEE: 'Phí sửa bài',
  RENEW_FEE: 'Phí gia hạn',
  BOOST_FEE: 'Phí đẩy bài',
  AI_SEARCH_FEE: 'Phí trợ lý AI',
  ADMIN_ADJUST: 'Admin điều chỉnh',
};

export function formatDateTime(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString('vi-VN') : '—';
}

export const deliveryMethodLabels: Record<'IN_PERSON' | 'SHIPPING', string> = {
  IN_PERSON: 'Gặp trực tiếp',
  SHIPPING: 'Gửi hàng',
};
