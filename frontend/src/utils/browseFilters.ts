export const browseFilterKeys = ['q', 'type', 'categoryId', 'condition', 'areaId', 'sort'] as const;
export const BROWSE_PAGE_SIZE = 24;

export type BrowseFilterKey = (typeof browseFilterKeys)[number];

export function categoryBrowsePath(categoryId: number): string {
  return `/browse?categoryId=${encodeURIComponent(String(categoryId))}`;
}

export function districtBrowsePath(areaId: number): string {
  return `/browse?areaId=${encodeURIComponent(String(areaId))}`;
}

export function browseSearchPath(q: string, areaId?: number): string {
  const areaQuery = areaId === undefined
    ? ''
    : `&areaId=${encodeURIComponent(String(areaId))}`;
  return `/browse?q=${encodeURIComponent(q)}${areaQuery}`;
}

export function positiveIntegerParam(value: string | null): number | undefined {
  if (!value || !/^\d+$/.test(value)) return undefined;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined;
}

export function updateBrowseSearchParams(
  current: URLSearchParams,
  updates: Partial<Record<BrowseFilterKey, string>>,
): URLSearchParams {
  const next = new URLSearchParams(current);
  Object.entries(updates).forEach(([key, value]) => {
    if (key === 'areaId') next.delete('district');
    if (value) next.set(key, value);
    else next.delete(key);
  });
  return next;
}

export function resetBrowseSearchParams(current: URLSearchParams): URLSearchParams {
  const next = new URLSearchParams(current);
  browseFilterKeys.forEach((key) => next.delete(key));
  next.delete('district');
  return next;
}
