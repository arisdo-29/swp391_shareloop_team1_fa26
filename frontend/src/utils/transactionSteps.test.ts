import { describe, expect, it } from 'vitest';
import type { ItemRequestStatus } from '../types/domain';
import { completedStepCount, transactionSteps } from './transactionSteps';

describe('Ánh xạ trạng thái Request sang 4 bước giao dịch', () => {
  it('có đúng 4 bước theo thứ tự', () => {
    // then
    expect(transactionSteps).toEqual(['Được chọn', 'Chốt lịch', 'Giao nhận', 'Hoàn tất']);
  });

  it.each<[ItemRequestStatus, number]>([
    ['RESERVED', 1],
    ['AWAITING_LOGISTICS', 1],
    ['LOGISTICS_CONFIRMED', 2],
    ['AWAITING_HANDOVER', 2],
    ['COMPLETED', 4],
    ['DISPUTED', 4],
  ])('%s xong %i bước', (status, expected) => {
    // when, then
    expect(completedStepCount(status)).toBe(expected);
  });

  it.each<ItemRequestStatus>(['PENDING', 'REJECTED', 'CANCELLED'])(
    '%s chưa vào giao dịch nên trả -1',
    (status) => {
      // when, then
      expect(completedStepCount(status)).toBe(-1);
    },
  );
});
