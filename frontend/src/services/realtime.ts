import { API_BASE_URL, tokenStorage } from './http';

export interface RealtimeUpdate {
  userId: number | null;
  kind: 'READY' | 'NOTIFICATION' | 'REQUEST' | 'MESSAGE' | 'ITEM' | 'MODERATION' | 'CATALOG' | 'WALLET';
  resourceId: number | null;
}

type Listener = (update: RealtimeUpdate) => void;
const listeners = new Set<Listener>();
let privateStreamConnected = false;
let privateStreamGeneration = 0;

export function isRealtimeConnected() {
  return privateStreamConnected;
}

export function subscribeRealtime(listener: Listener) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function emit(update: RealtimeUpdate) {
  for (const listener of listeners) listener(update);
}

export function parseRealtimeFrame(frame: string): RealtimeUpdate | null {
  const data = frame.split('\n').find((line) => line.startsWith('data:'))?.slice(5).trim();
  if (!data) return null;
  try {
    const parsed: unknown = JSON.parse(data);
    if (!parsed || typeof parsed !== 'object' || !('kind' in parsed)) return null;
    const update = parsed as RealtimeUpdate;
    if (typeof update.kind !== 'string') return null;
    return update;
  } catch {
    return null;
  }
}

/** Một luồng fetch/SSE có Bearer JWT; tự nối lại khi Cloud Run đóng kết nối. */
export function connectRealtime(path: string, authenticated: boolean) {
  const generation = authenticated ? ++privateStreamGeneration : 0;
  let stopped = false;
  let controller: AbortController | null = null;
  let retryTimer: number | undefined;
  let retryMs = 1_000;

  const connect = async () => {
    if (stopped || document.visibilityState === 'hidden') return;
    const token = authenticated ? tokenStorage.get() : null;
    if (authenticated && !token) return;
    controller = new AbortController();
    try {
      const response = await fetch(`${API_BASE_URL}${path}`, {
        headers: token ? { Authorization: `${tokenStorage.getType()} ${token}` } : {},
        signal: controller.signal,
      });
      if (!response.ok || !response.body) {
        if (response.status === 401 || response.status === 403) {
          stopped = true;
          return;
        }
        throw new Error('Realtime connection failed');
      }
      if (authenticated && generation === privateStreamGeneration) privateStreamConnected = true;
      retryMs = 1_000;
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      while (!stopped) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n');
        let boundary = buffer.indexOf('\n\n');
        while (boundary !== -1) {
          const update = parseRealtimeFrame(buffer.slice(0, boundary));
          if (update) emit(update);
          buffer = buffer.slice(boundary + 2);
          boundary = buffer.indexOf('\n\n');
        }
      }
    } catch {
      // Mất mạng hoặc hết hạn luồng: thử lại khi tab còn hiển thị.
    } finally {
      if (authenticated && generation === privateStreamGeneration) privateStreamConnected = false;
      controller = null;
      if (!stopped && document.visibilityState === 'visible') {
        retryTimer = window.setTimeout(() => void connect(), retryMs);
        retryMs = Math.min(retryMs * 2, 30_000);
      }
    }
  };

  const onVisibilityChange = () => {
    if (document.visibilityState === 'hidden') {
      controller?.abort();
      if (retryTimer !== undefined) window.clearTimeout(retryTimer);
    } else if (!controller) {
      if (retryTimer !== undefined) window.clearTimeout(retryTimer);
      void connect();
    }
  };
  document.addEventListener('visibilitychange', onVisibilityChange);
  void connect();
  return () => {
    stopped = true;
    if (authenticated && generation === privateStreamGeneration) privateStreamConnected = false;
    controller?.abort();
    if (retryTimer !== undefined) window.clearTimeout(retryTimer);
    document.removeEventListener('visibilitychange', onVisibilityChange);
  };
}
