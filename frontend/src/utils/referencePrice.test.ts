import { describe, expect, it } from 'vitest';
import { rankOfferedItems } from './referencePrice';

describe('rankOfferedItems', () => {
  const items = [
    { id: 'outside', referencePrice: 239999 },
    { id: 'legacy' },
    { id: 'upper', referencePrice: 360000 },
    { id: 'nearest', referencePrice: 280000 },
    { id: 'lower', referencePrice: 240000 },
  ];

  it('ưu tiên món tương đương, giữ lại món ngoài khoảng và món chưa có giá', () => {
    const { items: ranked, similarIds } = rankOfferedItems(items, 300000, 20);
    expect(ranked.map(({ id }) => id)).toEqual(['nearest', 'upper', 'lower', 'outside', 'legacy']);
    expect([...similarIds]).toEqual(['nearest', 'upper', 'lower']);
  });

  it('giữ thứ tự hiện tại nếu món đang xem chưa có giá', () => {
    const result = rankOfferedItems(items, undefined, 20);
    expect(result.items).toEqual(items);
    expect(result.similarIds.size).toBe(0);
  });
});
