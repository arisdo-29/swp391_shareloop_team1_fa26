import type { Item } from '../types/domain';

const STORAGE_KEY = 'shareloop:ai-search-result';

export interface AiSearchSnapshot {
  userId: string;
  query: string;
  message: string;
  items: Item[];
  chargedCredits: number;
}

export function readAiSearchSnapshot(userId: string): AiSearchSnapshot | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const value: unknown = JSON.parse(raw);
    if (typeof value !== 'object' || value === null) return null;
    const snapshot = value as Partial<AiSearchSnapshot>;
    if (snapshot.userId !== userId) return null;
    if (typeof snapshot.message !== 'string' || !Array.isArray(snapshot.items)) return null;
    if (!snapshot.items.every((item) =>
      item && typeof item.id === 'string' && typeof item.title === 'string'
      && typeof item.description === 'string' && Array.isArray(item.images))) return null;
    if (typeof snapshot.chargedCredits !== 'number') return null;
    // Bản lưu cũ chưa có câu hỏi thì coi như rỗng.
    const query = typeof snapshot.query === 'string' ? snapshot.query : '';
    return { ...snapshot, query } as AiSearchSnapshot;
  } catch {
    return null;
  }
}

export function saveAiSearchSnapshot(snapshot: AiSearchSnapshot): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot));
  } catch {
    // Kết quả vẫn hiển thị trong lần mở trang hiện tại nếu trình duyệt chặn storage.
  }
}

const KEEP_KEY = 'shareloop:ai-search-keep';

/** Đánh dấu "sắp mở bài từ kết quả AI": lần mở trang AI kế tiếp được giữ lại kết quả và câu hỏi. */
export function markAiSearchForReturn(): void {
  try {
    sessionStorage.setItem(KEEP_KEY, '1');
  } catch {
    // Không lưu được thì lần quay lại chỉ mất kết quả, không ảnh hưởng chức năng khác.
  }
}

export function shouldKeepAiSearch(): boolean {
  try {
    return sessionStorage.getItem(KEEP_KEY) === '1';
  } catch {
    return false;
  }
}

export function clearAiSearchKeepMark(): void {
  try {
    sessionStorage.removeItem(KEEP_KEY);
  } catch {
    // Bỏ qua: dấu này chỉ là gợi ý cho lần mở trang tiếp theo.
  }
}

export function clearAiSearchSnapshot(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // Tìm kiếm vẫn thực hiện bình thường nếu trình duyệt chặn storage.
  }
}
