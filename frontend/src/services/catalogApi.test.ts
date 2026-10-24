import { afterEach, describe, expect, it, vi } from 'vitest';
import { catalogApi, invalidateCatalogCache, type AreaResponse } from './catalogApi';
import { http } from './http';

const areas: AreaResponse[] = [
  { id: 1, name: 'TP.HCM', parentId: null, level: 1, children: [] },
];

afterEach(() => {
  invalidateCatalogCache();
  vi.restoreAllMocks();
});

describe('catalogApi cache', () => {
  it('deduplicates concurrent area requests and reuses the fresh response', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: areas });

    const [first, second] = await Promise.all([catalogApi.areas(), catalogApi.areas()]);
    const third = await catalogApi.areas();

    expect(get).toHaveBeenCalledTimes(1);
    expect(first).toBe(second);
    expect(third).toBe(first);
  });

  it('loads areas again after invalidation', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: areas });

    await catalogApi.areas();
    invalidateCatalogCache('areas');
    await catalogApi.areas();

    expect(get).toHaveBeenCalledTimes(2);
  });
});
