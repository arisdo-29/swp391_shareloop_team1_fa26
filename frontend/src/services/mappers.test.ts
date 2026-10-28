import { describe, expect, it } from 'vitest';
import type { ItemResponse } from './itemApi';
import { toFeItem } from './mappers';

const base: ItemResponse = {
  id: 5,
  donorId: 3,
  categoryId: 1,
  areaId: 6,
  offerType: 'GIVE',
  title: 'Sách Java',
  description: 'Còn mới',
  desiredItem: null,
  condition: 'GOOD',
  defectNote: null,
  brand: null,
  images: [],
  defectImages: [],
  operatingStatus: null,
  status: 'APPROVED',
  createdAt: '2026-10-28T10:00:00Z',
  updatedAt: '2026-10-28T10:00:00Z',
  expireAt: null,
  boostedUntil: null,
};

describe('toFeItem', () => {
  it('U04: giữ trạng thái tạm ẩn và lý do để trang Hoạt động hiện nhãn "Đang tạm ẩn"', () => {
    const item = toFeItem({ ...base, hidden: true, hiddenReason: 'Có nhiều báo cáo' }, new Map(), new Map());

    expect(item.hidden).toBe(true);
    expect(item.hiddenReason).toBe('Có nhiều báo cáo');
  });

  it('bài công khai không có trường hidden thì không bị coi là đang ẩn', () => {
    const item = toFeItem({ ...base, hidden: null, hiddenReason: null }, new Map(), new Map());

    expect(item.hidden).toBeUndefined();
    expect(item.hiddenReason).toBeUndefined();
  });
});
