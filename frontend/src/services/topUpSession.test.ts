// @vitest-environment jsdom

import { beforeEach, describe, expect, it } from 'vitest';
import {
  clearPendingTopUp,
  loadPendingTopUp,
  resolveTopUpReturnResult,
  returnOrderCode,
  savePendingTopUp,
  vnpayReturnStatus,
} from './topUpSession';

beforeEach(() => {
  sessionStorage.clear();
});

describe('VNPay return', () => {
  it('chỉ tin trạng thái SUCCESS do Backend xác nhận', () => {
    expect(resolveTopUpReturnResult('SUCCESS', 'FAILED')).toBe('success');
    expect(resolveTopUpReturnResult('PENDING', 'SUCCESS')).toBe('pending');
  });

  it('phân biệt hủy, thất bại và hết hạn mà không suy ra đã cộng Credit', () => {
    expect(resolveTopUpReturnResult('PENDING', 'FAILED')).toBe('pending-after-failed-return');
    expect(resolveTopUpReturnResult('FAILED', 'FAILED')).toBe('cancelled');
    expect(resolveTopUpReturnResult('FAILED', null)).toBe('failed');
    expect(resolveTopUpReturnResult('EXPIRED', 'SUCCESS')).toBe('expired');
  });

  it('đọc đúng tham số callback đã được Backend xác minh', () => {
    const params = new URLSearchParams('orderCode=SL-123&status=FAILED');

    expect(returnOrderCode(params)).toBe('SL-123');
    expect(vnpayReturnStatus(params)).toBe('FAILED');
    expect(vnpayReturnStatus(new URLSearchParams('status=UNKNOWN'))).toBeNull();
  });
});

describe('pending top-up session', () => {
  it('chỉ lưu dữ liệu điều hướng do Backend cấp, không lưu trạng thái, số tiền hay Credit', () => {
    savePendingTopUp({
      orderCode: 'SL-123',
      amountVnd: 20_000,
      credits: 20,
      status: 'PENDING',
      provider: 'VNPAY',
      redirectUrl: 'https://sandbox.vnpayment.vn/pay?vnp_TxnRef=SL-123',
      expiresAt: '2026-11-17T08:30:00Z',
    });

    expect(loadPendingTopUp()).toEqual({
      orderCode: 'SL-123',
      redirectUrl: 'https://sandbox.vnpayment.vn/pay?vnp_TxnRef=SL-123',
      expiresAt: '2026-11-17T08:30:00Z',
    });
    expect(sessionStorage.getItem('shareloop:pendingTopUp')).not.toContain('amountVnd');
    expect(sessionStorage.getItem('shareloop:pendingTopUp')).not.toContain('credits');
    expect(sessionStorage.getItem('shareloop:pendingTopUp')).not.toContain('status');
    clearPendingTopUp();
    expect(loadPendingTopUp()).toBeNull();
  });
});
