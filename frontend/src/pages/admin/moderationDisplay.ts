import type { ApiCondition, OfferType, OperatingStatus } from '../../services/itemApi';

// Các trường kiểm duyệt tự động có chung ở hàng chờ (ReviewQueueItem) và trang chi tiết (ReviewItemDetail).
export interface ModerationSignals {
  aiRisk?: string | null;
  moderationFlag?: string | null;
  flagReason?: string | null;
  matchedTerms?: string[];
}

const riskLabel: Record<string, string> = {
  UNCHECKED: 'Chưa kiểm tra',
  LOW: 'Thấp',
  MEDIUM: 'Trung bình',
  HIGH: 'Cao',
};

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
  LIKE_NEW: 'Như mới',
  GOOD: 'Còn tốt',
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

/** Mức rủi ro bằng tiếng Việt. Giá trị Backend chưa biết thì giữ nguyên chữ để admin vẫn thấy. */
export function displayRisk(value?: string | null) {
  const normalized = value?.trim().toUpperCase();
  return normalized ? (riskLabel[normalized] ?? value!.trim()) : '';
}

/** Nhãn cờ kiểm duyệt. Bài không có cờ (NONE hoặc trống) trả về chuỗi rỗng để không hiện badge. */
export function displayFlag(value?: string | null) {
  const normalized = value?.trim().toUpperCase();
  if (!normalized || normalized === 'NONE') return '';
  return moderationFlagLabel[normalized] ?? value!.trim();
}

/** Lý do gắn cờ. Mã kỹ thuật được dịch, câu tiếng Việt từ Backend giữ nguyên, mã lạ thì nói chung chung. */
export function displayModerationNote(signals: ModerationSignals) {
  const reason = signals.flagReason?.trim();
  if (reason && reason.toUpperCase() !== 'NONE') {
    const normalizedReason = reason.toUpperCase();
    if (moderationReasonLabel[normalizedReason]) return moderationReasonLabel[normalizedReason];
    return /^[A-Z][A-Z0-9_]*$/.test(reason) ? 'Cần kiểm tra thêm' : reason;
  }
  return displayFlag(signals.moderationFlag);
}
