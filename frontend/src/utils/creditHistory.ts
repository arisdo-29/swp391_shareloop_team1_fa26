import type { CreditHistory, CreditHistoryType } from '../types/domain';
import type { WalletTransactionResponse } from '../services/walletApi';

// Một tab lọc của bảng lịch sử Credit: id để nhớ tab đang chọn, matches để lọc các dòng của trang hiện tại.
export interface HistoryTab {
  id: string;
  label: string;
  matches: (entry: CreditHistory) => boolean;
}

const allTab: HistoryTab = { id: 'all', label: 'Tất cả', matches: () => true };
const topUpTab: HistoryTab = {
  id: 'topup',
  label: 'Nạp Credit',
  matches: (entry) => entry.type === 'TOPUP',
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

// Mọi dòng có itemId đều ghi ref theo một định dạng chung để đọc lại tên món ở danh sách và hộp chi tiết.
const ITEM_REF_PATTERN = /^item:(.+)$/;

export function itemIdOf(entry: CreditHistory) {
  return entry.ref?.match(ITEM_REF_PATTERN)?.[1];
}

// Tra tên món theo id ghi trong dòng lịch sử; bài không còn trong map thì không có tên.
export function itemTitleOf(entry: CreditHistory, itemTitles: Map<string, string>) {
  const itemId = itemIdOf(entry);
  return itemId ? itemTitles.get(itemId) : undefined;
}

// Dùng chung cho lịch sử Credit và báo cáo tài chính để một loại phí chỉ có một tên gọi.
export const walletTransactionLabels: Record<WalletTransactionResponse['type'], string> = {
  TOP_UP: 'Nạp Credit',
  POST_FEE: 'Phí đăng bài',
  EDIT_FEE: 'Phí chỉnh sửa bài',
  RENEW_FEE: 'Phí gia hạn bài',
  BOOST_FEE: 'Phí đẩy tin',
  AI_SEARCH_FEE: 'Phí dịch vụ AI',
  ADMIN_ADJUST: 'Điều chỉnh bởi quản trị viên',
};

const AI_REFUND_LABEL = 'Hoàn phí tìm kiếm AI';

// Backend ghi hoàn phí AI thành dòng AI_SEARCH_FEE có số dương. Đổi sang REFUND để không lẫn vào tab Chi tiêu.
// Bỏ note vì note đó có số dòng gốc (dùng để chặn hoàn trùng), người dùng không cần thấy.
function isAiRefund(entry: WalletTransactionResponse) {
  return entry.type === 'AI_SEARCH_FEE' && entry.amount > 0;
}

function toHistoryType(entry: WalletTransactionResponse): CreditHistoryType {
  if (entry.type === 'TOP_UP') return 'TOPUP';
  if (isAiRefund(entry)) return 'REFUND';
  if (entry.type === 'AI_SEARCH_FEE') return 'AI_SPEND';
  if (entry.type === 'ADMIN_ADJUST') return 'ADMIN_ADJUSTMENT';
  return 'SPEND';
}

export function toCreditHistory(entry: WalletTransactionResponse, userId: string): CreditHistory {
  const refund = isAiRefund(entry);
  return {
    id: `api_wallet_${entry.id}`,
    userId,
    transactionId: entry.paymentOrderId
      ? `payment-order-${entry.paymentOrderId}`
      : `ledger-${entry.id}`,
    type: toHistoryType(entry),
    amount: entry.amount,
    balance: entry.balanceAfter,
    description: refund ? AI_REFUND_LABEL : walletTransactionLabels[entry.type],
    status: 'completed',
    ref: entry.itemId ? `item:${entry.itemId}` : undefined,
    note: refund ? '' : (entry.note ?? ''),
    createdAt: entry.createdAt,
  };
}

export interface CreditHistorySummary {
  toppedUp: number; // tổng Credit đã nạp
  spent: number; // tổng Credit đã chi (số dương)
  refunded: number; // tổng Credit được hoàn
  adjusted: number; // tổng Admin điều chỉnh (có thể âm)
}

/**
 * Cộng các dòng lịch sử đã có từ backend để hiện tổng theo khoảng thời gian.
 * Chỉ đọc số backend trả về, không đổi số dư; số dư thật vẫn lấy từ GET /wallet.
 */
export function summarizeCreditHistory(entries: CreditHistory[]): CreditHistorySummary {
  return entries.reduce<CreditHistorySummary>(
    (summary, entry) => {
      if (entry.type === 'TOPUP') summary.toppedUp += entry.amount;
      else if (entry.type === 'REFUND') summary.refunded += entry.amount;
      else if (entry.type === 'ADMIN_ADJUSTMENT') summary.adjusted += entry.amount;
      else if (entry.amount < 0) summary.spent += -entry.amount;
      return summary;
    },
    { toppedUp: 0, spent: 0, refunded: 0, adjusted: 0 },
  );
}
