import type { ActivityLogResponse } from '../../services/adminAuditApi';

// Nhật ký hệ thống tự cộng thêm thời gian hiển thị cho bài (DisplayTimeService ở backend).
// Backend vẫn trả nhãn và câu cũ, frontend đổi câu chữ hiển thị và quy đổi giờ từ data.hours.
const DISPLAY_EXTENDED_TYPE = 'ITEM_DISPLAY_COMPENSATED';
const DISPLAY_EXTENDED_OLD_LABEL = 'Cộng bù thời gian hiển thị';
export const DISPLAY_EXTENDED_LABEL = 'Gia hạn thời gian hiển thị';

const displayExtendedReasons: Record<string, string> = {
  RESERVATION_CANCELLED: 'do giao dịch bị hủy',
  EDIT_REVIEW: 'do chờ duyệt bản sửa',
};

function isDisplayExtended(log: Pick<ActivityLogResponse, 'type'>) {
  return log.type?.trim().toUpperCase() === DISPLAY_EXTENDED_TYPE;
}

/** Quy đổi số giờ ra ngày và giờ: 168 thành "7 ngày", 30 thành "1 ngày 6 giờ", 5 thành "5 giờ". */
export function formatDisplayDuration(totalHours: number) {
  const hours = Math.max(0, Math.floor(totalHours));
  const days = Math.floor(hours / 24);
  const remainder = hours % 24;
  if (days && remainder) return `${days} ngày ${remainder} giờ`;
  if (days) return `${days} ngày`;
  return `${remainder} giờ`;
}

function hoursFromData(data: ActivityLogResponse['data']) {
  const raw = data?.hours;
  const hours = typeof raw === 'number' ? raw : typeof raw === 'string' && raw.trim() ? Number(raw) : Number.NaN;
  return Number.isFinite(hours) && hours > 0 ? hours : null;
}

/** Nhãn hành động hiển thị cho một dòng nhật ký. */
export function activityLogLabel(log: Pick<ActivityLogResponse, 'type' | 'typeLabel'>) {
  if (isDisplayExtended(log)) return DISPLAY_EXTENDED_LABEL;
  return log.typeLabel;
}

/** Câu chi tiết hiển thị cho một dòng nhật ký. Thiếu số giờ thì giữ câu backend trả về. */
export function activityLogDetail(log: Pick<ActivityLogResponse, 'type' | 'data' | 'detail'>) {
  if (!isDisplayExtended(log)) return log.detail;
  const hours = hoursFromData(log.data);
  if (hours === null) return log.detail;
  const reasonValue = log.data?.reason;
  const reason = typeof reasonValue === 'string' ? displayExtendedReasons[reasonValue.trim().toUpperCase()] : undefined;
  return `Tự động gia hạn thêm ${formatDisplayDuration(hours)}${reason ? ` ${reason}` : ''}.`;
}

/** Nhãn của loại hành động trong bộ lọc nhật ký. */
export function activityTypeOptionLabel(type: string, label: string) {
  return type.trim().toUpperCase() === DISPLAY_EXTENDED_TYPE ? DISPLAY_EXTENDED_LABEL : label;
}

/** Lịch sử bài đăng chỉ nhận nhãn chữ từ backend nên đổi theo đúng nhãn cũ. */
export function historyActionLabel(action: string) {
  return action.trim() === DISPLAY_EXTENDED_OLD_LABEL ? DISPLAY_EXTENDED_LABEL : action;
}
