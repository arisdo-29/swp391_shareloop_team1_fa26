// @vitest-environment jsdom

import { waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { tokenStorage } from './http';
import { connectRealtime, parseRealtimeFrame, subscribeRealtime } from './realtime';

afterEach(() => {
  tokenStorage.clear();
  vi.unstubAllGlobals();
});

describe('SSE realtime', () => {
  it('đọc đúng sự kiện cập nhật của một tài nguyên', () => {
    expect(parseRealtimeFrame('event: update\ndata: {"userId":12,"kind":"REQUEST","resourceId":101}')).toEqual({
      userId: 12,
      kind: 'REQUEST',
      resourceId: 101,
    });
  });

  it('bỏ qua khung không có dữ liệu hợp lệ', () => {
    expect(parseRealtimeFrame(': heartbeat')).toBeNull();
    expect(parseRealtimeFrame('data: invalid')).toBeNull();
  });

  it('nhận sự kiện qua luồng dùng Bearer JWT', async () => {
    tokenStorage.set('test-token', 'Bearer', 3600);
    const encoded = new TextEncoder().encode('event: update\r\ndata: {"userId":12,"kind":"MESSAGE","resourceId":101}\r\n\r\n');
    let readCount = 0;
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      body: {
        getReader: () => ({
          read: async () => readCount++ === 0
            ? { value: encoded, done: false }
            : { value: undefined, done: true },
        }),
      },
    });
    vi.stubGlobal('fetch', fetchMock);
    const updates: string[] = [];
    const unsubscribe = subscribeRealtime((update) => updates.push(`${update.kind}:${update.resourceId}`));
    const disconnect = connectRealtime('/api/v1/notifications/events', true);
    try {
      await waitFor(() => expect(updates).toContain('MESSAGE:101'));
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/notifications/events'),
        expect.objectContaining({ headers: { Authorization: 'Bearer test-token' } }),
      );
    } finally {
      disconnect();
      unsubscribe();
    }
  });
});
