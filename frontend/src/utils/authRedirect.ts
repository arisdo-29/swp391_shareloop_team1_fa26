export function safeInternalNextPath(value: string | null): string | null {
  if (!value || !value.startsWith('/') || value.startsWith('//')) return null;
  return value;
}

export function loginPath(next: string): string {
  return `/login?next=${encodeURIComponent(next)}`;
}
