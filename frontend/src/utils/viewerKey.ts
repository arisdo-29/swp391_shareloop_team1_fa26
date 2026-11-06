// Khoá xoay bài đẩy cho khách chưa đăng nhập (docs/api/item.md, tham số viewerKey). Mỗi trình duyệt giữ một số cố định
// để backend chia nhóm bài đẩy khác nhau cho từng người; đã đăng nhập thì backend dùng id người dùng nên không cần.
export const VIEWER_KEY_STORAGE_KEY = 'sl_viewer_key';

// Giới hạn trong số nguyên 32-bit dương để vừa kiểu Long ở backend và không mất độ chính xác ở JS.
const MAX_VIEWER_KEY = 2_147_483_647;

function isValidViewerKey(value: number): boolean {
  return Number.isInteger(value) && value >= 0 && value <= MAX_VIEWER_KEY;
}

/** Đọc khoá đã lưu; chưa có (hoặc hỏng) thì tạo số ngẫu nhiên không âm và lưu lại. Storage bị chặn thì trả 0. */
export function getViewerKey(): number {
  try {
    const raw = localStorage.getItem(VIEWER_KEY_STORAGE_KEY);
    if (raw !== null && raw.trim() !== '' && isValidViewerKey(Number(raw))) {
      return Number(raw);
    }
    const created = Math.floor(Math.random() * MAX_VIEWER_KEY);
    localStorage.setItem(VIEWER_KEY_STORAGE_KEY, String(created));
    return created;
  } catch {
    /* Storage may be blocked or unavailable. */
    return 0;
  }
}

/** viewerKey để gửi lên API: chỉ khi chưa đăng nhập; đã đăng nhập trả undefined để không gửi tham số. */
export function viewerKeyFor(isSignedIn: boolean): number | undefined {
  return isSignedIn ? undefined : getViewerKey();
}
