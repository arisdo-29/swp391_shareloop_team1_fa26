// @vitest-environment jsdom

import { cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http } from '../services/http';
import { usePrivateMediaUrl } from './usePrivateMediaUrl';

vi.mock('../services/http', () => ({ http: { get: vi.fn() } }));

const createObjectURL = vi.fn();
const revokeObjectURL = vi.fn();

beforeEach(() => {
  createObjectURL.mockReturnValue('blob:private-1');
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL }));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('usePrivateMediaUrl', () => {
  it('tải ảnh bằng http có token và trả về blob URL', async () => {
    // given
    vi.mocked(http.get).mockResolvedValue({ data: new Blob(['x']) });

    // when
    const { result } = renderHook(() => usePrivateMediaUrl('/api/v1/requests/7/media/9'));

    // then
    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.url).toBe('blob:private-1'));
    expect(http.get).toHaveBeenCalledWith('/api/v1/requests/7/media/9', { responseType: 'blob' });
  });

  it('thu hồi blob URL khi component gỡ', async () => {
    // given
    vi.mocked(http.get).mockResolvedValue({ data: new Blob(['x']) });
    const { result, unmount } = renderHook(() => usePrivateMediaUrl('/api/v1/requests/7/media/9'));
    await waitFor(() => expect(result.current.url).toBe('blob:private-1'));

    // when
    unmount();

    // then
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:private-1');
  });

  it('báo lỗi khi không tải được ảnh', async () => {
    // given
    vi.mocked(http.get).mockRejectedValue(new Error('404'));

    // when
    const { result } = renderHook(() => usePrivateMediaUrl('/api/v1/requests/7/media/9'));

    // then
    await waitFor(() => expect(result.current.failed).toBe(true));
    expect(result.current.url).toBeNull();
  });

  it('không gọi http khi chưa có đường dẫn', () => {
    // when
    const { result } = renderHook(() => usePrivateMediaUrl(null));

    // then
    expect(result.current).toEqual({ url: null, loading: false, failed: false });
    expect(http.get).not.toHaveBeenCalled();
  });
});
