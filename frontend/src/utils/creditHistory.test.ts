import { describe, expect, it } from 'vitest';
import type { WalletTransactionResponse } from '../services/walletApi';
import type { CreditHistory } from '../types/domain';
import {
  adminHistoryTabs,
  itemIdOf,
  summarizeCreditHistory,
  toCreditHistory,
  userHistoryTabs,
} from './creditHistory';

function entry(
  id: number,
  type: WalletTransactionResponse['type'],
  amount: number,
): WalletTransactionResponse {
  return {
    id,
    type,
    amount,
    balanceAfter: 100,
    itemId: null,
    paymentOrderId: null,
    note: null,
    createdAt: '2026-10-05T03:00:00Z',
  };
}

const history = [
  toCreditHistory(entry(1, 'TOP_UP', 50), '7'),
  toCreditHistory(entry(2, 'POST_FEE', -5), '7'),
  toCreditHistory(entry(3, 'ADMIN_ADJUST', 3), '7'),
  toCreditHistory(entry(4, 'ADMIN_ADJUST', -2), '7'),
];

function idsOf(tabs: typeof adminHistoryTabs, tabId: string) {
  const tab = tabs.find((candidate) => candidate.id === tabId)!;
  return history.filter(tab.matches).map((item) => item.id);
}

describe('toCreditHistory', () => {
  it('đổi giao dịch ví thành dòng lịch sử có nhãn tiếng Việt', () => {
    expect(history[0]).toMatchObject({ type: 'TOPUP', description: 'Nạp Credit', userId: '7' });
    expect(history[1]).toMatchObject({ type: 'SPEND', description: 'Phí đăng bài' });
    expect(history[2].type).toBe('ADMIN_ADJUSTMENT');
  });
});

describe('adminHistoryTabs', () => {
  it('chia lịch sử thành nạp, cộng và trừ Credit', () => {
    expect(idsOf(adminHistoryTabs, 'all')).toHaveLength(4);
    expect(idsOf(adminHistoryTabs, 'topup')).toEqual(['api_wallet_1']);
    expect(idsOf(adminHistoryTabs, 'credit')).toEqual(['api_wallet_3']);
    expect(idsOf(adminHistoryTabs, 'debit')).toEqual(['api_wallet_2', 'api_wallet_4']);
  });
});

describe('userHistoryTabs', () => {
  it('giữ nguyên cách chia của người dùng: tất cả, nạp và chi tiêu', () => {
    expect(userHistoryTabs.map((tab) => tab.label)).toEqual(['Tất cả', 'Nạp Credit', 'Chi tiêu']);
    expect(idsOf(userHistoryTabs, 'spend')).toEqual(['api_wallet_2']);
  });
});

function walletEntry(
  id: number,
  type: WalletTransactionResponse['type'],
  amount: number,
  extra: Partial<WalletTransactionResponse> = {},
): WalletTransactionResponse {
  return { ...entry(id, type, amount), ...extra };
}

describe('toCreditHistory theo từng loại phí', () => {
  it.each([
    ['EDIT_FEE', 'Phí chỉnh sửa bài'],
    ['RENEW_FEE', 'Phí gia hạn bài'],
    ['BOOST_FEE', 'Phí đẩy tin'],
    ['POST_FEE', 'Phí đăng bài'],
  ] as const)('giữ nhãn đúng và itemId cho %s', (type, label) => {
    // given
    const wallet = walletEntry(10, type, -5, { itemId: 31 });

    // when
    const result = toCreditHistory(wallet, '7');

    // then
    expect(result).toMatchObject({ type: 'SPEND', description: label });
    expect(itemIdOf(result)).toBe('31');
  });

  it('không có itemId thì không đọc lại được id món', () => {
    // given
    const result = toCreditHistory(walletEntry(11, 'RENEW_FEE', -5), '7');

    // when
    const itemId = itemIdOf(result);

    // then
    expect(itemId).toBeUndefined();
  });

  it('đổi hoàn phí AI (số dương) sang REFUND, bỏ note có số và không xếp vào tab Chi tiêu', () => {
    // given
    const refund = toCreditHistory(
      walletEntry(12, 'AI_SEARCH_FEE', 2, { note: 'Hoàn phí AI #9' }),
      '7',
    );

    // when
    const spendTab = userHistoryTabs.find((tab) => tab.id === 'spend')!;

    // then
    expect(refund).toMatchObject({ type: 'REFUND', description: 'Hoàn phí tìm kiếm AI', note: '' });
    expect(spendTab.matches(refund)).toBe(false);
  });

  it('giữ phí AI (số âm) là chi tiêu', () => {
    // given
    const charge = toCreditHistory(walletEntry(13, 'AI_SEARCH_FEE', -2), '7');

    // when
    const spendTab = userHistoryTabs.find((tab) => tab.id === 'spend')!;

    // then
    expect(charge.type).toBe('AI_SPEND');
    expect(spendTab.matches(charge)).toBe(true);
  });
});

describe('summarizeCreditHistory', () => {
  const entry = (type: CreditHistory['type'], amount: number): CreditHistory => ({
    id: `${type}-${amount}`,
    userId: 'api_1',
    transactionId: 'ledger-1',
    type,
    amount,
    balance: 0,
    description: '',
    status: 'completed',
    note: '',
    createdAt: '2026-10-20T08:00:00Z',
  });

  it('cộng riêng tiền nạp, tiền chi, tiền hoàn và điều chỉnh của Admin', () => {
    const summary = summarizeCreditHistory([
      entry('TOPUP', 50),
      entry('TOPUP', 10),
      entry('SPEND', -5),
      entry('AI_SPEND', -2),
      entry('REFUND', 2),
      entry('ADMIN_ADJUSTMENT', -3),
    ]);

    expect(summary).toEqual({ toppedUp: 60, spent: 7, refunded: 2, adjusted: -3 });
  });

  it('trả về 0 khi chưa có lịch sử', () => {
    expect(summarizeCreditHistory([])).toEqual({ toppedUp: 0, spent: 0, refunded: 0, adjusted: 0 });
  });
});
