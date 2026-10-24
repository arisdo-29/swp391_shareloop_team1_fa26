// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminConfigApi, type AdminConfigResponse } from '../../services/adminConfigApi';
import { AdminConfigs } from './AdminConfigs';

vi.mock('../../services/adminConfigApi', () => ({
  adminConfigApi: { list: vi.fn(), update: vi.fn(), updateByBody: vi.fn() },
}));

function config(
  key: string,
  value: string,
  label: string,
  group: AdminConfigResponse['group'],
  groupLabel: string,
): AdminConfigResponse {
  return {
    key,
    value,
    defaultValue: '5',
    label,
    description: `Mô tả ${label}`,
    group,
    groupLabel,
  };
}

const configs = [
  config('post_fee', '5', 'Phí đăng bài', 'FEES', 'Phí và nạp Credit'),
  config('boost_fee', '8', 'Phí đẩy bài', 'FEES', 'Phí và nạp Credit'),
  config('ai_free_per_day', '3', 'Lượt AI miễn phí mỗi ngày', 'AI', 'Trợ lý AI'),
  config('reputation_gold_min', '200', 'Điểm tối thiểu hạng Vàng', 'REPUTATION', 'Uy tín và hạng'),
];

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminConfigs />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.mocked(adminConfigApi.list).mockResolvedValue(configs);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminConfigs', () => {
  it('gom cấu hình theo nhóm, có tên và mô tả tiếng Việt, ẩn key thô và bỏ nhóm uy tín', async () => {
    renderPage();

    await screen.findByText('Phí đăng bài');
    expect(screen.getByRole('heading', { name: 'Phí và nạp Credit' })).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Trợ lý AI' })).toBeTruthy();
    expect(screen.getByText('Mô tả Phí đăng bài')).toBeTruthy();
    expect(screen.getAllByText('Giá trị hiện tại').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Mặc định').length).toBeGreaterThan(0);
    expect(screen.queryByText('post_fee')).toBeNull();
    expect(screen.queryByText('Điểm tối thiểu hạng Vàng')).toBeNull();
    expect(screen.getByRole('link', { name: 'Uy tín & Hạng' }).getAttribute('href')).toBe('/admin/reputation');
  });

  it('sửa một cấu hình cần giá trị mới và lý do rồi báo bằng tên cấu hình', async () => {
    const user = userEvent.setup();
    vi.mocked(adminConfigApi.update).mockResolvedValue(config('post_fee', '7', 'Phí đăng bài', 'FEES', 'Phí và nạp Credit'));
    renderPage();
    const row = (await screen.findByText('Phí đăng bài')).closest('tr')!;

    await user.click(within(row).getByRole('button', { name: 'Chỉnh sửa' }));
    const dialog = screen.getByRole('dialog', { name: 'Phí đăng bài' });
    const save = within(dialog).getByRole('button', { name: 'Xác nhận cập nhật' }) as HTMLButtonElement;
    expect(save.disabled).toBe(true);
    const valueInput = within(dialog).getByRole('textbox', { name: 'Giá trị mới' });
    await user.clear(valueInput);
    await user.type(valueInput, '7');
    await user.type(within(dialog).getByRole('textbox', { name: 'Lý do thay đổi' }), 'Điều chỉnh theo quý');
    await user.click(save);

    await waitFor(() =>
      expect(adminConfigApi.update).toHaveBeenCalledWith('post_fee', { value: '7', reason: 'Điều chỉnh theo quý' }),
    );
    expect(adminConfigApi.updateByBody).not.toHaveBeenCalled();
    expect(await screen.findByText('Đã cập nhật "Phí đăng bài".')).toBeTruthy();
  });

  it('dùng endpoint body cho cấu hình ngưỡng AI mà không gọi trùng endpoint theo key', async () => {
    const user = userEvent.setup();
    const aiThreshold = config('chat_ai_threshold', '0.7', 'Ngưỡng AI kiểm tra tin nhắn', 'AI', 'Trợ lý AI');
    vi.mocked(adminConfigApi.list).mockResolvedValue([...configs, aiThreshold]);
    vi.mocked(adminConfigApi.updateByBody).mockResolvedValue({ ...aiThreshold, value: '0.8' });
    renderPage();
    const row = (await screen.findByText('Ngưỡng AI kiểm tra tin nhắn')).closest('tr')!;

    await user.click(within(row).getByRole('button', { name: 'Chỉnh sửa' }));
    const dialog = screen.getByRole('dialog', { name: 'Ngưỡng AI kiểm tra tin nhắn' });
    const valueInput = within(dialog).getByRole('textbox', { name: 'Giá trị mới' });
    await user.clear(valueInput);
    await user.type(valueInput, '0.8');
    await user.type(within(dialog).getByRole('textbox', { name: 'Lý do thay đổi' }), 'Tăng ngưỡng AI');
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận cập nhật' }));

    await waitFor(() =>
      expect(adminConfigApi.updateByBody).toHaveBeenCalledWith({
        key: 'chat_ai_threshold',
        value: '0.8',
        reason: 'Tăng ngưỡng AI',
      }),
    );
    expect(adminConfigApi.update).not.toHaveBeenCalled();
  });

  it('giữ hộp thoại và hiện câu báo lỗi của Backend khi giá trị không hợp lệ', async () => {
    const user = userEvent.setup();
    vi.mocked(adminConfigApi.update).mockRejectedValue({
      status: 400,
      message: 'Giá trị cấu hình nằm ngoài khoảng cho phép.',
    });
    renderPage();
    const row = (await screen.findByText('Phí đăng bài')).closest('tr')!;

    await user.click(within(row).getByRole('button', { name: 'Chỉnh sửa' }));
    const dialog = screen.getByRole('dialog', { name: 'Phí đăng bài' });
    const valueInput = within(dialog).getByRole('textbox', { name: 'Giá trị mới' });
    await user.clear(valueInput);
    await user.type(valueInput, '9999');
    await user.type(within(dialog).getByRole('textbox', { name: 'Lý do thay đổi' }), 'Thử');
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận cập nhật' }));

    expect((await within(dialog).findByRole('alert')).textContent).toContain('nằm ngoài khoảng cho phép');
  });
});
