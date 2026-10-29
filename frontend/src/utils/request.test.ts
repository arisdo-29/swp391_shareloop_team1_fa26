import { describe, expect, it } from 'vitest';
import { canCancelTransaction, canOpenComplaint, canReportHandoverIssue, formatDeadline } from './request';

describe('canCancelTransaction', () => {
  it('cho huỷ ở bốn trạng thái sau khi đã chọn người', () => {
    for (const status of ['RESERVED', 'AWAITING_LOGISTICS', 'LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER'] as const) {
      expect(canCancelTransaction(status)).toBe(true);
    }
  });

  it('không cho huỷ khi chưa chọn, đã kết thúc hoặc đang khiếu nại', () => {
    for (const status of ['PENDING', 'COMPLETED', 'DISPUTED', 'REJECTED', 'CANCELLED'] as const) {
      expect(canCancelTransaction(status)).toBe(false);
    }
  });
});

describe('canReportHandoverIssue', () => {
  const confirmedAt = '2026-10-28T01:00:00Z';

  it('chỉ hiện khi AWAITING_HANDOVER, bên kia đã xác nhận và mình chưa', () => {
    expect(canReportHandoverIssue('AWAITING_HANDOVER', confirmedAt, undefined)).toBe(true);
  });

  it('không hiện khi bên kia chưa xác nhận', () => {
    expect(canReportHandoverIssue('AWAITING_HANDOVER', undefined, undefined)).toBe(false);
  });

  it('không hiện khi mình đã xác nhận', () => {
    expect(canReportHandoverIssue('AWAITING_HANDOVER', confirmedAt, confirmedAt)).toBe(false);
  });

  it('không hiện ở trạng thái khác', () => {
    expect(canReportHandoverIssue('LOGISTICS_CONFIRMED', confirmedAt, undefined)).toBe(false);
    expect(canReportHandoverIssue('DISPUTED', confirmedAt, undefined)).toBe(false);
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
  it('ghi giờ:phút ngày/tháng có số 0 đứng trước', () => {
    const local = new Date(2026, 9, 5, 7, 3);
    expect(formatDeadline(local.toISOString())).toBe('07:03 05/10');
  });
});
