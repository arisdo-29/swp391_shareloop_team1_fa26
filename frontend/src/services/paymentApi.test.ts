// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { InternalAxiosRequestConfig } from 'axios';
import { http, setUnauthorizedHandler, tokenStorage, type SessionTermination } from './http';
import { paymentApi } from './paymentApi';

const originalAdapter = http.defaults.adapter;

function response(config: InternalAxiosRequestConfig, data: object) {
  return { config, data, headers: {}, status: 200, statusText: 'OK' };
}

function rejection(config: InternalAxiosRequestConfig, status: number) {
  return Promise.reject({
    config,
    response: {
      status,
      data: { code: status === 401 ? 'UNAUTHORIZED' : 'PAYMENT_UNAVAILABLE', message: 'Lỗi API.' },
      headers: {},
      statusText: 'Error',
      config,
    },
  });
}

beforeEach(() => {
  localStorage.clear();
  tokenStorage.set('active-token', 'Bearer', 3600);
  tokenStorage.setUserId(7);
  setUnauthorizedHandler(null);
});

afterEach(() => {
  http.defaults.adapter = originalAdapter;
  setUnauthorizedHandler(null);
  vi.restoreAllMocks();
});

describe('xác minh phiên khi đọc kết quả nạp Credit', () => {
  it('giữ phiên nếu API đơn trả 401 nhưng /me xác nhận token vẫn hợp lệ', async () => {
    const requestedUrls: string[] = [];
    const handler = vi.fn<(termination: SessionTermination) => void>();
    setUnauthorizedHandler(handler);
    http.defaults.adapter = async (config) => {
      requestedUrls.push(config.url ?? '');
      if (config.url === '/api/v1/me') {
        return response(config, {
          id: 7,
          email: 'member@shareloop.vn',
          fullName: 'Thành viên',
          phone: '0900000000',
          isAdmin: false,
          areaId: null,
          avatarMediaId: null,
          avatarUrl: null,
        });
      }
      return rejection(config, 401);
    };

    await expect(paymentApi.getOrder('SL-123')).rejects.toMatchObject({ status: 401 });

    expect(requestedUrls).toEqual(['/api/v1/wallet/topup-orders/SL-123', '/api/v1/me']);
    expect(tokenStorage.get()).toBe('active-token');
    expect(handler).not.toHaveBeenCalled();
  });

  it('kết thúc phiên khi /me xác nhận token đã hết hạn', async () => {
    const handler = vi.fn<(termination: SessionTermination) => void>();
    setUnauthorizedHandler(handler);
    http.defaults.adapter = async (config) => rejection(config, 401);

    await expect(paymentApi.getOrder('SL-123')).rejects.toMatchObject({ status: 401 });

    expect(tokenStorage.get()).toBeNull();
    expect(tokenStorage.getUserId()).toBeNull();
    expect(handler).toHaveBeenCalledOnce();
  });

  it('giữ phiên và không gọi /me khi API đơn gặp lỗi máy chủ', async () => {
    const requestedUrls: string[] = [];
    const handler = vi.fn<(termination: SessionTermination) => void>();
    setUnauthorizedHandler(handler);
    http.defaults.adapter = async (config) => {
      requestedUrls.push(config.url ?? '');
      return rejection(config, 503);
    };

    await expect(paymentApi.getOrder('SL-123')).rejects.toMatchObject({ status: 503 });

    expect(requestedUrls).toEqual(['/api/v1/wallet/topup-orders/SL-123']);
    expect(tokenStorage.get()).toBe('active-token');
    expect(handler).not.toHaveBeenCalled();
  });
});
