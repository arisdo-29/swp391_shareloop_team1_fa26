import { API_BASE_URL } from './http';

export function resolveMediaUrl(value: string | null | undefined) {
  const url = value?.trim();
  if (!url) return '';
  if (/^[a-z][a-z\d+\-.]*:/i.test(url)) return url;
  try {
    return new URL(url, API_BASE_URL).toString();
  } catch {
    return url;
  }
}
