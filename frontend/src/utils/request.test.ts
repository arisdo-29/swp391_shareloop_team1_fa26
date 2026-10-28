import { describe, expect, it } from 'vitest';
import { canCancelTransaction, canReportHandoverIssue } from './request';

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
