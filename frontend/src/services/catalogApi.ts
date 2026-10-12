export const catalogApiEndpoints = {
  categories: '/api/v1/categories',
  category: (id: number) => `/api/v1/categories/${id}`,
  areas: '/api/v1/areas',
  attributes: (categoryId: number) => `/api/v1/categories/${categoryId}/attributes`,
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

export interface AttributeResponse {
  id: number;
  categoryId: number;
  name: string;
  dataType: 'TEXT' | 'NUMBER' | 'SELECT' | 'BOOLEAN';
  options: string[] | null;
  required: boolean;
  sortOrder: number;
  active: boolean;
}

export async function getCategories(): Promise<CategoryResponse[]> {
  const response = await fetch(catalogApiEndpoints.categories);
  if (!response.ok) throw new Error(`Cannot load categories: ${response.status}`);
  return response.json() as Promise<CategoryResponse[]>;
}

export async function getAreas(): Promise<AreaResponse[]> {
  const response = await fetch(catalogApiEndpoints.areas);
  if (!response.ok) throw new Error(`Cannot load areas: ${response.status}`);
  return response.json() as Promise<AreaResponse[]>;
}

export async function getCategoryAttributes(categoryId: number): Promise<AttributeResponse[]> {
  const response = await fetch(catalogApiEndpoints.attributes(categoryId));
  if (!response.ok) throw new Error(`Cannot load category attributes: ${response.status}`);
  return response.json() as Promise<AttributeResponse[]>;
}
