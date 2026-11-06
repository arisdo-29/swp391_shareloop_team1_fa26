import type { BoostQuote } from '../services/itemApi';

// Câu chữ cho hộp đẩy bài và trạng thái đẩy ở "Bài của tôi". Mọi mốc thời gian, phí và số suất lấy từ API (boost-quote,
// listing-fees, ItemResponse); ở đây chỉ định dạng để hiển thị, không tự cộng ngày hay tính luật đẩy bài.

export const WALLET_INSUFFICIENT_BALANCE = 'WALLET_INSUFFICIENT_BALANCE';

/** Các quyền lợi cố định trong hộp xác nhận đẩy bài, mỗi mục một dòng có nhãn in đậm. */
export const BOOST_BENEFITS = [
  {
    label: 'Vị trí ưu tiên',
    text: 'Bài được ưu tiên xuất hiện ở banner trang chủ và đầu trang Tìm đồ.',
  },
  {
    label: 'Xoay vòng công bằng',
    text: 'Các bài đẩy được xoay vòng đều, mỗi nhóm hiển thị 3 phút, nên bài không luôn nằm trên cùng. Người mua sau không được xếp trên bạn.',
  },
] as const;

/** Lưu ý hoàn phí, tách riêng để hiển thị như một cảnh báo. */
export const BOOST_REFUND_POLICY =
  'Bài bị gỡ, bị ẩn hoặc hết hạn hiển thị thì mất thời gian đẩy còn lại và không hoàn phí.';

/** Định dạng ngày giờ như phần còn lại của ứng dụng. */
export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('vi-VN');
}

/** Chỉ phần ngày, ví dụ 9/11/2026. */
export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('vi-VN');
}

/** Chỉ phần giờ, ví dụ 11:17:17. */
export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('vi-VN');
}

export type BoostRejectionAction = 'renew' | 'topUp' | null;

export interface BoostRejection {
  message: string;
  action: BoostRejectionAction;
}

/**
 * Lý do không đẩy được và khi nào làm lại được, theo mã lỗi (reasonCode của quote hoặc code của lỗi POST). Mã chưa biết
 * thì hiện nguyên reason backend trả.
 */
export function boostRejection(
  code: string | null,
  quote: BoostQuote | null,
  fallbackReason: string | null,
): BoostRejection {
  const fallback = fallbackReason?.trim() || quote?.reason?.trim() || 'Chưa đẩy được bài lúc này.';
  switch (code) {
    case 'LISTINGFEE_BOOST_SOLD_OUT':
      return {
        message: quote?.earliestSlotAt
          ? `Hết suất đẩy bài. Suất sớm nhất trống lúc ${formatDateTime(quote.earliestSlotAt)}. Bạn chưa bị trừ Credit.`
          : 'Hết suất đẩy bài. Bạn chưa bị trừ Credit.',
        action: null,
      };
    case 'LISTINGFEE_BOOST_USER_LIMIT':
      return {
        message: quote?.userEarliestBoostEndAt
          ? `Bạn đang đẩy ${quote.userActiveBoosts} bài, là mức tối đa. Bài sớm nhất kết thúc lúc ${formatDateTime(quote.userEarliestBoostEndAt)}.`
          : `Bạn đang đẩy ${quote?.userActiveBoosts ?? 'đủ số'} bài, là mức tối đa.`,
        action: null,
      };
    case 'LISTINGFEE_BOOST_COOLDOWN':
      return {
        message: quote?.cooldownUntil
          ? `Bài vừa kết thúc đợt đẩy. Bạn có thể đẩy lại từ ${formatDateTime(quote.cooldownUntil)}.`
          : 'Bài vừa kết thúc đợt đẩy, cần chờ hết thời gian nghỉ.',
        action: null,
      };
    case 'LISTINGFEE_BOOST_CHAIN_LIMIT':
      return { message: chainLimitMessage(quote), action: null };
    case 'LISTINGFEE_BOOST_EXCEEDS_EXPIRY':
      return {
        message: quote?.expireAt
          ? `Bài hết hạn hiển thị lúc ${formatDateTime(quote.expireAt)}. Hãy gia hạn bài trước khi đẩy.`
          : 'Bài sắp hết hạn hiển thị. Hãy gia hạn bài trước khi đẩy.',
        action: 'renew',
      };
    case WALLET_INSUFFICIENT_BALANCE:
      return {
        message: quote ? `Cần ${quote.fee} Credit để đẩy bài.` : 'Số dư Credit không đủ để đẩy bài.',
        action: 'topUp',
      };
    default:
      return { message: fallback, action: null };
  }
}

// Mốc nghỉ lấy từ cooldownUntil (backend tính) thay vì viết cứng số ngày nghỉ.
function chainLimitMessage(quote: BoostQuote | null): string {
  if (!quote) return 'Đợt đẩy này đã đủ số ngày tối đa.';
  const head = `Đợt đẩy này đã đủ số ngày tối đa, kết thúc lúc ${formatDateTime(quote.chainEndsAt)}.`;
  return quote.cooldownUntil
    ? `${head} Sau đó bài nghỉ và được đẩy lại từ ${formatDateTime(quote.cooldownUntil)}.`
    : `${head} Sau đó bài cần nghỉ trước khi được đẩy lại.`;
}

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;

/** Đếm ngược để hiển thị ("2 ngày 5 giờ"), không phải tính luật. */
export function formatRemaining(untilIso: string, nowMs: number): string {
  const remainingMs = Date.parse(untilIso) - nowMs;
  if (remainingMs < HOUR_MS) return 'dưới 1 giờ';
  const days = Math.floor(remainingMs / DAY_MS);
  const hours = Math.floor((remainingMs % DAY_MS) / HOUR_MS);
  return days > 0 ? `${days} ngày ${hours} giờ` : `${hours} giờ`;
}

export interface BoostTimelineFields {
  boostedUntil?: string;
  boostChainEndsAt?: string;
  boostCooldownUntil?: string;
}

/** Trạng thái đẩy của một bài ở "Bài của tôi"; null khi bài không đang đẩy cũng không đang nghỉ. */
export function boostStatusLines(item: BoostTimelineFields, nowMs: number): { main: string; extra?: string } | null {
  const { boostedUntil, boostChainEndsAt, boostCooldownUntil } = item;
  if (boostedUntil && Date.parse(boostedUntil) > nowMs) {
    const main = `Đang đẩy · còn ${formatRemaining(boostedUntil, nowMs)} · kết thúc ${formatDateTime(boostedUntil)}`;
    // Đã đẩy tới mốc cuối đợt thì không mua thêm được nữa: ẩn dòng phụ.
    const canExtend = boostChainEndsAt && Date.parse(boostChainEndsAt) > Date.parse(boostedUntil);
    return canExtend ? { main, extra: `Mua thêm được tới ${formatDateTime(boostChainEndsAt)}` } : { main };
  }
  if (boostCooldownUntil && Date.parse(boostCooldownUntil) > nowMs) {
    return { main: `Được đẩy lại từ ${formatDateTime(boostCooldownUntil)}` };
  }
  return null;
}
