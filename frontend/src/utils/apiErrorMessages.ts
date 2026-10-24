import type { ApiError } from '../services/http';

export const ITEM_REPUTATION_BLOCKED = 'ITEM_REPUTATION_BLOCKED';
export const REQUEST_REPUTATION_BLOCKED = 'REQUEST_REPUTATION_BLOCKED';
export const ITEM_MODERATION_BLOCKED = 'ITEM_MODERATION_BLOCKED';

const ITEM_REPUTATION_BLOCKED_MESSAGE =
  'Tài khoản đang có 0 sao uy tín nên tạm thời không đăng hoặc sửa bài được.';
const REQUEST_REPUTATION_BLOCKED_MESSAGE =
  'Tài khoản đang có 0 sao uy tín nên tạm thời không gửi yêu cầu được.';

const DEFAULT_MODERATION_MESSAGE =
  'Nội dung bài đăng của bạn có thể chứa thông tin không phù hợp với quy định của ShareLoop. Vui lòng kiểm tra và chỉnh sửa tiêu đề, mô tả hoặc hình ảnh trước khi đăng lại.';

/** Câu dễ hiểu cho lỗi 0 sao uy tín (BR-U05). Trả null khi lỗi không phải loại này. */
export function reputationBlockedMessage(code: string | undefined): string | null {
  if (code === ITEM_REPUTATION_BLOCKED) return ITEM_REPUTATION_BLOCKED_MESSAGE;
  if (code === REQUEST_REPUTATION_BLOCKED) return REQUEST_REPUTATION_BLOCKED_MESSAGE;
  return null;
}

/** Từ khoá bị chặn mà backend trả về, bỏ giá trị rỗng hoặc không phải chữ. */
function matchedTerms(details: ApiError['details']): string[] {
  const terms = details?.matchedTerms;
  if (!Array.isArray(terms)) return [];
  return terms.filter((term): term is string => typeof term === 'string' && term.trim().length > 0);
}

/** Lý do bài bị kiểm duyệt chặn, viết theo reasonCode để người dùng biết cần sửa gì (FR-42). */
export function moderationBlockedMessage(error: Pick<ApiError, 'details'>): string {
  const reasonCode = error.details?.reasonCode;
  if (reasonCode === 'RESTRICTED_CATEGORY') {
    return 'Danh mục này hiện không nhận đăng bài. Vui lòng chọn danh mục khác.';
  }
  if (reasonCode === 'CONTACT_INFO') {
    return 'Tiêu đề hoặc mô tả đang có thông tin liên hệ như số điện thoại, email hoặc đường dẫn. Vui lòng xoá đi và trao đổi qua khung chat sau khi có người nhận.';
  }
  if (reasonCode === 'LEXICON_BLOCK') {
    const terms = matchedTerms(error.details);
    const detail = terms.length ? ` Từ cần chỉnh: ${terms.join(', ')}.` : '';
    return `Bài đăng có nội dung không được phép đăng trên ShareLoop, ví dụ đồ cần sang tên giấy tờ, đồ quy ra tiền mặt hoặc thực phẩm và đồ uống.${detail} Vui lòng chỉnh sửa rồi đăng lại.`;
  }
  return DEFAULT_MODERATION_MESSAGE;
}
