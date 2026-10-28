import { describe, expect, it } from 'vitest';
import { summarizePointHistory } from './reputationApi';

describe('summarizePointHistory', () => {
  it('tách điểm được cộng, bị trừ và số lần không được cộng', () => {
    const summary = summarizePointHistory([
      { delta: 10, reason: 'Cho tặng', createdAt: '2026-10-20T08:00:00Z' },
      { delta: 6, reason: 'Trao đổi', createdAt: '2026-10-19T08:00:00Z' },
      { delta: -3, reason: 'Bị báo cáo', createdAt: '2026-10-18T08:00:00Z' },
      { delta: 0, reason: 'Giao dịch lặp cặp', createdAt: '2026-10-17T08:00:00Z' },
    ]);

    expect(summary).toEqual({ gained: 16, lost: 3, notCounted: 1, changes: 4 });
  });
});
