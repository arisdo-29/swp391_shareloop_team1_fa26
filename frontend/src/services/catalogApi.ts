import { http } from './http';

// Theo docs/api/catalog.md (GET công khai, không cần token)
export interface AreaResponse { id: number; name: string; parentId: number | null; level: number; children: AreaResponse[] }
export interface CategoryResponse { id: number; name: string; parentId: number | null; restricted: boolean; boostable: boolean; sortOrder: number }

export const catalogApi = {
  async areas() {
    const { data } = await http.get<AreaResponse[]>('/api/v1/areas');
    return data;
  },
  async categories() {
    const { data } = await http.get<CategoryResponse[]>('/api/v1/categories');
    return data;
  },
};
