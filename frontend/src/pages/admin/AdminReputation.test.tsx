// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminReputationApi } from '../../services/adminReputationApi';
import { AdminReputation } from './AdminReputation';

vi.mock('../../services/adminReputationApi', () => ({
  adminReputationApi: { grantGraceStar: vi.fn() },
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.restoreAllMocks();
});

describe('AdminReputation', () => {
  it('khóa thao tác và hiển thị loading trong khi Backend xử lý', async () => {
    const user = userEvent.setup();
    let resolveRequest!: (value: {
      userId: number;
      trustStars: number;
      graceStarUsed: boolean;
    }) => void;
    vi.mocked(adminReputationApi.grantGraceStar).mockReturnValue(
      new Promise((resolve) => {
        resolveRequest = resolve;
      }),
    );
    render(<AdminReputation />);

    await user.type(screen.getByRole('textbox', { name: /^ID người dùng/ }), '42');
    await user.type(screen.getByRole('textbox', { name: 'Lý do cấp sao' }), 'Đã giải trình');
    await user.click(screen.getByRole('button', { name: 'Cấp 1 sao ân hạn' }));
    const dialog = screen.getByRole('dialog', { name: 'Xác nhận cấp sao ân hạn' });
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận cấp 1 sao' }));

    const loadingButton = screen.getByRole('button', { name: 'Đang cấp sao…' });
    expect((loadingButton as HTMLButtonElement).disabled).toBe(true);
    resolveRequest({ userId: 42, trustStars: 1, graceStarUsed: true });
    expect(await screen.findByText(/Số sao hiện tại: 1/)).toBeTruthy();
  });

  it('xác nhận trước khi gửi và hiển thị kết quả từ Backend', async () => {
    const user = userEvent.setup();
    vi.mocked(adminReputationApi.grantGraceStar).mockResolvedValue({
      userId: 42,
      trustStars: 1,
      graceStarUsed: true,
    });
    render(<AdminReputation />);

    await user.type(screen.getByRole('textbox', { name: /^ID người dùng/ }), '42');
    await user.type(screen.getByRole('textbox', { name: 'Lý do cấp sao' }), 'Đã giải trình');
    await user.click(screen.getByRole('button', { name: 'Cấp 1 sao ân hạn' }));

    const dialog = screen.getByRole('dialog', { name: 'Xác nhận cấp sao ân hạn' });
    expect(within(dialog).getByText('#42')).toBeTruthy();
    expect(within(dialog).getByText('Đã giải trình')).toBeTruthy();
    expect(within(dialog).getByText(/chỉ áp dụng một lần/)).toBeTruthy();
    expect(adminReputationApi.grantGraceStar).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận cấp 1 sao' }));

    expect(adminReputationApi.grantGraceStar).toHaveBeenCalledWith(42, 'Đã giải trình');
    expect(await screen.findByText(/Số sao hiện tại: 1/)).toBeTruthy();
  });

  it('không gọi mutation khi Admin hủy xác nhận', async () => {
    const user = userEvent.setup();
    render(<AdminReputation />);

    await user.type(screen.getByRole('textbox', { name: /^ID người dùng/ }), '7');
    await user.type(screen.getByRole('textbox', { name: 'Lý do cấp sao' }), 'Kiểm tra hợp lệ');
    await user.click(screen.getByRole('button', { name: 'Cấp 1 sao ân hạn' }));
    await user.click(screen.getByRole('button', { name: 'Hủy' }));

    expect(adminReputationApi.grantGraceStar).not.toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('chỉ chấp nhận ID là số nguyên dương', async () => {
    const user = userEvent.setup();
    render(<AdminReputation />);
    const input = screen.getByRole('textbox', { name: /^ID người dùng/ });
    const submitButton = screen.getByRole('button', { name: 'Cấp 1 sao ân hạn' });

    await user.type(input, '1.5');
    expect(screen.getByText('ID người dùng phải là số nguyên dương.')).toBeTruthy();
    expect((submitButton as HTMLButtonElement).disabled).toBe(true);
    await user.clear(input);
    await user.type(input, '0');
    expect((submitButton as HTMLButtonElement).disabled).toBe(true);
    await user.clear(input);
    await user.type(input, '12');
    await user.type(screen.getByRole('textbox', { name: 'Lý do cấp sao' }), 'Đủ điều kiện');
    expect((submitButton as HTMLButtonElement).disabled).toBe(false);
  });

  it('hiển thị lỗi nghiệp vụ rõ ràng', async () => {
    const user = userEvent.setup();
    vi.mocked(adminReputationApi.grantGraceStar).mockRejectedValue({
      code: 'REPUTATION_GRACE_STAR_NOT_ELIGIBLE',
      status: 409,
    });
    render(<AdminReputation />);
    await user.type(screen.getByRole('textbox', { name: /^ID người dùng/ }), '8');
    await user.type(screen.getByRole('textbox', { name: 'Lý do cấp sao' }), 'Không đủ điều kiện');
    await user.click(screen.getByRole('button', { name: 'Cấp 1 sao ân hạn' }));
    await user.click(screen.getByRole('button', { name: 'Xác nhận cấp 1 sao' }));

    await waitFor(() =>
      expect(within(screen.getByRole('dialog')).getByRole('alert').textContent).toContain(
        'đang có 0 sao',
      ),
    );
  });
});
