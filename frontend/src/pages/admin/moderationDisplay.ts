import type { ApiCondition, OfferType, OperatingStatus } from '../../services/itemApi';

// Các trường kiểm duyệt tự động có chung ở hàng chờ (ReviewQueueItem) và trang chi tiết (ReviewItemDetail).
export interface ModerationSignals {
  aiRisk?: string | null;
  moderationFlag?: string | null;
  flagReasonCode?: string | null;
  flagReason?: string | null;
  matchedTerms?: string[];
}

const moderationFlagLabel: Record<string, string> = {
  PASS: 'Đạt',
  REVIEW: 'Cần xem kỹ',
  BLOCK: 'Bị chặn',
};

const moderationReasonLabel: Record<string, string> = {
  LEXICON_REVIEW: 'Nội dung có từ khóa cần kiểm tra.',
  INVALID_INPUT: 'Thông tin bài đăng chưa hợp lệ.',
  INVALID_LEXICON_RULE: 'Quy tắc kiểm duyệt từ khóa chưa hợp lệ.',
  MODERATION_CHECK_FAILED: 'Không thể hoàn tất kiểm tra tự động.',
};

export const conditionLabels: Record<ApiCondition, string> = {
  NEW: 'Mới',
  LIKE_NEW: 'Ít sử dụng',
  GOOD: 'Đã dùng nhiều',
  DEFECTIVE: 'Có lỗi',
};

export const offerTypeLabels: Record<OfferType, string> = {
  GIVE: 'Cho tặng',
  SWAP: 'Trao đổi',
};

export const operatingStatusLabels: Record<OperatingStatus, string> = {
  NORMAL: 'Hoạt động bình thường',
  PARTIAL: 'Hoạt động một phần',
  NOT_WORKING: 'Không hoạt động',
};

/**
 * SRS v11 bỏ tầng AI gắn cờ: chỉ hiện cờ của tầng 1 (từ khoá, kiểm tra form). Cờ tầng 1 luôn có mã lý do kỹ thuật
 * hoặc từ khoá đã khớp; cờ AI chỉ có câu do AI viết, kể cả trên bài cũ được gắn trước khi tắt AI.
 */
function isRuleFlag(signals: ModerationSignals) {
  const code = signals.flagReasonCode?.trim().toUpperCase();
  return Boolean(signals.matchedTerms?.length) || Boolean(code && moderationReasonLabel[code]);
}

/** Nhãn cờ kiểm duyệt. Bài không có cờ (NONE hoặc trống) trả về chuỗi rỗng để không hiện badge. */
export function displayFlag(value?: string | null) {
  const normalized = value?.trim().toUpperCase();
  if (!normalized || normalized === 'NONE') return '';
  return moderationFlagLabel[normalized] ?? value!.trim();
}

/** Nhãn cờ của tầng 1; cờ AI trả về chuỗi rỗng để không hiện badge. */
export function displayRuleFlag(signals: ModerationSignals) {
  return isRuleFlag(signals) ? displayFlag(signals.moderationFlag) : '';
}

/** Lý do gắn cờ. Mã kỹ thuật được dịch, câu tiếng Việt từ Backend giữ nguyên, mã lạ thì nói chung chung. */
export function displayModerationNote(signals: ModerationSignals) {
  if (!isRuleFlag(signals)) return '';
  const reason = signals.flagReason?.trim();
  if (reason && reason.toUpperCase() !== 'NONE') {
    const normalizedReason = reason.toUpperCase();
    if (moderationReasonLabel[normalizedReason]) return moderationReasonLabel[normalizedReason];
    return /^[A-Z][A-Z0-9_]*$/.test(reason) ? 'Cần kiểm tra thêm' : reason;
  }
  return displayFlag(signals.moderationFlag);
}
