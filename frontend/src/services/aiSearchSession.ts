import type { Item } from '../types/domain';

const STORAGE_KEY = 'shareloop:ai-search-result';

export interface AiSearchSnapshot {
  userId: string;
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
    return snapshot as AiSearchSnapshot;
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

export function clearAiSearchSnapshot(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // Tìm kiếm vẫn thực hiện bình thường nếu trình duyệt chặn storage.
  }
}
