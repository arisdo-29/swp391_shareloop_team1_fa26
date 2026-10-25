// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '../services/authApi';
import { ChangePasswordForm } from './ChangePasswordForm';

vi.mock('../services/authApi', () => ({
  authApi: { changePassword: vi.fn() },
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

async function fillForm(current: string, next: string, confirm: string) {
  await userEvent.type(screen.getByLabelText('Mật khẩu hiện tại'), current);
  await userEvent.type(screen.getByLabelText('Mật khẩu mới'), next);
  await userEvent.type(screen.getByLabelText('Xác nhận mật khẩu mới'), confirm);
}

describe('ChangePasswordForm', () => {
  it('gọi API đổi mật khẩu và báo thành công khi dữ liệu hợp lệ', async () => {
    // given
    vi.mocked(authApi.changePassword).mockResolvedValue(undefined as never);
    render(<ChangePasswordForm />);
    await fillForm('MatKhauCu@1', 'MatKhauMoi@2', 'MatKhauMoi@2');

    // when
    await userEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }));

    // then
    expect(await screen.findByText('Đổi mật khẩu thành công.')).toBeTruthy();
    expect(authApi.changePassword).toHaveBeenCalledWith({
      currentPassword: 'MatKhauCu@1',
      newPassword: 'MatKhauMoi@2',
    });
  });

  it('khoá nút khi đang gửi để không bấm được hai lần', async () => {
    // given
    let finish: () => void = () => undefined;
    vi.mocked(authApi.changePassword).mockReturnValue(
      new Promise<never>((resolve) => {
        finish = () => resolve(undefined as never);
      }),
    );
    render(<ChangePasswordForm />);
    await fillForm('MatKhauCu@1', 'MatKhauMoi@2', 'MatKhauMoi@2');

    // when
    await userEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }));

    // then
    const sending = screen.getByRole('button', { name: 'Đang xử lý...' }) as HTMLButtonElement;
    expect(sending.disabled).toBe(true);
    finish();
    await waitFor(() => expect(authApi.changePassword).toHaveBeenCalledTimes(1));
  });

  it('báo lỗi khi mật khẩu xác nhận không khớp và không gọi API', async () => {
    // given
    render(<ChangePasswordForm />);
    await fillForm('MatKhauCu@1', 'MatKhauMoi@2', 'KhacHoan@3');

    // when
    await userEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }));

    // then
    expect(await screen.findByText('Mật khẩu xác nhận không khớp.')).toBeTruthy();
    expect(authApi.changePassword).not.toHaveBeenCalled();
  });
});
