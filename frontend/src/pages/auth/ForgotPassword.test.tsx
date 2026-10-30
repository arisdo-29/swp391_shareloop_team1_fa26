// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '../../services/authApi';
import { ForgotPassword } from './ForgotPassword';

vi.mock('../../services/authApi', () => ({
  authApi: {
    forgotPassword: vi.fn(),
    resetPassword: vi.fn(),
  },
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderResetPassword() {
  return render(
    <MemoryRouter
      initialEntries={[
        {
          pathname: '/forgot-password/verify',
          state: {
            email: 'an@example.com',
            otpExpiresInSeconds: 300,
            resendCooldownSeconds: 0,
          },
        },
      ]}
    >
      <Routes>
        <Route path="/forgot-password/verify" element={<ForgotPassword />} />
        <Route path="/login" element={<div>Login destination</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Reset Password OTP', () => {
  it('hiển thị và gửi chính xác mã 123456', async () => {
    vi.mocked(authApi.resetPassword).mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderResetPassword();
    const inputs = screen.getAllByLabelText(/Số OTP/) as HTMLInputElement[];

    await user.click(inputs[0]);
    await user.keyboard('123456');

    expect(inputs.map((input) => input.value)).toEqual(['1', '2', '3', '4', '5', '6']);

    await user.type(screen.getByLabelText('Mật khẩu mới'), 'MatKhau@123');
    await user.type(screen.getByLabelText('Xác nhận mật khẩu mới'), 'MatKhau@123');
    await user.click(screen.getByRole('button', { name: 'Đặt lại mật khẩu' }));

    await waitFor(() => {
      expect(authApi.resetPassword).toHaveBeenCalledWith({
        email: 'an@example.com',
        otp: '123456',
        newPassword: 'MatKhau@123',
      });
    });
    expect(await screen.findByText('Login destination')).toBeTruthy();
  });
});
