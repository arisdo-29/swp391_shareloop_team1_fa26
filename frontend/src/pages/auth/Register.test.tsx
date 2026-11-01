// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '../../services/authApi';
import type { ApiError } from '../../services/http';
import { Register } from './Register';

vi.mock('../../services/authApi', () => ({
  authApi: { register: vi.fn() },
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function apiError(code: string, status: number, extra: Partial<ApiError> = {}): ApiError {
  return { code, status, message: 'Thông báo thô từ máy chủ.', ...extra };
}

async function submitValidForm() {
  await userEvent.type(screen.getByLabelText(/Họ và tên/), 'Nguyễn Lan An');
  await userEvent.type(screen.getByLabelText(/Email/), 'lan.an@gmail.com');
  await userEvent.type(screen.getByLabelText(/Số điện thoại/), '0901234567');
  await userEvent.type(screen.getByLabelText('Mật khẩu'), 'MatKhau@12345');
  await userEvent.click(screen.getByRole('button', { name: 'Đăng ký' }));
}

function renderRegister() {
  return render(
    <MemoryRouter>
      <Register />
    </MemoryRouter>,
  );
}

describe('Register lỗi từ backend', () => {
  it('báo email dùng một lần ngay dưới ô email', async () => {
    // given
    vi.mocked(authApi.register).mockRejectedValue(
      apiError('AUTH_EMAIL_DOMAIN_NOT_ALLOWED', 400, { field: 'email' }),
    );
    renderRegister();

    // when
    await submitValidForm();

    // then
    expect(
      await screen.findByText(
        'Không thể đăng ký bằng email dùng một lần. Vui lòng dùng email cá nhân của bạn.',
      ),
    ).toBeTruthy();
    expect(screen.queryByText('Thông báo thô từ máy chủ.')).toBeNull();
  });

  it('báo quá nhiều tài khoản từ cùng mạng ở đầu form', async () => {
    // given
    vi.mocked(authApi.register).mockRejectedValue(apiError('AUTH_TOO_MANY_ACCOUNTS_FROM_IP', 429));
    renderRegister();

    // when
    await submitValidForm();

    // then
    expect(
      await screen.findByText(
        'Mạng của bạn đã tạo quá nhiều tài khoản trong 24 giờ qua. Vui lòng thử lại sau.',
      ),
    ).toBeTruthy();
  });

  it('báo trùng số điện thoại dù backend không gắn field', async () => {
    // given
    vi.mocked(authApi.register).mockRejectedValue(apiError('AUTH_PHONE_ALREADY_USED', 409));
    renderRegister();

    // when
    await submitValidForm();

    // then
    const message = await screen.findByText('Số điện thoại đã được sử dụng. Vui lòng dùng số khác.');
    const phoneInput = screen.getByLabelText(/Số điện thoại/);
    expect(phoneInput.closest('div')?.textContent).toContain(message.textContent ?? '');
  });

  it('hiện câu tiếng Việt khi không kết nối được máy chủ', async () => {
    // given
    vi.mocked(authApi.register).mockRejectedValue({
      code: 'NETWORK_ERROR',
      status: 0,
      message: 'Không kết nối được máy chủ, vui lòng thử lại.',
    });
    renderRegister();

    // when
    await submitValidForm();

    // then
    expect(await screen.findByText('Không kết nối được máy chủ, vui lòng thử lại.')).toBeTruthy();
    expect(screen.queryByText(/Request failed/)).toBeNull();
  });
});

describe('Register kiểm tra dữ liệu phía trình duyệt', () => {
  it('hiện lỗi ngay khi rời từng ô không hợp lệ', async () => {
    renderRegister();
    const user = userEvent.setup();

    const nameInput = screen.getByLabelText(/Họ và tên/);
    await user.click(nameInput);
    await user.tab();
    expect(screen.getByText('Vui lòng nhập họ và tên.')).toBeTruthy();
    expect(nameInput.getAttribute('aria-invalid')).toBe('true');

    const emailInput = screen.getByLabelText(/Email/);
    await user.type(emailInput, 'email-sai');
    await user.tab();
    expect(screen.getByText('Email không đúng định dạng.')).toBeTruthy();

    const phoneInput = screen.getByLabelText(/Số điện thoại/);
    await user.type(phoneInput, '12345');
    await user.tab();
    expect(
      screen.getByText('Số điện thoại phải có dạng 0xxxxxxxxx hoặc +84xxxxxxxxx.'),
    ).toBeTruthy();

    const passwordInput = screen.getByLabelText('Mật khẩu');
    await user.type(passwordInput, 'ngan');
    await user.tab();
    expect(screen.getByText('Mật khẩu phải có từ 8–64 ký tự.')).toBeTruthy();
  });

  it('chỉ kiểm tra lại và xóa lỗi sau khi người dùng rời ô', async () => {
    renderRegister();
    const user = userEvent.setup();
    const emailInput = screen.getByLabelText(/Email/);

    await user.type(emailInput, 'email-sai');
    await user.tab();
    expect(screen.getByText('Email không đúng định dạng.')).toBeTruthy();

    await user.clear(emailInput);
    await user.type(emailInput, 'hop.le@example.com');
    expect(screen.getByText('Email không đúng định dạng.')).toBeTruthy();
    expect(emailInput.getAttribute('aria-invalid')).toBe('true');

    await user.tab();
    expect(screen.queryByText('Email không đúng định dạng.')).toBeNull();
    expect(emailInput.getAttribute('aria-invalid')).toBeNull();
  });

  it('hiện lỗi cho mọi ô và không gọi API khi submit form không hợp lệ', async () => {
    renderRegister();

    await userEvent.click(screen.getByRole('button', { name: 'Đăng ký' }));

    expect(screen.getByText('Vui lòng nhập họ và tên.')).toBeTruthy();
    expect(screen.getByText('Vui lòng nhập email.')).toBeTruthy();
    expect(screen.getByText('Vui lòng nhập số điện thoại.')).toBeTruthy();
    expect(screen.getByText('Vui lòng nhập mật khẩu.')).toBeTruthy();
    expect(authApi.register).not.toHaveBeenCalled();
  });
});
