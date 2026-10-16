import { http } from './http';

export interface AdminCategoryResponse {
  id: number;
  name: string;
  parentId: number | null;
  restricted: boolean;
  boostable: boolean;
  sortOrder: number;
  active: boolean;
}

export interface AdminCategoryRequest {
  name: string;
  parentId?: number | null;
  restricted: boolean;
  boostable: boolean;
  sortOrder: number;
}

export const adminCategoryApi = {
  async list() {
    const { data } = await http.get<AdminCategoryResponse[]>('/api/v1/admin/categories');
    return data;
  },
  async create(input: AdminCategoryRequest) {
    const { data } = await http.post<AdminCategoryResponse>('/api/v1/admin/categories', input);
    return data;
  },
  async update(id: number, input: AdminCategoryRequest) {
    const { data } = await http.put<AdminCategoryResponse>(`/api/v1/admin/categories/${id}`, input);
    return data;
  },
  async setActive(id: number, active: boolean) {
    const { data } = await http.patch<AdminCategoryResponse>(`/api/v1/admin/categories/${id}/active`, { active });
    return data;
  },
};
