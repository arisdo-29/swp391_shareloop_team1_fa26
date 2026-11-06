// @vitest-environment jsdom

import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { itemApi, type FeaturedItemsResponse, type ItemResponse } from '../../services/itemApi';
import { FEATURED_REFRESH_DELAY_MS, useFeaturedItems } from './useFeaturedItems';

vi.mock('../../services/itemApi', () => ({
  itemApi: {
    featured: vi.fn(),
  },
}));

const NOW = Date.parse('2026-11-06T08:01:00Z');
const ROTATE_MS = 180_000;

function featuredItem(id: number): ItemResponse {
  return { id, title: `Bài ${id}` } as ItemResponse;
}

// Nhóm của lượt bắt đầu lúc rotatedAtMs, đổi nhóm sau ROTATE_MS.
function rotation(rotatedAtMs: number, ids: number[]): FeaturedItemsResponse {
  return {
    items: ids.map(featuredItem),
    rotatedAt: new Date(rotatedAtMs).toISOString(),
    nextRotateAt: new Date(rotatedAtMs + ROTATE_MS).toISOString(),
  };
}

// Cho các promise đang chờ (gọi API, setState) chạy xong trong fake timers.
async function flushPromises() {
  await act(async () => {
    await Promise.resolve();
  });
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(NOW);
});

afterEach(() => {
  vi.useRealTimers();
  vi.clearAllMocks();
});

describe('useFeaturedItems', () => {
  it('gọi featured khi mở trang và hẹn gọi lại đúng lúc nextRotateAt (cộng 1 giây)', async () => {
    // given: lượt hiện tại bắt đầu 08:00, đổi nhóm lúc 08:03
    const rotatedAt = Date.parse('2026-11-06T08:00:00Z');
    vi.mocked(itemApi.featured)
      .mockResolvedValueOnce(rotation(rotatedAt, [1, 2]))
      .mockResolvedValueOnce(rotation(rotatedAt + ROTATE_MS, [3, 4]));

    // when
    const { result } = renderHook(() => useFeaturedItems(5, 42));
    await flushPromises();

    // then: tải nhóm đầu với limit và viewerKey
    expect(itemApi.featured).toHaveBeenCalledWith({ limit: 5, viewerKey: 42 });
    expect(result.current.items.map((item) => item.id)).toEqual([1, 2]);

    // chưa tới mốc thì chưa gọi lại
    const untilNextRotate = rotatedAt + ROTATE_MS - NOW;
    await act(async () => {
      vi.advanceTimersByTime(untilNextRotate + FEATURED_REFRESH_DELAY_MS - 1);
    });
    expect(itemApi.featured).toHaveBeenCalledTimes(1);

    // tới mốc thì gọi lại và đổi sang nhóm mới
    await act(async () => {
      vi.advanceTimersByTime(1);
    });
    await flushPromises();
    expect(itemApi.featured).toHaveBeenCalledTimes(2);
    expect(result.current.items.map((item) => item.id)).toEqual([3, 4]);
  });

  it('huỷ timer khi rời trang', async () => {
    // given
    vi.mocked(itemApi.featured).mockResolvedValue(rotation(Date.parse('2026-11-06T08:00:00Z'), [1]));
    const { unmount } = renderHook(() => useFeaturedItems(5, 0));
    await flushPromises();

    // when
    unmount();
    await act(async () => {
      vi.advanceTimersByTime(10 * ROTATE_MS);
    });

    // then: không gọi thêm lần nào sau khi rời trang
    expect(itemApi.featured).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);
  });

  it('lỗi mạng thì giữ nhóm cũ và thử lại ở mốc đổi nhóm kế tiếp', async () => {
    // given: lần đầu thành công, lần hai lỗi mạng
    const rotatedAt = Date.parse('2026-11-06T08:00:00Z');
    vi.mocked(itemApi.featured)
      .mockResolvedValueOnce(rotation(rotatedAt, [1, 2]))
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(rotation(rotatedAt + 2 * ROTATE_MS, [5, 6]));
    const { result } = renderHook(() => useFeaturedItems(5, 0));
    await flushPromises();

    // when: tới mốc đổi nhóm nhưng gọi lỗi
    await act(async () => {
      vi.advanceTimersByTime(rotatedAt + ROTATE_MS - NOW + FEATURED_REFRESH_DELAY_MS);
    });
    await flushPromises();

    // then: vẫn giữ nhóm cũ
    expect(itemApi.featured).toHaveBeenCalledTimes(2);
    expect(result.current.items.map((item) => item.id)).toEqual([1, 2]);

    // tới mốc đổi nhóm kế tiếp thì thử lại và lấy được nhóm mới
    await act(async () => {
      vi.advanceTimersByTime(ROTATE_MS);
    });
    await flushPromises();
    expect(itemApi.featured).toHaveBeenCalledTimes(3);
    expect(result.current.items.map((item) => item.id)).toEqual([5, 6]);
  });

  it('quay lại tab sau khi đã quá mốc đổi nhóm thì gọi lại ngay', async () => {
    // given
    const rotatedAt = Date.parse('2026-11-06T08:00:00Z');
    vi.mocked(itemApi.featured).mockResolvedValue(rotation(rotatedAt, [1]));
    renderHook(() => useFeaturedItems(5, 0));
    await flushPromises();

    // when: đồng hồ đã qua mốc (timer ở tab ẩn chưa kịp chạy) và tab hiện lại
    vi.setSystemTime(rotatedAt + ROTATE_MS + 5_000);
    await act(async () => {
      document.dispatchEvent(new Event('visibilitychange'));
    });
    await flushPromises();

    // then
    expect(itemApi.featured).toHaveBeenCalledTimes(2);
  });
});
