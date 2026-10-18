import axios, { AxiosError } from 'axios';

// Địa chỉ backend: đặt trong .env.local (máy cá nhân) và Environment Variables trên Vercel.
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'https://shareloop-api.onrender.com';
const TOKEN_KEY = 'shareloop:accessToken';
const TOKEN_TYPE_KEY = 'shareloop:tokenType';
const TOKEN_EXPIRES_AT_KEY = 'shareloop:tokenExpiresAt';
const USER_ID_KEY = 'shareloop:userId';
let unauthorizedHandler: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

export const tokenStorage = {
  get: () => {
    const token = localStorage.getItem(TOKEN_KEY);
    const expiresAt = Number(localStorage.getItem(TOKEN_EXPIRES_AT_KEY));
    if (token && Number.isFinite(expiresAt) && expiresAt > 0 && Date.now() >= expiresAt) {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(TOKEN_TYPE_KEY);
      localStorage.removeItem(TOKEN_EXPIRES_AT_KEY);
      localStorage.removeItem(USER_ID_KEY);
      return null;
    }
    return token;
  },
  getType: () => localStorage.getItem(TOKEN_TYPE_KEY) || 'Bearer',
  getUserId: () => {
    const raw = localStorage.getItem(USER_ID_KEY);
    if (!raw) return null;
    const userId = Number(raw);
    return Number.isSafeInteger(userId) && userId > 0 ? userId : null;
  },
  set: (token: string, tokenType: string, expiresIn: number) => {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(TOKEN_TYPE_KEY, tokenType);
    localStorage.setItem(TOKEN_EXPIRES_AT_KEY, String(Date.now() + expiresIn * 1000));
  },
  setUserId: (userId: number) => localStorage.setItem(USER_ID_KEY, String(userId)),
  clear: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(TOKEN_TYPE_KEY);
    localStorage.removeItem(TOKEN_EXPIRES_AT_KEY);
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
});

http.interceptors.request.use((config) => {
  const token = tokenStorage.get();
  if (token) config.headers.Authorization = `${tokenStorage.getType()} ${token}`;
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<Partial<ApiError>>) => {
    const status = error.response?.status ?? 0;
    const body = error.response?.data;
    if (status === 401 && body?.code !== 'AUTH_INVALID_CREDENTIALS') {
      const hadApiToken = Boolean(tokenStorage.get());
      tokenStorage.clear();
      if (hadApiToken) unauthorizedHandler?.();
    }
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
