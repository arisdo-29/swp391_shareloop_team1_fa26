import { http } from './http';

export const ADMIN_CHECKLIST_QUESTION_MAX_LENGTH = 255;
export const ADMIN_CHECKLIST_DESCRIPTION_MAX_LENGTH = 1000;

export interface AdminChecklistEntry {
  id: number;
  key: string;
  question: string;
  sortOrder: number;
  active: boolean;
  // Mô tả điều kiện đạt (Q01), null khi chưa có.
  description?: string | null;
}

export interface AdminChecklistRequest {
  question: string;
  // Chuỗi rỗng thì xoá mô tả, bỏ trường thì giữ mô tả cũ.
  description?: string;
}

export function validateChecklistItemIds(itemIds: number[]) {
  return itemIds.length > 0 &&
    itemIds.every((id) => Number.isSafeInteger(id) && id > 0) &&
    new Set(itemIds).size === itemIds.length;
}

export const adminChecklistApi = {
  async list() {
    const { data } = await http.get<AdminChecklistEntry[]>('/api/v1/admin/checklist', {
      // Tránh browser/CDN trả lại checklist cũ sau khi Backend vừa cập nhật cấu hình.
      params: { _: Date.now() },
    });
    return data;
  },
  async create(input: AdminChecklistRequest) {
    const { data } = await http.post<AdminChecklistEntry>('/api/v1/admin/checklist', input);
    return data;
  },
  async update(id: number, input: AdminChecklistRequest) {
    const { data } = await http.put<AdminChecklistEntry>(`/api/v1/admin/checklist/${id}`, input);
    return data;
  },
  async reorder(itemIds: number[]) {
    if (!validateChecklistItemIds(itemIds)) {
      throw new Error('Danh sách ID checklist không hợp lệ.');
    }
    const { data } = await http.put<AdminChecklistEntry[]>('/api/v1/admin/checklist/reorder', { itemIds });
    return data;
  },
  async setActive(id: number, active: boolean) {
    const { data } = await http.patch<AdminChecklistEntry>(`/api/v1/admin/checklist/${id}/active`, { active });
    return data;
  },
};
