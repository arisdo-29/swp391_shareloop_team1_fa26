// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { getViewerKey, VIEWER_KEY_STORAGE_KEY, viewerKeyFor } from './viewerKey';

describe('getViewerKey', () => {
  afterEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('tạo khoá không âm lần đầu và trả lại đúng khoá đó ở lần sau', () => {
    const first = getViewerKey();

    expect(Number.isInteger(first)).toBe(true);
    expect(first).toBeGreaterThanOrEqual(0);
    expect(localStorage.getItem(VIEWER_KEY_STORAGE_KEY)).toBe(String(first));
    expect(getViewerKey()).toBe(first);
  });

  it('khoá đã lưu bị hỏng thì tạo khoá mới', () => {
    localStorage.setItem(VIEWER_KEY_STORAGE_KEY, '-5');

    const key = getViewerKey();

    expect(key).toBeGreaterThanOrEqual(0);
    expect(localStorage.getItem(VIEWER_KEY_STORAGE_KEY)).toBe(String(key));
  });

  it('storage bị chặn thì trả 0', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });

    expect(getViewerKey()).toBe(0);
  });

  it('chỉ gửi viewerKey khi chưa đăng nhập', () => {
    expect(viewerKeyFor(true)).toBeUndefined();
    expect(viewerKeyFor(false)).toEqual(expect.any(Number));
  });
});
