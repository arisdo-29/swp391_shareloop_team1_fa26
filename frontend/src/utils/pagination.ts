export const PAGE_SIZE_OPTIONS = [10, 20, 50] as const;

type PageSlot = number | 'ellipsis-start' | 'ellipsis-end';

/**
 * Tính dãy nút số trang: luôn có trang đầu, trang cuối và hai trang quanh trang hiện tại;
 * khoảng bị bỏ được thay bằng "...". Số trang trả về đếm từ 0.
 */
export function pageSlots(page: number, totalPages: number): PageSlot[] {
  // Ít trang thì hiện đủ, không cần "..."
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, index) => index);

  const lastPage = totalPages - 1;
  const start = Math.max(1, Math.min(page - 1, lastPage - 4));
  const end = Math.min(lastPage - 1, Math.max(page + 1, 4));
  const slots: PageSlot[] = [0];
  if (start > 1) slots.push('ellipsis-start');
  for (let index = start; index <= end; index += 1) slots.push(index);
  if (end < lastPage - 1) slots.push('ellipsis-end');
  slots.push(lastPage);
  return slots;
}
