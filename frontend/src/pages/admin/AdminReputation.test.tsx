// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminConfigApi, type AdminConfigResponse } from '../../services/adminConfigApi';
import { AdminReputation } from './AdminReputation';

vi.mock('../../services/adminConfigApi', () => ({
  adminConfigApi: { list: vi.fn(), preview: vi.fn(), updateBatch: vi.fn() },
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
    groupLabel: group === 'REPUTATION' ? 'Hạng và Uy tín' : 'Hiển thị bài',
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
    // Thứ tự điểm các hạng không còn kiểm ở trình duyệt, Backend kiểm khi lưu.
    expect(within(dialog).queryByText(/phải tăng dần/)).toBeNull();
    expect(within(dialog).getByText(/Số bài Trao đổi phí thường phải/)).toBeTruthy();
    expect(within(dialog).getByText(/Số ngày hiển thị bài phải/)).toBeTruthy();
    expect(
      within(dialog).getByRole('button', { name: 'Lưu thay đổi' }).hasAttribute('disabled'),
    ).toBe(true);
    expect(adminConfigApi.updateBatch).not.toHaveBeenCalled();
  });

  it('hiện lỗi thứ tự điểm các hạng do Backend trả về và giữ hộp thoại', async () => {
    const user = userEvent.setup();
    const orderMessage =
      'Điểm tối thiểu các hạng phải tăng dần: Bạc nhỏ hơn Vàng, Vàng nhỏ hơn Kim cương.';
    vi.mocked(adminConfigApi.updateBatch).mockRejectedValue({
      status: 400,
      message: orderMessage,
      details: {
        errors: [
          {
            message: orderMessage,
            keys: ['reputation_silver_min', 'reputation_gold_min', 'reputation_diamond_min'],
            errorCode: 'SETTING_RANK_THRESHOLD_ORDER',
          },
        ],
      },
    });
    render(<AdminReputation />);

    await user.click(await screen.findByRole('button', { name: 'Sửa nội dung hạng Bạc' }));
    const dialog = screen.getByRole('dialog', { name: 'Sửa nội dung hạng Bạc' });
    const minPoints = within(dialog).getByRole('spinbutton', { name: 'Điểm tối thiểu' });
    await user.clear(minPoints);
    await user.type(minPoints, '250');
    await user.type(within(dialog).getByRole('textbox', { name: 'Lý do thay đổi' }), 'Thử');
    await user.click(within(dialog).getByRole('button', { name: 'Lưu thay đổi' }));

    expect((await within(dialog).findByRole('alert')).textContent).toContain('phải tăng dần');
    expect(
      within(dialog).getByRole('button', { name: 'Lưu thay đổi' }).hasAttribute('disabled'),
    ).toBe(true);
    expect(screen.getByRole('dialog', { name: 'Sửa nội dung hạng Bạc' })).toBeTruthy();
  });

  it('báo cấu hình Backend đồng bộ kèm khi đổi số ngày hiển thị bài mặc định', async () => {
    const user = userEvent.setup();
    vi.mocked(adminConfigApi.updateBatch).mockResolvedValue({
      configs: [
        { ...configs.find((entry) => entry.key === 'post_display_days')!, value: '40' },
        { ...configs.find((entry) => entry.key === 'post_display_days_gold')!, value: '55' },
      ],
    });
    render(<AdminReputation />);

    await user.click(await screen.findByRole('button', { name: 'Sửa nội dung hạng Bạc' }));
    const dialog = screen.getByRole('dialog', { name: 'Sửa nội dung hạng Bạc' });
    const displayDays = within(dialog).getByRole('spinbutton', { name: /^Số ngày hiển thị bài/ });
    await user.clear(displayDays);
    await user.type(displayDays, '40');
    await user.type(within(dialog).getByRole('textbox', { name: 'Lý do thay đổi' }), 'Tăng ngày');
    await user.click(within(dialog).getByRole('button', { name: 'Lưu thay đổi' }));

    expect(
      await screen.findByText('Đã cập nhật hạng "Bạc", đồng bộ kèm: Số ngày hiển thị bài hạng Vàng.'),
    ).toBeTruthy();
  });

  it('bắt tích xác nhận khi Backend trả cảnh báo chưa xác nhận rồi gửi lại với acknowledgedWarnings = true', async () => {
    const user = userEvent.setup();
    vi.mocked(adminConfigApi.updateBatch)
      .mockRejectedValueOnce({
        status: 409,
        code: 'SETTING_CONFIG_WARNING_NOT_ACKNOWLEDGED',
        message: 'Thay đổi có cảnh báo.',
        details: {
          preview: {
            changes: [],
            errors: [],
            warnings: [
              {
                message: 'Số ngày hiển thị bài hạng Vàng (20) ngắn hơn Số ngày hiển thị bài (30).',
                keys: ['post_display_days_gold', 'post_display_days'],
                errorCode: null,
              },
            ],
          },
        },
      })
      .mockResolvedValueOnce({
        configs: [{ ...configs.find((entry) => entry.key === 'post_display_days_gold')!, value: '20' }],
      });
    render(<AdminReputation />);

    await user.click(await screen.findByRole('button', { name: 'Sửa nội dung hạng Vàng' }));
    const dialog = screen.getByRole('dialog', { name: 'Sửa nội dung hạng Vàng' });
    const displayDays = within(dialog).getByRole('spinbutton', { name: /^Số ngày hiển thị bài/ });
    await user.clear(displayDays);
    await user.type(displayDays, '20');
    await user.type(within(dialog).getByRole('textbox', { name: 'Lý do thay đổi' }), 'Thử');
    await user.click(within(dialog).getByRole('button', { name: 'Lưu thay đổi' }));

    expect(await within(dialog).findByText(/ngắn hơn Số ngày hiển thị bài/)).toBeTruthy();
    expect(
      within(dialog).getByRole('button', { name: 'Lưu thay đổi' }).hasAttribute('disabled'),
    ).toBe(true);
    await user.click(within(dialog).getByRole('checkbox', { name: 'Tôi đã hiểu cảnh báo' }));
    await user.click(within(dialog).getByRole('button', { name: 'Lưu thay đổi' }));

    await waitFor(() => expect(adminConfigApi.updateBatch).toHaveBeenCalledTimes(2));
    expect(adminConfigApi.updateBatch).toHaveBeenLastCalledWith({
      changes: [{ key: 'post_display_days_gold', value: '20' }],
      reason: 'Thử',
      acknowledgedWarnings: true,
    });
  });

  it('lưu cả ba giá trị đã đổi trong một lô và cập nhật bảng', async () => {
    const user = userEvent.setup();
    vi.mocked(adminConfigApi.updateBatch).mockImplementation(async (input) => ({
      configs: input.changes.map((change) => ({
        ...configs.find((entry) => entry.key === change.key)!,
        value: change.value,
      })),
    }));
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

    await waitFor(() => expect(adminConfigApi.updateBatch).toHaveBeenCalledTimes(1));
    expect(adminConfigApi.updateBatch).toHaveBeenCalledWith({
      changes: [
        { key: 'reputation_gold_min', value: '300' },
        { key: 'max_active_items_gold', value: '20' },
        { key: 'post_display_days_gold', value: '60' },
      ],
      reason: 'Cập nhật quyền lợi hạng Vàng',
      acknowledgedWarnings: false,
    });
    expect(await screen.findByText('Đã cập nhật hạng "Vàng".')).toBeTruthy();
    const goldRow = screen.getByText('Vàng').closest('tr')!;
    expect(within(goldRow).getByText('300')).toBeTruthy();
    expect(within(goldRow).getByText('20')).toBeTruthy();
    expect(within(goldRow).getByText('60')).toBeTruthy();
  });
});
