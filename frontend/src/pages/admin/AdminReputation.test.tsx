// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminConfigApi, type AdminConfigResponse } from '../../services/adminConfigApi';
import { adminReputationApi } from '../../services/adminReputationApi';
import { adminUserApi, type AdminUserSummary } from '../../services/adminUserApi';
import { AdminReputation } from './AdminReputation';

vi.mock('../../services/adminReputationApi', () => ({
  adminReputationApi: { grantGraceStar: vi.fn() },
}));
vi.mock('../../services/adminUserApi', () => ({
  adminUserApi: { list: vi.fn() },
}));
vi.mock('../../services/adminConfigApi', () => ({
  adminConfigApi: { list: vi.fn(), update: vi.fn() },
}));

function config(
  key: string,
  value: string,
  label: string,
  group: AdminConfigResponse['group'] = 'REPUTATION',
): AdminConfigResponse {
  return {
    key,
    value,
    defaultValue: value,
    label,
    description: `Mô tả ${label}`,
    group,
    groupLabel: group === 'REPUTATION' ? 'Uy tín và hạng' : 'Hiển thị bài',
  };
}

const configs: AdminConfigResponse[] = [
  config('reputation_silver_min', '50', 'Điểm tối thiểu hạng Bạc'),
  config('reputation_gold_min', '200', 'Điểm tối thiểu hạng Vàng'),
  config('reputation_diamond_min', '500', 'Điểm tối thiểu hạng Kim cương'),
  config('max_active_items_bronze', '5', 'Ngưỡng phí thường Trao đổi hạng Đồng'),
  config('max_active_items_silver', '8', 'Ngưỡng phí thường Trao đổi hạng Bạc'),
  config('max_active_items_gold', '15', 'Ngưỡng phí thường Trao đổi hạng Vàng'),
  config('max_active_items_diamond', '-1', 'Ngưỡng phí thường Trao đổi hạng Kim cương'),
  config('post_display_days_gold', '45', 'Số ngày hiển thị bài hạng Vàng'),
  config('post_display_days', '30', 'Số ngày hiển thị bài', 'POSTING'),
  config('reputation_give_donor', '10', 'Điểm người cho khi hoàn tất'),
];

const lanAn: AdminUserSummary = {
  id: 42,
  fullName: 'Lan An',
  email: 'an@example.com',
  phone: '0900000042',
  role: 'USER',
  status: 'ACTIVE',
  locked: false,
  createdAt: '2026-10-01T00:00:00Z',
};

beforeEach(() => {
  vi.mocked(adminConfigApi.list).mockResolvedValue(configs);
  vi.mocked(adminUserApi.list).mockResolvedValue({ items: [lanAn], page: 0, size: 5, totalElements: 1 });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.restoreAllMocks();
});

async function selectUser(user: ReturnType<typeof userEvent.setup>) {
  await user.type(await screen.findByRole('textbox', { name: 'Tìm người dùng' }), 'lan');
  await user.click(await screen.findByRole('button', { name: /Lan An/ }));
}

describe('AdminReputation cấu hình hạng', () => {
  it('hiện ngưỡng phí thường theo hạng và không lặp key ở bảng quy tắc', async () => {
    render(<AdminReputation />);

    const rankTable = (await screen.findByText('Kim cương')).closest('table')!;
    expect(within(rankTable).getByRole('columnheader', { name: 'Số bài Trao đổi phí thường' })).toBeTruthy();
    expect(within(rankTable).getByText('Không giới hạn')).toBeTruthy();
    expect(within(rankTable).getByText('45')).toBeTruthy();
    // Ba hạng còn lại dùng số ngày hiển thị mặc định của hệ thống.
    expect(within(rankTable).getAllByText('30 ngày')).toHaveLength(3);
    const rules = screen.getByText('Điểm người cho khi hoàn tất').closest('table')!;
    expect(within(rules).queryByText('Điểm tối thiểu hạng Bạc')).toBeNull();
  });

  it('chặn điểm hạng không tăng dần ngay trên trang và không gọi API sửa', async () => {
    const user = userEvent.setup();
    render(<AdminReputation />);

    await user.click(await screen.findByRole('button', { name: 'Sửa Điểm tối thiểu hạng Bạc' }));
    const dialog = screen.getByRole('dialog', { name: 'Điểm tối thiểu hạng Bạc' });
    const valueInput = within(dialog).getByRole('textbox', { name: 'Giá trị mới' });
    await user.clear(valueInput);
    await user.type(valueInput, '250');
    await user.type(within(dialog).getByRole('textbox', { name: 'Lý do thay đổi' }), 'Điều chỉnh thử');
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận cập nhật' }));

    expect(within(dialog).getByRole('alert').textContent).toContain('phải tăng dần');
    expect(adminConfigApi.update).not.toHaveBeenCalled();
  });

  it('lưu điểm hạng hợp lệ kèm lý do và hiện thông báo thành công', async () => {
    const user = userEvent.setup();
    vi.mocked(adminConfigApi.update).mockResolvedValue(
      config('reputation_gold_min', '300', 'Điểm tối thiểu hạng Vàng'),
    );
    render(<AdminReputation />);

    await user.click(await screen.findByRole('button', { name: 'Sửa Điểm tối thiểu hạng Vàng' }));
    const dialog = screen.getByRole('dialog', { name: 'Điểm tối thiểu hạng Vàng' });
    const valueInput = within(dialog).getByRole('textbox', { name: 'Giá trị mới' });
    await user.clear(valueInput);
    await user.type(valueInput, '300');
    await user.type(within(dialog).getByRole('textbox', { name: 'Lý do thay đổi' }), 'Nâng ngưỡng hạng Vàng');
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận cập nhật' }));

    await waitFor(() =>
      expect(adminConfigApi.update).toHaveBeenCalledWith('reputation_gold_min', {
        value: '300',
        reason: 'Nâng ngưỡng hạng Vàng',
      }),
    );
    expect(await screen.findByText('Đã cập nhật "Điểm tối thiểu hạng Vàng".')).toBeTruthy();
  });
});

describe('AdminReputation cấp sao ân hạn', () => {
  it('khóa thao tác và hiển thị loading trong khi Backend xử lý', async () => {
    const user = userEvent.setup();
    let resolveRequest!: (value: { userId: number; trustStars: number; graceStarUsed: boolean }) => void;
    vi.mocked(adminReputationApi.grantGraceStar).mockReturnValue(
      new Promise((resolve) => {
        resolveRequest = resolve;
      }),
    );
    render(<AdminReputation />);

    await selectUser(user);
    await user.type(screen.getByRole('textbox', { name: 'Lý do cấp sao' }), 'Đã giải trình');
    await user.click(screen.getByRole('button', { name: 'Cấp 1 sao ân hạn' }));
    const dialog = screen.getByRole('dialog', { name: 'Xác nhận cấp sao ân hạn' });
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận cấp 1 sao' }));

    const loadingButton = screen.getByRole('button', { name: 'Đang cấp sao…' });
    expect((loadingButton as HTMLButtonElement).disabled).toBe(true);
    resolveRequest({ userId: 42, trustStars: 1, graceStarUsed: true });
    expect(await screen.findByText(/Số sao hiện tại: 1/)).toBeTruthy();
  });

  it('xác nhận bằng tên người dùng trước khi gửi và hiển thị kết quả từ Backend', async () => {
    const user = userEvent.setup();
    vi.mocked(adminReputationApi.grantGraceStar).mockResolvedValue({
      userId: 42,
      trustStars: 1,
      graceStarUsed: true,
    });
    render(<AdminReputation />);

    await selectUser(user);
    await user.type(screen.getByRole('textbox', { name: 'Lý do cấp sao' }), 'Đã giải trình');
    await user.click(screen.getByRole('button', { name: 'Cấp 1 sao ân hạn' }));

    const dialog = screen.getByRole('dialog', { name: 'Xác nhận cấp sao ân hạn' });
    expect(within(dialog).getByText('Lan An')).toBeTruthy();
    expect(within(dialog).queryByText('#42')).toBeNull();
    expect(within(dialog).getByText('Đã giải trình')).toBeTruthy();
    expect(within(dialog).getByText(/chỉ áp dụng một lần/)).toBeTruthy();
    expect(adminReputationApi.grantGraceStar).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận cấp 1 sao' }));

    expect(adminReputationApi.grantGraceStar).toHaveBeenCalledWith(42, 'Đã giải trình');
    expect(await screen.findByText(/Đã cấp sao ân hạn cho Lan An. Số sao hiện tại: 1/)).toBeTruthy();
  });

  it('không gọi mutation khi Admin hủy xác nhận', async () => {
    const user = userEvent.setup();
    render(<AdminReputation />);

    await selectUser(user);
    await user.type(screen.getByRole('textbox', { name: 'Lý do cấp sao' }), 'Kiểm tra hợp lệ');
    await user.click(screen.getByRole('button', { name: 'Cấp 1 sao ân hạn' }));
    await user.click(screen.getByRole('button', { name: 'Hủy' }));

    expect(adminReputationApi.grantGraceStar).not.toHaveBeenCalled();
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('chỉ cho cấp khi đã chọn người dùng và nhập lý do', async () => {
    const user = userEvent.setup();
    render(<AdminReputation />);
    const submitButton = (await screen.findByRole('button', {
      name: 'Cấp 1 sao ân hạn',
    })) as HTMLButtonElement;

    expect(submitButton.disabled).toBe(true);
    await user.type(screen.getByRole('textbox', { name: 'Lý do cấp sao' }), 'Đủ điều kiện');
    expect(submitButton.disabled).toBe(true);
    await selectUser(user);
    expect(submitButton.disabled).toBe(false);
  });

  it('hiển thị lỗi nghiệp vụ rõ ràng', async () => {
    const user = userEvent.setup();
    vi.mocked(adminReputationApi.grantGraceStar).mockRejectedValue({
      code: 'REPUTATION_GRACE_STAR_NOT_ELIGIBLE',
      status: 409,
    });
    render(<AdminReputation />);
    await selectUser(user);
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
