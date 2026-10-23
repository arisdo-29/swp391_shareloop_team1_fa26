import { http } from './http';
import type { AttributeDataType } from './catalogApi';

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
  // Bỏ trống khi tạo mới: Backend đặt danh mục xuống cuối. Bỏ trống khi sửa: giữ thứ tự cũ.
  sortOrder?: number;
}

export interface AdminCategoryAttributeResponse {
  id: number;
  categoryId: number;
  name: string;
  dataType: AttributeDataType;
  options: string[];
  required: boolean;
  sortOrder: number;
  active: boolean;
}

export interface AdminCategoryAttributeRequest {
  name: string;
  dataType: AttributeDataType;
  options?: string[];
  required: boolean;
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
  // categoryIds phải gồm mọi danh mục (kể cả đang ẩn) theo thứ tự mới. Trả danh sách đã sắp xếp.
  async reorder(categoryIds: number[]) {
    const { data } = await http.put<AdminCategoryResponse[]>('/api/v1/admin/categories/reorder', { categoryIds });
    return data;
  },
  async listAttributes(categoryId: number) {
    const { data } = await http.get<AdminCategoryAttributeResponse[]>(`/api/v1/admin/categories/${categoryId}/attributes`);
    return data;
  },
  async createAttribute(categoryId: number, input: AdminCategoryAttributeRequest) {
    const { data } = await http.post<AdminCategoryAttributeResponse>(`/api/v1/admin/categories/${categoryId}/attributes`, input);
    return data;
  },
  async updateAttribute(categoryId: number, attributeId: number, input: AdminCategoryAttributeRequest) {
    const { data } = await http.put<AdminCategoryAttributeResponse>(`/api/v1/admin/categories/${categoryId}/attributes/${attributeId}`, input);
    return data;
  },
  async setAttributeActive(categoryId: number, attributeId: number, active: boolean) {
    const { data } = await http.patch<AdminCategoryAttributeResponse>(`/api/v1/admin/categories/${categoryId}/attributes/${attributeId}/active`, { active });
    return data;
  },
};
