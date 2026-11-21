import type { AdminConfigResponse } from '../../services/adminConfigApi';

// Nhãn lý do báo cáo, cùng bộ mã với trang Khiếu nại. Mã lạ giữ nguyên để Admin vẫn thấy giá trị thật.
const reportReasonLabels: Record<string, string> = {
  NOT_AS_DESCRIBED: 'Không đúng mô tả',
  COUNTERFEIT: 'Hàng giả',
  PROHIBITED_ITEM: 'Hàng cấm',
  NO_SHOW: 'Không đến hẹn',
  WRONG_CONTACT_INFO: 'Sai thông tin liên hệ',
  HARASSMENT: 'Quấy rối',
  OTHER: 'Khác',
};

/** Câu báo sau khi lưu: nêu số cấu hình đã cập nhật khi có cấu hình đồng bộ đi kèm. */
export function configUpdateNotice(label: string, updatedCount: number) {
  return updatedCount > 1
    ? `Đã cập nhật ${updatedCount} cấu hình, gồm "${label}" và các cấu hình liên quan.`
    : `Đã cập nhật "${label}".`;
}

/**
 * Giá trị -1 của hạn mức bài đăng nghĩa là không giới hạn, nên hiện chữ thay vì số âm. Danh sách lý do vi phạm nghiêm
 * trọng (L13) hiện bằng nhãn tiếng Việt thay cho mã kỹ thuật.
 */
export function formatConfigValue(config: AdminConfigResponse, value = config.value) {
  if (config.key.startsWith('max_active_items') && value === '-1') return 'Không giới hạn';
  if (config.key === 'report_serious_reasons') {
    return value
      .split(',')
      .map((code) => code.trim())
      .filter(Boolean)
      .map((code) => reportReasonLabels[code] ?? code)
      .join(', ');
  }
  return value;
}
