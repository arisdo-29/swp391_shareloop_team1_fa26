import type { CreditHistory, CreditHistoryType } from '../types/domain';
import type { WalletTransactionResponse } from '../services/walletApi';

// Một tab lọc của bảng lịch sử Credit: id để nhớ tab đang chọn, matches để lọc các dòng của trang hiện tại.
export interface HistoryTab {
  id: string;
  label: string;
  matches: (entry: CreditHistory) => boolean;
  // Đơn nạp đang chờ xác nhận (chỉ có ở tài khoản demo) cũng được tính vào tab này.
  withPendingTopups?: boolean;
}

const allTab: HistoryTab = { id: 'all', label: 'Tất cả', matches: () => true, withPendingTopups: true };
const topUpTab: HistoryTab = {
  id: 'topup',
  label: 'Nạp Credit',
  matches: (entry) => entry.type === 'TOPUP',
  withPendingTopups: true,
};

// Người dùng tự xem: gộp mọi khoản chi vào một tab 'Chi tiêu'.
const SPEND_TYPES: CreditHistoryType[] = ['SPEND', 'AI_SPEND', 'RECEIVE_FEE'];
export const userHistoryTabs: HistoryTab[] = [
  allTab,
  topUpTab,
  { id: 'spend', label: 'Chi tiêu', matches: (entry) => SPEND_TYPES.includes(entry.type) },
];

// Admin xem lịch sử một người dùng: chia theo chiều cộng và chiều trừ để dễ đối chiếu.
export const adminHistoryTabs: HistoryTab[] = [
  allTab,
  topUpTab,
  {
    id: 'credit',
    label: 'Cộng Credit',
    matches: (entry) => entry.type !== 'TOPUP' && entry.amount > 0,
  },
  { id: 'debit', label: 'Trừ Credit', matches: (entry) => entry.amount < 0 },
];

export const HISTORY_PAGE_SIZE = 20;
const DAY_MS = 24 * 60 * 60 * 1000;
// Asia/Ho_Chi_Minh là UTC+7 quanh năm (không có giờ mùa hè) nên cộng cố định 7 giờ.
const VN_OFFSET_MS = 7 * 60 * 60 * 1000;

export type QuickRange = '7days' | 'month' | 'quarter' | 'all';

// Ngày hôm nay theo giờ Việt Nam, dạng yyyy-MM-dd (dùng được cho ô <input type='date'>).
function todayInVietnam() {
  return new Date(Date.now() + VN_OFFSET_MS).toISOString().slice(0, 10);
}

function dateToInput(timestamp: number) {
  return new Date(timestamp).toISOString().slice(0, 10);
}

// Trả về [từ ngày, đến ngày] cho nút lọc nhanh; '' nghĩa là không giới hạn.
export function quickRangeDates(range: QuickRange): [string, string] {
  const today = todayInVietnam();
  if (range === 'all') return ['', ''];
  if (range === '7days') {
    return [dateToInput(Date.parse(today) - 6 * DAY_MS), today];
  }
  if (range === 'month') return [today.slice(0, 8) + '01', today];
  const month = Number(today.slice(5, 7));
  const quarterStartMonth = Math.floor((month - 1) / 3) * 3 + 1;
  return [`${today.slice(0, 5)}${String(quarterStartMonth).padStart(2, '0')}-01`, today];
}

// 00:00 giờ Việt Nam của ngày đã chọn, đổi sang ISO UTC. API tính 'from' là gồm.
export function startOfDayIso(date: string) {
  return new Date(`${date}T00:00:00+07:00`).toISOString();
}

// API tính 'to' là không gồm, nên lấy 00:00 của ngày kế tiếp để bao trọn cuối ngày đã chọn.
export function endOfDayIso(date: string) {
  return new Date(new Date(`${date}T00:00:00+07:00`).getTime() + DAY_MS).toISOString();
}

const legacyHistoryPattern =
  /swap|give|giu phi|giữ phí|giu giao dich|giữ giao dịch|tam giu|tạm giữ/i;

export function isPostFee(entry: CreditHistory) {
  return entry.type === 'SPEND' && /^phi dang bai|^phí đăng bài/i.test(entry.description);
}

export function isVisibleCreditHistory(entry: CreditHistory) {
  if (['TRANSACTION_FEE', 'HOLD', 'RELEASE_HOLD'].includes(entry.type)) return false;
  if (legacyHistoryPattern.test(`${entry.description} ${entry.note}`)) return false;
  return (
    entry.type === 'TOPUP' ||
    entry.type === 'AI_SPEND' ||
    entry.type === 'RECEIVE_FEE' ||
    entry.type === 'ADMIN_ADJUSTMENT' ||
    isPostFee(entry)
  );
}

export function postFeeItemId(entry: CreditHistory) {
  return (
    entry.ref?.match(/^post:(.+):fee$/)?.[1] ?? entry.transactionId.match(/^post:(.+):fee$/)?.[1]
  );
}

const walletTransactionLabels: Record<WalletTransactionResponse['type'], string> = {
  TOP_UP: 'Nạp Credit',
  POST_FEE: 'Phí đăng bài',
  EDIT_FEE: 'Phí chỉnh sửa bài',
  RENEW_FEE: 'Phí gia hạn bài',
  BOOST_FEE: 'Phí đẩy tin',
  AI_SEARCH_FEE: 'Phí dịch vụ AI',
  ADMIN_ADJUST: 'Điều chỉnh bởi quản trị viên',
};

export function toCreditHistory(entry: WalletTransactionResponse, userId: string): CreditHistory {
  const type: CreditHistoryType =
    entry.type === 'TOP_UP'
      ? 'TOPUP'
      : entry.type === 'AI_SEARCH_FEE'
        ? 'AI_SPEND'
        : entry.type === 'ADMIN_ADJUST'
          ? 'ADMIN_ADJUSTMENT'
          : 'SPEND';
  return {
    id: `api_wallet_${entry.id}`,
    userId,
    transactionId: entry.paymentOrderId
      ? `payment-order-${entry.paymentOrderId}`
      : `ledger-${entry.id}`,
    type,
    amount: entry.amount,
    balance: entry.balanceAfter,
    description: walletTransactionLabels[entry.type],
    status: 'completed',
    ref: entry.type === 'POST_FEE' && entry.itemId ? `post:${entry.itemId}:fee` : undefined,
    note: entry.note ?? '',
    createdAt: entry.createdAt,
  };
}
