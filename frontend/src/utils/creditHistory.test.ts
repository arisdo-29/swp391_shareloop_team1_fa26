import { describe, expect, it } from 'vitest';
import type { WalletTransactionResponse } from '../services/walletApi';
import { adminHistoryTabs, toCreditHistory, userHistoryTabs } from './creditHistory';

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
