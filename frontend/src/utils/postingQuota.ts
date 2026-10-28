import type { PostingQuota } from '../services/itemApi';
import type { ItemType } from '../types/domain';

export interface PostingQuotaRow {
  label: string;
  value: string;
}

/**
 * Dòng "Bài ... đang có: a/b" ở cột Tóm tắt trang Đăng tin theo hình thức đang chọn. Hạn mức -1 là không giới hạn.
 * Chưa chọn hình thức thì không có dòng nào; chưa tải được hạn mức thì hiện "--".
 */
export function postingQuotaRow(type: ItemType | '', quota: PostingQuota | null): PostingQuotaRow | null {
  if (type === '') return null;
  const label = type === 'gift' ? 'Bài Cho tặng đang có' : 'Bài Trao đổi đang có';
  if (!quota) return { label, value: '--' };
  const entry = type === 'gift' ? quota.give : quota.swap;
  return { label, value: `${entry.active}/${entry.limit < 0 ? 'Không giới hạn' : entry.limit}` };
}
