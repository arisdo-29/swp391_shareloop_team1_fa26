import { describe, expect, it } from 'vitest';
import {
  canCancelTransaction,
  canOpenComplaint,
  canRefuseHandover,
  formatDeadline,
} from './request';

describe('canCancelTransaction', () => {
  it('cho huỷ sau khi đã chọn người và trước khi chốt lịch', () => {
    for (const status of ['RESERVED', 'AWAITING_LOGISTICS'] as const) {
      expect(canCancelTransaction(status)).toBe(true);
    }
  });

  it('không cho huỷ từ khi chốt lịch, khi chưa chọn, đã kết thúc hoặc đang khiếu nại', () => {
    for (const status of [
      'LOGISTICS_CONFIRMED',
      'AWAITING_HANDOVER',
      'PENDING',
      'COMPLETED',
      'DISPUTED',
      'REJECTED',
      'CANCELLED',
    ] as const) {
      expect(canCancelTransaction(status)).toBe(false);
    }
  });
});

describe('canRefuseHandover', () => {
  it('người nhận luôn được từ chối', () => {
    expect(canRefuseHandover(false, undefined)).toBe(true);
    expect(canRefuseHandover(false, 7)).toBe(true);
  });

  it('chủ bài chỉ được từ chối ở Trao đổi', () => {
    expect(canRefuseHandover(true, undefined)).toBe(false);
    expect(canRefuseHandover(true, 7)).toBe(true);
  });
});

describe('canOpenComplaint (U08)', () => {
  const deadline = '2026-10-30T07:30:00Z';
  const deadlineMs = new Date(deadline).getTime();

  it('còn trong 24 giờ sau hoàn tất thì còn khiếu nại được', () => {
    expect(canOpenComplaint('COMPLETED', deadline, deadlineMs - 1)).toBe(true);
  });

  it('quá hạn thì ẩn nút khiếu nại', () => {
    expect(canOpenComplaint('COMPLETED', deadline, deadlineMs)).toBe(false);
    expect(canOpenComplaint('COMPLETED', deadline, deadlineMs + 60_000)).toBe(false);
  });

  it('chỉ giao dịch đã hoàn tất mới khiếu nại được', () => {
    expect(canOpenComplaint('AWAITING_HANDOVER', deadline, deadlineMs - 1)).toBe(false);
    expect(canOpenComplaint('DISPUTED', deadline, deadlineMs - 1)).toBe(false);
  });
});

describe('formatDeadline', () => {
  it('ghi giờ:phút ngày/tháng theo Asia/Ho_Chi_Minh', () => {
    expect(formatDeadline('2026-10-05T00:03:00Z')).toBe('07:03 05/10');
  });
});
