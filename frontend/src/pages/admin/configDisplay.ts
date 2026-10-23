import type { AdminConfigResponse } from '../../services/adminConfigApi';

/** Giá trị -1 của hạn mức bài đăng nghĩa là không giới hạn, nên hiện chữ thay vì số âm. */
export function formatConfigValue(config: AdminConfigResponse, value = config.value) {
  if (config.key.startsWith('max_active_items') && value === '-1') return 'Không giới hạn';
  return value;
}
