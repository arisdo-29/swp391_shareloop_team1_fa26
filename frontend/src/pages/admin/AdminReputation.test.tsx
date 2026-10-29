// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminConfigApi, type AdminConfigResponse } from '../../services/adminConfigApi';
import { AdminReputation } from './AdminReputation';

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

beforeEach(() => {
  vi.mocked(adminConfigApi.list).mockResolvedValue(configs);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.restoreAllMocks();
});

describe('AdminReputation cấu hình hạng', () => {
  it('UT-09: không còn phần cấp sao ân hạn', async () => {
    render(<AdminReputation />);

    expect(await screen.findByText('Kim cương')).toBeTruthy();
    expect(screen.queryByText(/sao ân hạn/i)).toBeNull();
    expect(screen.queryByRole('textbox', { name: 'Tìm người dùng' })).toBeNull();
  });

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

