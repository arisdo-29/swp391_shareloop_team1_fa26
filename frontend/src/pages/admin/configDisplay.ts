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
