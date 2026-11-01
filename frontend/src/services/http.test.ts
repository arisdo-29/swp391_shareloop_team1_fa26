// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { http, setUnauthorizedHandler, tokenStorage, type SessionTermination } from './http';

const originalAdapter = http.defaults.adapter;

function rejectRequest(status: number, data: Record<string, unknown>) {
  http.defaults.adapter = async (config) =>
    Promise.reject({
      config,
      response: { status, data, headers: {}, statusText: 'Error', config },
    });
}

function setSession() {
  tokenStorage.set('active-token', 'Bearer', 3600);
  tokenStorage.setUserId(7);
}

beforeEach(() => {
  localStorage.clear();
  setUnauthorizedHandler(null);
});

afterEach(() => {
  http.defaults.adapter = originalAdapter;
  setUnauthorizedHandler(null);
  vi.restoreAllMocks();
});

describe('HTTP session interceptor', () => {
  it('kết thúc phiên và chuyển lý do khi tài khoản đang đăng nhập bị khoá', async () => {
    setSession();
    const handler = vi.fn<(termination: SessionTermination) => void>();
    setUnauthorizedHandler(handler);
    rejectRequest(403, {
      code: 'AUTH_ACCOUNT_LOCKED',
      message: 'Tài khoản đã bị khoá.',
      details: { reason: 'spam bài' },
    });

    await expect(http.get('/api/v1/me')).rejects.toMatchObject({
      code: 'AUTH_ACCOUNT_LOCKED',
      status: 403,
    });

    expect(tokenStorage.get()).toBeNull();
    expect(tokenStorage.getUserId()).toBeNull();
    expect(handler).toHaveBeenCalledOnce();
    expect(handler).toHaveBeenCalledWith({
      code: 'AUTH_ACCOUNT_LOCKED',
      message: 'Tài khoản đã bị khoá. Lý do: spam bài',
    });
  });

  it('chỉ xử lý một lần khi nhiều response lỗi cùng trả về sau khi token đã bị xoá', async () => {
    setSession();
    const handler = vi.fn<(termination: SessionTermination) => void>();
    setUnauthorizedHandler(handler);
    rejectRequest(403, {
      code: 'AUTH_ACCOUNT_BANNED',
      message: 'Tài khoản đã bị cấm.',
    });

    await Promise.allSettled([http.get('/api/v1/me'), http.get('/api/v1/wallet')]);

    expect(handler).toHaveBeenCalledOnce();
  });

  it('không tự đăng xuất hoặc redirect đối với chính request đăng nhập', async () => {
    setSession();
    const handler = vi.fn<(termination: SessionTermination) => void>();
    setUnauthorizedHandler(handler);
    rejectRequest(403, {
      code: 'AUTH_ACCOUNT_LOCKED',
      message: 'Tài khoản đã bị khoá. Lý do: vi phạm quy định',
      details: { reason: 'vi phạm quy định' },
    });

    await expect(http.post('/api/v1/auth/login', {})).rejects.toMatchObject({
      code: 'AUTH_ACCOUNT_LOCKED',
      status: 403,
    });

    expect(tokenStorage.get()).toBe('active-token');
    expect(handler).not.toHaveBeenCalled();
  });

  it('không đăng xuất với lỗi 403 phân quyền thông thường', async () => {
    setSession();
    const handler = vi.fn<(termination: SessionTermination) => void>();
    setUnauthorizedHandler(handler);
    rejectRequest(403, { code: 'AUTH_FORBIDDEN', message: 'Không có quyền.' });

    await expect(http.get('/api/v1/admin')).rejects.toMatchObject({
      code: 'AUTH_FORBIDDEN',
      status: 403,
    });

    expect(tokenStorage.get()).toBe('active-token');
    expect(handler).not.toHaveBeenCalled();
  });
});
