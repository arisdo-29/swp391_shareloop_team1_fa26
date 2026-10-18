import { API_BASE_URL } from './http';

export function resolveMediaUrl(value: string | null | undefined) {
  const url = value?.trim();
  if (!url) return '';
  if (/^[a-z][a-z\d+\-.]*:/i.test(url)) return url;
  let path = /^\d+$/.test(url) ? `/api/v1/media/${url}` : url;
  if (/^\/?media\//i.test(path)) path = `/api/v1/${path.replace(/^\/+/, '')}`;
  if (/^api\/v1\//i.test(path)) path = `/${path}`;
  try {
    const apiOrigin = new URL(API_BASE_URL).origin;
    return new URL(path, `${apiOrigin}/`).toString();
  } catch {
    const apiRoot = API_BASE_URL.replace(/\/api\/v1\/?$/i, '').replace(/\/$/, '');
    return `${apiRoot}${path.startsWith('/') ? path : `/${path}`}`;
  }
}
