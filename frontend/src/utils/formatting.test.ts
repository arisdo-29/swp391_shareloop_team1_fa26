import { describe, expect, it } from 'vitest';
import { conditionDescription, conditionLabel } from './formatting';

describe('item condition display text', () => {
  it('hiển thị đúng bốn nhãn và mô tả tình trạng', () => {
    expect([
      [conditionLabel.NEW, conditionDescription.NEW],
      [conditionLabel.LIKE_NEW, conditionDescription.LIKE_NEW],
      [conditionLabel.GOOD, conditionDescription.GOOD],
      [conditionLabel.DEFECTIVE, conditionDescription.DEFECTIVE],
    ]).toEqual([
      ['Mới', 'Chưa sử dụng'],
      ['Ít sử dụng', 'Mới dùng vài lần, còn đẹp'],
      ['Đã dùng nhiều', 'Có trầy xước hoặc hao mòn, vẫn dùng tốt'],
      ['Có lỗi', 'Có hư hỏng, cần nêu rõ'],
    ]);
  });
});
