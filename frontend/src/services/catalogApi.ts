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
  activeItemCount: number;
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

const CATALOG_CACHE_TTL_MS = 5 * 60 * 1000;

function createCachedResource<T>(load: () => Promise<T>) {
  let cached: { value: T; expiresAt: number } | null = null;
  let inFlight: Promise<T> | null = null;
  let version = 0;

  return {
    read() {
      if (cached && cached.expiresAt > Date.now()) return Promise.resolve(cached.value);
      if (inFlight) return inFlight;

      const requestVersion = version;
      const request = load()
        .then((value) => {
          if (requestVersion === version) {
            cached = { value, expiresAt: Date.now() + CATALOG_CACHE_TTL_MS };
          }
          return value;
        })
        .finally(() => {
          if (inFlight === request) inFlight = null;
        });
      inFlight = request;
      return request;
    },
    invalidate() {
      version += 1;
      cached = null;
      inFlight = null;
    },
  };
}

const areasResource = createCachedResource(async () => {
  const { data } = await http.get<AreaResponse[]>(catalogApiEndpoints.areas);
  return data;
});

const categoriesResource = createCachedResource(async () => {
  const { data } = await http.get<CategoryResponse[]>(catalogApiEndpoints.categories);
  return data;
});

export function invalidateCatalogCache(resource: 'areas' | 'categories' | 'all' = 'all') {
  if (resource === 'areas' || resource === 'all') areasResource.invalidate();
  if (resource === 'categories' || resource === 'all') categoriesResource.invalidate();
}

export const catalogApi = {
  areas(): Promise<AreaResponse[]> {
    return areasResource.read();
  },
  categories(): Promise<CategoryResponse[]> {
    return categoriesResource.read();
  },
  async categoriesWithCounts(): Promise<CategoryResponse[]> {
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
