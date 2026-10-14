import axios, { AxiosError } from 'axios';

// Địa chỉ backend: đặt trong .env.local (máy cá nhân) và Environment Variables trên Vercel.
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'https://shareloop-api.onrender.com';
const TOKEN_KEY = 'shareloop:accessToken';
const USER_ID_KEY = 'shareloop:userId';
let unauthorizedHandler: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

export const tokenStorage = {
  get: () => localStorage.getItem(TOKEN_KEY),
  getUserId: () => {
    const raw = localStorage.getItem(USER_ID_KEY);
    if (!raw) return null;
    const userId = Number(raw);
    return Number.isSafeInteger(userId) && userId > 0 ? userId : null;
  },
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  setUserId: (userId: number) => localStorage.setItem(USER_ID_KEY, String(userId)),
  clear: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_ID_KEY);
  },
};

/** Lỗi thống nhất của backend. Màn hình chỉ dựa vào `code`. */
export interface ApiError {
  code: string;
  message: string;
  field?: string;
  details?: Record<string, unknown>;
  traceId?: string;
  status: number;
}

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 70_000, // Render free ngủ sau 15 phút, lần gọi đầu có thể mất khoảng 1 phút
  headers: { 'Content-Type': 'application/json' },
});

http.interceptors.request.use((config) => {
  const token = tokenStorage.get();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<Partial<ApiError>>) => {
    const status = error.response?.status ?? 0;
    if (status === 401) {
      const hadApiToken = Boolean(tokenStorage.get());
      tokenStorage.clear();
      if (hadApiToken) unauthorizedHandler?.();
    }
    const body = error.response?.data;
    const apiError: ApiError = {
      code: body?.code ?? (status === 0 ? 'NETWORK_ERROR' : 'UNKNOWN_ERROR'),
      message: body?.message ?? 'Không kết nối được máy chủ, vui lòng thử lại.',
      field: body?.field,
      details: body?.details,
      traceId: body?.traceId,
      status,
    };
    return Promise.reject(apiError);
  },
);
