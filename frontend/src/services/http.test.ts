// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { subscribeToasts, type ToastMessage } from '../components/toast';
import {
  http,
  setUnauthorizedHandler,
  tokenStorage,
  userFacingErrorMessage,
  type SessionTermination,
} from './http';

const originalAdapter = http.defaults.adapter;

function rejectRequest(status: number, data: Record<string, unknown>) {
  http.defaults.adapter = async (config) =>
    Promise.reject({
      config,
      response: { status, data, headers: {}, statusText: 'Error', config },
    });
}

function resolveRequest(data: Record<string, unknown> = {}) {
  http.defaults.adapter = async (config) => ({
    config,
    data,
    headers: {},
    status: 200,
    statusText: 'OK',
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

  it('không đăng xuất khi API thanh toán trả lỗi nghiệp vụ 403', async () => {
    setSession();
    const handler = vi.fn<(termination: SessionTermination) => void>();
    setUnauthorizedHandler(handler);
    rejectRequest(403, {
      code: 'WALLET_PAYMENT_ORDER_NOT_PAYABLE',
      message: 'Đơn thanh toán không thể xử lý.',
    });

    await expect(http.get('/api/v1/wallet/topup-orders/SL-123')).rejects.toMatchObject({
      code: 'WALLET_PAYMENT_ORDER_NOT_PAYABLE',
      status: 403,
    });

    expect(tokenStorage.get()).toBe('active-token');
    expect(tokenStorage.getUserId()).toBe(7);
    expect(handler).not.toHaveBeenCalled();
  });

  it('chỉ kết thúc phiên với 401 từ API bảo vệ khi JWT không còn hợp lệ', async () => {
    setSession();
    const handler = vi.fn<(termination: SessionTermination) => void>();
    setUnauthorizedHandler(handler);
    rejectRequest(401, { code: 'UNAUTHORIZED', message: 'Bạn chưa đăng nhập.' });

    await expect(http.get('/api/v1/me')).rejects.toMatchObject({
      code: 'UNAUTHORIZED',
      status: 401,
    });

    expect(tokenStorage.get()).toBeNull();
    expect(tokenStorage.getUserId()).toBeNull();
    expect(handler).toHaveBeenCalledOnce();
    expect(handler).toHaveBeenCalledWith({ code: 'UNAUTHORIZED' });
  });

  it('không phát Toast khi mutation trả 401 do phiên hết hạn', async () => {
    setSession();
    const handler = vi.fn<(termination: SessionTermination) => void>();
    const received: ToastMessage[] = [];
    const unsubscribe = subscribeToasts((message) => received.push(message));
    setUnauthorizedHandler(handler);
    rejectRequest(401, {
      code: 'UNAUTHORIZED',
      message: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
    });

    await expect(http.post('/api/v1/items', {})).rejects.toMatchObject({ status: 401 });

    expect(tokenStorage.get()).toBeNull();
    expect(handler).toHaveBeenCalledWith({ code: 'UNAUTHORIZED' });
    expect(received).toEqual([]);
    unsubscribe();
  });
});

describe('HTTP mutation Toast interceptor', () => {
  it('chỉ báo thành công sau khi API trả response thành công', async () => {
    const received: ToastMessage[] = [];
    const unsubscribe = subscribeToasts((message) => received.push(message));
    resolveRequest();

    await http.post('/api/v1/items/12/renew');

    expect(received).toMatchObject([{ tone: 'success', message: 'Gia hạn bài đăng thành công.' }]);
    unsubscribe();
  });

  it.each(['/api/v1/auth/login', '/api/v1/auth/logout'])(
    'không hiển thị Toast khi %s thành công',
    async (url) => {
      const received: ToastMessage[] = [];
      const unsubscribe = subscribeToasts((message) => received.push(message));
      resolveRequest();

      await http.post(url);

      expect(received).toEqual([]);
      unsubscribe();
    },
  );

  it('không hiển thị Toast khi tải ảnh thành công', async () => {
    const received: ToastMessage[] = [];
    const unsubscribe = subscribeToasts((message) => received.push(message));
    resolveRequest({ mediaId: 1, url: '/media/1' });

    await http.post('/api/v1/media', new FormData());

    expect(received).toEqual([]);
    unsubscribe();
  });

  it('vẫn hiển thị Toast khi tải ảnh thất bại', async () => {
    const received: ToastMessage[] = [];
    const unsubscribe = subscribeToasts((message) => received.push(message));
    rejectRequest(400, { code: 'MEDIA_UPLOAD_FAILED', message: 'Không tải được ảnh lên.' });

    await expect(http.post('/api/v1/media', new FormData())).rejects.toMatchObject({ status: 400 });

    expect(received).toContainEqual(
      expect.objectContaining({ tone: 'error', message: 'Không tải được ảnh lên.' }),
    );
    unsubscribe();
  });

  it('vẫn hiển thị Toast đăng bài thành công', async () => {
    const received: ToastMessage[] = [];
    const unsubscribe = subscribeToasts((message) => received.push(message));
    resolveRequest({ id: 1 });

    await http.post('/api/v1/items', {});

    expect(received).toContainEqual(
      expect.objectContaining({ tone: 'success', message: 'Đăng bài thành công.' }),
    );
    unsubscribe();
  });

  it('không hiển thị Toast khi lưu cấu hình Admin thành công', async () => {
    const received: ToastMessage[] = [];
    const unsubscribe = subscribeToasts((message) => received.push(message));
    resolveRequest({ key: 'post_fee', value: '7' });

    await http.put('/api/v1/admin/configs/post_fee', {
      value: '7',
      reason: 'Điều chỉnh theo quý',
    });

    expect(received).toEqual([]);
    unsubscribe();
  });

  it('không hiển thị Toast lỗi trùng với Alert khi lưu cấu hình Admin thất bại', async () => {
    const received: ToastMessage[] = [];
    const unsubscribe = subscribeToasts((message) => received.push(message));
    rejectRequest(400, { code: 'CONFIG_INVALID', message: 'Giá trị cấu hình không hợp lệ.' });

    await expect(
      http.put('/api/v1/admin/configs/post_fee', {
        value: 'invalid',
        reason: 'Điều chỉnh theo quý',
      }),
    ).rejects.toMatchObject({ status: 400 });

    expect(received).toEqual([]);
    unsubscribe();
  });

  it.each([
    ['/api/v1/admin/categories/1', 'put'],
    ['/api/v1/admin/checklist/4', 'put'],
    ['/api/v1/admin/reviews/8/approve', 'post'],
    ['/api/v1/admin/users/9/lock', 'post'],
    ['/api/v1/admin/requests/7/cancel', 'post'],
    ['/api/v1/admin/reports/6/conclude', 'post'],
  ])('không hiển thị Toast Admin trùng với phản hồi tại trang cho %s', async (url, method) => {
    const received: ToastMessage[] = [];
    const unsubscribe = subscribeToasts((message) => received.push(message));
    resolveRequest();

    await http.request({ url, method });

    expect(received).toEqual([]);
    unsubscribe();
  });

  it('không hiển thị success fallback cho mutation Admin chưa được ánh xạ', async () => {
    const received: ToastMessage[] = [];
    const unsubscribe = subscribeToasts((message) => received.push(message));
    resolveRequest();

    await http.post('/api/v1/admin/future-action');

    expect(received).toEqual([]);
    unsubscribe();
  });

  it('vẫn hiển thị Toast lỗi khi đăng nhập thất bại', async () => {
    const received: ToastMessage[] = [];
    const unsubscribe = subscribeToasts((message) => received.push(message));
    rejectRequest(400, { code: 'AUTH_INVALID_CREDENTIALS', message: 'Sai thông tin đăng nhập.' });

    await expect(http.post('/api/v1/auth/login')).rejects.toMatchObject({ status: 400 });

    expect(received).toContainEqual(
      expect.objectContaining({ tone: 'error', message: 'Sai thông tin đăng nhập.' }),
    );
    unsubscribe();
  });

  it('đổi lỗi API thành Toast thất bại dễ hiểu', async () => {
    const received: ToastMessage[] = [];
    const unsubscribe = subscribeToasts((message) => received.push(message));
    rejectRequest(500, { code: 'INTERNAL_ERROR', message: 'stack trace' });

    await expect(http.post('/api/v1/reports', {})).rejects.toMatchObject({ status: 500 });

    expect(received).toMatchObject([
      { tone: 'error', message: 'Máy chủ đang gặp sự cố. Vui lòng thử lại sau.' },
    ]);
    unsubscribe();
  });
});

describe('HTTP user-facing errors', () => {
  it.each([
    'Backend chưa triển khai chức năng này.',
    'BE chưa có API.',
    'Endpoint /api/v1/items chưa hỗ trợ.',
    'Feature not implemented',
  ])('ẩn thông báo kỹ thuật: %s', (message) => {
    expect(userFacingErrorMessage(message)).toBe('Hiện chưa thể thực hiện thao tác này.');
  });

  it('giữ thông báo nghiệp vụ thân thiện', () => {
    expect(userFacingErrorMessage('Số dư Credit không đủ.')).toBe('Số dư Credit không đủ.');
  });

  it('lọc thông báo kỹ thuật trước khi chuyển tới trang và Toast', async () => {
    const received: ToastMessage[] = [];
    const unsubscribe = subscribeToasts((message) => received.push(message));
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    rejectRequest(409, {
      code: 'FEATURE_UNAVAILABLE',
      message: 'Backend endpoint /api/v1/reports chưa được triển khai.',
    });

    await expect(http.post('/api/v1/reports', {})).rejects.toMatchObject({
      message: 'Hiện chưa thể thực hiện thao tác này.',
    });
    expect(received).toContainEqual(
      expect.objectContaining({
        tone: 'error',
        message: 'Hiện chưa thể thực hiện thao tác này.',
      }),
    );
    expect(consoleError).toHaveBeenCalledOnce();
    unsubscribe();
  });
});
