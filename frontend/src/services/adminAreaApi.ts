import { http } from './http';
import { invalidateCatalogCache } from './catalogApi';

export interface AdminAreaResponse {
  id: number;
  name: string;
  parentId: number | null;
  level: number;
  active: boolean;
}

export interface AdminAreaRequest {
  name: string;
  parentId?: number | null;
}

export const adminAreaApi = {
  async list() {
    const { data } = await http.get<AdminAreaResponse[]>('/api/v1/admin/areas');
    return data;
  },
  async create(input: AdminAreaRequest) {
    const { data } = await http.post<AdminAreaResponse>('/api/v1/admin/areas', input);
    invalidateCatalogCache('areas');
    return data;
  },
  async update(id: number, input: AdminAreaRequest) {
    const { data } = await http.put<AdminAreaResponse>(`/api/v1/admin/areas/${id}`, input);
    invalidateCatalogCache('areas');
    return data;
  },
  // Số bài đang hiển thị công khai sẽ bị ẩn khỏi Tìm đồ nếu tắt khu vực (gồm khu vực con).
  async visibleItemCount(id: number) {
    const { data } = await http.get<{ visibleItemCount: number }>(`/api/v1/admin/areas/${id}/visible-item-count`);
    return data.visibleItemCount;
  },
  async setActive(id: number, active: boolean) {
    const { data } = await http.patch<AdminAreaResponse>(`/api/v1/admin/areas/${id}/active`, { active });
    invalidateCatalogCache('areas');
    return data;
  },
};
