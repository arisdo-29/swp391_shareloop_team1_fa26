import { describe, expect, it } from 'vitest';
import {
  activityLogDetail,
  activityLogLabel,
  activityTypeOptionLabel,
  formatDisplayDuration,
  historyActionLabel,
} from './activityLogDisplay';

const extended = {
  type: 'ITEM_DISPLAY_COMPENSATED',
  typeLabel: 'Cộng bù thời gian hiển thị',
  detail: 'Trả lại 168 giờ bài nằm trong giao dịch đã huỷ vào hạn hiển thị',
};

describe('activityLogDisplay', () => {
  it('quy đổi số giờ ra ngày và giờ', () => {
    expect(formatDisplayDuration(168)).toBe('7 ngày');
    expect(formatDisplayDuration(30)).toBe('1 ngày 6 giờ');
    expect(formatDisplayDuration(5)).toBe('5 giờ');
  });

  it('đổi nhãn và câu chi tiết theo số giờ thực tế khi giao dịch bị hủy', () => {
    const log = { ...extended, data: { hours: 168, reason: 'RESERVATION_CANCELLED' } };
    expect(activityLogLabel(log)).toBe('Gia hạn thời gian hiển thị');
    expect(activityLogDetail(log)).toBe('Tự động gia hạn thêm 7 ngày do giao dịch bị hủy.');
    expect(activityLogDetail({ ...log, data: { hours: 50, reason: 'RESERVATION_CANCELLED' } }))
      .toBe('Tự động gia hạn thêm 2 ngày 2 giờ do giao dịch bị hủy.');
  });

  it('ghi đúng lý do chờ duyệt bản sửa', () => {
    expect(activityLogDetail({ ...extended, data: { hours: 24, reason: 'EDIT_REVIEW' } }))
      .toBe('Tự động gia hạn thêm 1 ngày do chờ duyệt bản sửa.');
  });

  it('thiếu số giờ thì giữ câu backend, loại khác giữ nguyên', () => {
    expect(activityLogDetail({ ...extended, data: null })).toBe(extended.detail);
    const other = { type: 'CATEGORY_UPDATE', typeLabel: 'Sửa danh mục', detail: 'Đã sửa', data: { hours: 5 } };
    expect(activityLogLabel(other)).toBe('Sửa danh mục');
    expect(activityLogDetail(other)).toBe('Đã sửa');
  });

  it('đồng bộ nhãn ở bộ lọc và lịch sử bài đăng', () => {
    expect(activityTypeOptionLabel('ITEM_DISPLAY_COMPENSATED', 'Cộng bù thời gian hiển thị')).toBe('Gia hạn thời gian hiển thị');
    expect(activityTypeOptionLabel('REVIEW_APPROVE', 'Duyệt bài')).toBe('Duyệt bài');
    expect(historyActionLabel('Cộng bù thời gian hiển thị')).toBe('Gia hạn thời gian hiển thị');
    expect(historyActionLabel('Đẩy bài')).toBe('Đẩy bài');
  });
});
