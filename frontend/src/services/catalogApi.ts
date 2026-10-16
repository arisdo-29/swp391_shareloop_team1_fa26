import { http } from './http';

export const catalogApiEndpoints = {
  categories: '/api/v1/categories',
  category: (id: number) => `/api/v1/categories/${id}`,
  categoryAttributes: (categoryId: number) => `/api/v1/categories/${categoryId}/attributes`,
  areas: '/api/v1/areas',
} as const;

export interface CategoryResponse {
  id: number;
  name: string;
  parentId: number | null;
  restricted: boolean;
  boostable: boolean;
  sortOrder: number;
}

export interface AreaResponse {
  id: number;
  name: string;
  parentId: number | null;
  level: number;
  children: AreaResponse[];
}

export type AttributeDataType = 'TEXT' | 'NUMBER' | 'SELECT' | 'BOOLEAN';

export interface CategoryAttributeResponse {
  id: number;
  categoryId: number;
  name: string;
  dataType: AttributeDataType;
  options: string[];
  required: boolean;
  sortOrder: number;
}

export const catalogApi = {
  async areas(): Promise<AreaResponse[]> {
    const { data } = await http.get<AreaResponse[]>(catalogApiEndpoints.areas);
    return data;
  },
  async categories(): Promise<CategoryResponse[]> {
    const { data } = await http.get<CategoryResponse[]>(catalogApiEndpoints.categories);
    return data;
  },
  async category(id: number): Promise<CategoryResponse> {
    const { data } = await http.get<CategoryResponse>(catalogApiEndpoints.category(id));
    return data;
  },
  async categoryAttributes(categoryId: number): Promise<CategoryAttributeResponse[]> {
    const { data } = await http.get<CategoryAttributeResponse[]>(catalogApiEndpoints.categoryAttributes(categoryId));
    return data;
  },
};

export async function getCategories(): Promise<CategoryResponse[]> {
  return catalogApi.categories();
}

export async function getAreas(): Promise<AreaResponse[]> {
  return catalogApi.areas();
}
