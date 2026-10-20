import { http } from './http';

export const ADMIN_CHECKLIST_QUESTION_MAX_LENGTH = 255;

export interface AdminChecklistEntry {
  id: number;
  key: string;
  question: string;
  sortOrder: number;
  active: boolean;
}

export interface AdminChecklistRequest {
  question: string;
}

export const adminChecklistApi = {
  async list() {
    const { data } = await http.get<AdminChecklistEntry[]>('/api/v1/admin/checklist');
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
    const { data } = await http.put<AdminChecklistEntry[]>('/api/v1/admin/checklist/reorder', { itemIds });
    return data;
  },
  async setActive(id: number, active: boolean) {
    const { data } = await http.patch<AdminChecklistEntry>(`/api/v1/admin/checklist/${id}/active`, { active });
    return data;
  },
};
