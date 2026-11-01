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
    expect(
      within(rankTable).getByRole('columnheader', { name: 'Số bài Trao đổi phí thường' }),
    ).toBeTruthy();
    expect(within(rankTable).getByText('Không giới hạn')).toBeTruthy();
    expect(within(rankTable).getAllByText('45')).toHaveLength(2);
    expect(within(rankTable).getAllByText('30')).toHaveLength(2);
    expect(within(rankTable).getByRole('columnheader', { name: 'Thao tác' })).toBeTruthy();
    for (const rank of ['Đồng', 'Bạc', 'Vàng', 'Kim cương']) {
      expect(
        within(rankTable).getByRole('button', { name: `Sửa nội dung hạng ${rank}` }),
      ).toBeTruthy();
    }
    expect(within(rankTable).getAllByRole('button')).toHaveLength(4);
    const rules = screen.getByText('Điểm người cho khi hoàn tất').closest('table')!;
    expect(within(rules).queryByText('Điểm tối thiểu hạng Bạc')).toBeNull();
  });

  it('mở modal sửa đồng thời ba nội dung của hạng', async () => {
    const user = userEvent.setup();
    render(<AdminReputation />);

    await user.click(await screen.findByRole('button', { name: 'Sửa nội dung hạng Vàng' }));
    const dialog = screen.getByRole('dialog', { name: 'Sửa nội dung hạng Vàng' });
    expect(within(dialog).getByRole('spinbutton', { name: 'Điểm tối thiểu' })).toBeTruthy();
    expect(
      within(dialog).getByRole('spinbutton', { name: /^Số bài Trao đổi phí thường/ }),
    ).toBeTruthy();
    expect(within(dialog).getByRole('spinbutton', { name: /^Số ngày hiển thị bài/ })).toBeTruthy();
    expect(within(dialog).getByRole('button', { name: 'Hủy' })).toBeTruthy();
    expect(within(dialog).getByRole('button', { name: 'Lưu thay đổi' })).toBeTruthy();
  });

  it('khóa điểm hạng Đồng ở 0 để giữ nguyên logic phân hạng', async () => {
    const user = userEvent.setup();
    render(<AdminReputation />);

    await user.click(await screen.findByRole('button', { name: 'Sửa nội dung hạng Đồng' }));
    const dialog = screen.getByRole('dialog', { name: 'Sửa nội dung hạng Đồng' });
    const minPoints = within(dialog).getByRole('spinbutton', { name: /^Điểm tối thiểu/ });
    expect(minPoints.getAttribute('value')).toBe('0');
    expect(minPoints.hasAttribute('disabled')).toBe(true);
    expect(within(dialog).getByText('Hạng Đồng luôn bắt đầu từ 0 điểm.')).toBeTruthy();
  });

  it('validate ba trường trước khi lưu và không gọi API khi dữ liệu sai', async () => {
    const user = userEvent.setup();
    render(<AdminReputation />);

    await user.click(await screen.findByRole('button', { name: 'Sửa nội dung hạng Bạc' }));
    const dialog = screen.getByRole('dialog', { name: 'Sửa nội dung hạng Bạc' });
    const minPoints = within(dialog).getByRole('spinbutton', { name: 'Điểm tối thiểu' });
    const feeLimit = within(dialog).getByRole('spinbutton', {
      name: /^Số bài Trao đổi phí thường/,
    });
    const displayDays = within(dialog).getByRole('spinbutton', {
      name: /^Số ngày hiển thị bài/,
    });
    await user.clear(minPoints);
    await user.type(minPoints, '250');
    await user.clear(feeLimit);
    await user.type(feeLimit, '-2');
    await user.clear(displayDays);
    await user.type(displayDays, '0');
    await user.type(
      within(dialog).getByRole('textbox', { name: 'Lý do thay đổi' }),
      'Điều chỉnh thử',
    );
    expect(within(dialog).getByText(/phải tăng dần/)).toBeTruthy();
    expect(within(dialog).getByText(/Số bài Trao đổi phí thường phải/)).toBeTruthy();
    expect(within(dialog).getByText(/Số ngày hiển thị bài phải/)).toBeTruthy();
    expect(
      within(dialog).getByRole('button', { name: 'Lưu thay đổi' }).hasAttribute('disabled'),
    ).toBe(true);
    expect(adminConfigApi.update).not.toHaveBeenCalled();
  });

  it('lưu các giá trị đã đổi qua API hiện có và cập nhật bảng', async () => {
    const user = userEvent.setup();
    vi.mocked(adminConfigApi.update).mockImplementation(async (key, input) => {
      const current = configs.find((entry) => entry.key === key)!;
      return { ...current, value: input.value };
    });
    render(<AdminReputation />);

    await user.click(await screen.findByRole('button', { name: 'Sửa nội dung hạng Vàng' }));
    const dialog = screen.getByRole('dialog', { name: 'Sửa nội dung hạng Vàng' });
    const changes: Array<[RegExp, string]> = [
      [/^Điểm tối thiểu/, '300'],
      [/^Số bài Trao đổi phí thường/, '20'],
      [/^Số ngày hiển thị bài/, '60'],
    ];
    for (const [label, value] of changes) {
      const input = within(dialog).getByRole('spinbutton', { name: label });
      await user.clear(input);
      await user.type(input, value);
    }
    await user.type(
      within(dialog).getByRole('textbox', { name: 'Lý do thay đổi' }),
      'Cập nhật quyền lợi hạng Vàng',
    );
    await user.click(within(dialog).getByRole('button', { name: 'Lưu thay đổi' }));

    await waitFor(() => expect(adminConfigApi.update).toHaveBeenCalledTimes(3));
    expect(adminConfigApi.update).toHaveBeenNthCalledWith(1, 'reputation_gold_min', {
      value: '300',
      reason: 'Cập nhật quyền lợi hạng Vàng',
    });
    expect(adminConfigApi.update).toHaveBeenNthCalledWith(2, 'max_active_items_gold', {
      value: '20',
      reason: 'Cập nhật quyền lợi hạng Vàng',
    });
    expect(adminConfigApi.update).toHaveBeenNthCalledWith(3, 'post_display_days_gold', {
      value: '60',
      reason: 'Cập nhật quyền lợi hạng Vàng',
    });
    expect(await screen.findByText('Đã cập nhật hạng "Vàng".')).toBeTruthy();
    const goldRow = screen.getByText('Vàng').closest('tr')!;
    expect(within(goldRow).getByText('300')).toBeTruthy();
    expect(within(goldRow).getByText('20')).toBeTruthy();
    expect(within(goldRow).getByText('60')).toBeTruthy();
  });
});
