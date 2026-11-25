/** Số liệu của một thẻ: null là đang tải, 'error' là API lỗi. */
export type DashboardCount = number | null | 'error';

/** Chữ hiển thị cho số đếm: dấu ba chấm khi đang tải, hai gạch khi lỗi. */
export function formatDashboardCount(count: DashboardCount, cap?: number) {
  if (count === null) return '...';
  if (count === 'error') return '--';
  if (cap !== undefined && count >= cap) return `${cap.toLocaleString('vi-VN')}+`;
  return count.toLocaleString('vi-VN');
}
