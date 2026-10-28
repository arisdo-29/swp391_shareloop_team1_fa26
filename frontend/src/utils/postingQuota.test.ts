import { describe, expect, it } from 'vitest';
import type { PostingQuota } from '../services/itemApi';
import { postingQuotaRow } from './postingQuota';

const quota: PostingQuota = {
  give: { active: 2, limit: 5 },
  swap: { active: 9, limit: -1 },
  rank: 'DIAMOND',
};

describe('postingQuotaRow', () => {
  it('hiện số bài Cho tặng trên hạn mức', () => {
    expect(postingQuotaRow('gift', quota)).toEqual({ label: 'Bài Cho tặng đang có', value: '2/5' });
  });

  it('hiện Không giới hạn khi hạn mức là -1', () => {
    expect(postingQuotaRow('trade', quota)).toEqual({ label: 'Bài Trao đổi đang có', value: '9/Không giới hạn' });
  });

  it('không có dòng khi chưa chọn hình thức', () => {
    expect(postingQuotaRow('', quota)).toBeNull();
  });

  it('hiện -- khi chưa tải được hạn mức', () => {
    expect(postingQuotaRow('gift', null)).toEqual({ label: 'Bài Cho tặng đang có', value: '--' });
  });
});
