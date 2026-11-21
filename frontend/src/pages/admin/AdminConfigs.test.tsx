// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  adminConfigApi,
  type AdminConfigResponse,
  type ConfigChangePreview,
  type ConfigPreviewResponse,
} from '../../services/adminConfigApi';
import { AdminConfigs } from './AdminConfigs';

vi.mock('../../services/adminConfigApi', () => ({
  adminConfigApi: { list: vi.fn(), preview: vi.fn(), updateBatch: vi.fn() },
}));

function config(
  key: string,
  value: string,
  label: string,
  group: AdminConfigResponse['group'],
  groupLabel: string,
  defaultValue = '5',
): AdminConfigResponse {
  return {
    key,
    value,
    defaultValue,
    label,
    description: `Mô tả ${label}`,
    group,
    groupLabel,
  };
}

const configs = [
  config('post_fee', '5', 'Phí đăng bài', 'FEES', 'Phí và nạp Credit'),
  config('boost_fee', '8', 'Phí đẩy bài', 'FEES', 'Phí và nạp Credit'),
  config('boost_days', '3', 'Số ngày mỗi gói đẩy bài', 'POSTING', 'Đăng bài', '3'),
  config('boost_max_days', '6', 'Số ngày đẩy tối đa', 'POSTING', 'Đăng bài', '6'),
  config('report_serious_reasons', 'HARASSMENT', 'Lý do vi phạm nghiêm trọng', 'MODERATION', 'Kiểm duyệt', 'HARASSMENT'),
  config('ai_free_per_day', '3', 'Lượt AI miễn phí mỗi ngày', 'AI', 'Trợ lý AI'),
  config('reputation_gold_min', '200', 'Điểm tối thiểu hạng Vàng', 'REPUTATION', 'Hạng và Uy tín'),
];

const mainChange: ConfigChangePreview = {
  key: 'boost_days',
  label: 'Số ngày mỗi gói đẩy bài',
  oldValue: '3',
  newValue: '4',
  causedByKey: null,
  causedByLabel: null,
  note: null,
};

const syncedChange: ConfigChangePreview = {
  key: 'boost_max_days',
  label: 'Số ngày đẩy tối đa',
  oldValue: '6',
  newValue: '8',
  causedByKey: 'boost_days',
  causedByLabel: 'Số ngày mỗi gói đẩy bài',
  note: 'Đồng bộ theo Số ngày mỗi gói đẩy bài',
};

function previewOf(overrides: Partial<ConfigPreviewResponse> = {}): ConfigPreviewResponse {
  return { changes: [], errors: [], warnings: [], ...overrides };
}

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminConfigs />
    </MemoryRouter>,
  );
}

/** Mở hộp thoại sửa, nhập giá trị mới và lý do. Trả về hộp thoại để kiểm tra tiếp. */
async function openAndFill(label: string, value: string, reason = 'Điều chỉnh theo quý') {
  const user = userEvent.setup();
  const row = (await screen.findByText(label)).closest('tr')!;
  await user.click(within(row).getByRole('button', { name: 'Chỉnh sửa' }));
  const dialog = screen.getByRole('dialog', { name: label });
  const valueInput = within(dialog).getByRole('textbox', { name: 'Giá trị mới' });
  await user.clear(valueInput);
  await user.type(valueInput, value);
  await user.type(within(dialog).getByRole('textbox', { name: 'Lý do thay đổi' }), reason);
  return { user, dialog };
}

function saveButton(dialog: HTMLElement) {
  return within(dialog).getByRole('button', { name: 'Xác nhận cập nhật' }) as HTMLButtonElement;
}

beforeEach(() => {
  vi.mocked(adminConfigApi.list).mockResolvedValue(configs);
  vi.mocked(adminConfigApi.preview).mockResolvedValue(previewOf());
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
    expect(screen.getByRole('link', { name: 'Hạng và Uy tín' }).getAttribute('href')).toBe('/admin/reputation');
  });

  it('khoá nút lưu cho tới khi có giá trị mới và lý do, rồi lưu bằng một lần gọi theo lô', async () => {
    vi.mocked(adminConfigApi.preview).mockResolvedValue(
      previewOf({ changes: [{ ...mainChange, key: 'ai_free_per_day', oldValue: '3', newValue: '7' }] }),
    );
    vi.mocked(adminConfigApi.updateBatch).mockResolvedValue({
      configs: [config('ai_free_per_day', '7', 'Lượt AI miễn phí mỗi ngày', 'AI', 'Trợ lý AI')],
    });
    renderPage();
    const row = (await screen.findByText('Lượt AI miễn phí mỗi ngày')).closest('tr')!;
    const user = userEvent.setup();
    await user.click(within(row).getByRole('button', { name: 'Chỉnh sửa' }));
    const dialog = screen.getByRole('dialog', { name: 'Lượt AI miễn phí mỗi ngày' });
    expect(saveButton(dialog).disabled).toBe(true);

    const valueInput = within(dialog).getByRole('textbox', { name: 'Giá trị mới' });
    await user.clear(valueInput);
    await user.type(valueInput, '7');
    await user.type(within(dialog).getByRole('textbox', { name: 'Lý do thay đổi' }), 'Điều chỉnh theo quý');
    await waitFor(() => expect(saveButton(dialog).disabled).toBe(false));
    await user.click(saveButton(dialog));

    await waitFor(() =>
      expect(adminConfigApi.updateBatch).toHaveBeenCalledWith({
        changes: [{ key: 'ai_free_per_day', value: '7' }],
        reason: 'Điều chỉnh theo quý',
        acknowledgedWarnings: false,
      }),
    );
    expect(await screen.findByText('Đã cập nhật "Lượt AI miễn phí mỗi ngày".')).toBeTruthy();
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('hiện "Sẽ cập nhật kèm" với cũ → mới và ghi chú, rồi báo số cấu hình đã cập nhật', async () => {
    vi.mocked(adminConfigApi.preview).mockResolvedValue(previewOf({ changes: [mainChange, syncedChange] }));
    vi.mocked(adminConfigApi.updateBatch).mockResolvedValue({
      configs: [
        config('boost_days', '4', 'Số ngày mỗi gói đẩy bài', 'POSTING', 'Đăng bài', '3'),
        config('boost_max_days', '8', 'Số ngày đẩy tối đa', 'POSTING', 'Đăng bài', '6'),
      ],
    });
    renderPage();
    const { user, dialog } = await openAndFill('Số ngày mỗi gói đẩy bài', '4');

    expect(await within(dialog).findByText('Sẽ cập nhật kèm')).toBeTruthy();
    expect(within(dialog).getByText('Số ngày đẩy tối đa: 6 → 8')).toBeTruthy();
    expect(within(dialog).getByText('Đồng bộ theo Số ngày mỗi gói đẩy bài')).toBeTruthy();
    const syncBox = within(dialog).getByRole('checkbox', { name: 'Đồng bộ các cấu hình liên quan' }) as HTMLInputElement;
    expect(syncBox.checked).toBe(true);
    expect(within(dialog).getByText(/không ảnh hưởng đợt đang chạy/)).toBeTruthy();

    await waitFor(() => expect(saveButton(dialog).disabled).toBe(false));
    await user.click(saveButton(dialog));

    // Bật đồng bộ thì chỉ gửi khoá chính, Backend tự tính và lưu khoá đồng bộ.
    await waitFor(() =>
      expect(adminConfigApi.updateBatch).toHaveBeenCalledWith({
        changes: [{ key: 'boost_days', value: '4' }],
        reason: 'Điều chỉnh theo quý',
        acknowledgedWarnings: false,
      }),
    );
    expect(
      await screen.findByText('Đã cập nhật 2 cấu hình, gồm "Số ngày mỗi gói đẩy bài" và các cấu hình liên quan.'),
    ).toBeTruthy();
  });

  it('tắt đồng bộ thì gửi kèm khoá liên quan ở giá trị hiện tại và hiện lỗi Backend trả về', async () => {
    vi.mocked(adminConfigApi.preview).mockResolvedValue(previewOf({ changes: [mainChange, syncedChange] }));
    vi.mocked(adminConfigApi.updateBatch).mockRejectedValue({
      status: 400,
      message: 'Số ngày tối đa một đợt đẩy phải là bội số của số ngày mỗi gói (4). Gợi ý: 8',
      details: {
        errors: [
          {
            message: 'Số ngày tối đa một đợt đẩy phải là bội số của số ngày mỗi gói (4). Gợi ý: 8',
            keys: ['boost_days', 'boost_max_days'],
            errorCode: 'SETTING_CONFIG_RULE_VIOLATION',
          },
        ],
      },
    });
    renderPage();
    const { user, dialog } = await openAndFill('Số ngày mỗi gói đẩy bài', '4');
    await within(dialog).findByText('Sẽ cập nhật kèm');

    await user.click(within(dialog).getByRole('checkbox', { name: 'Đồng bộ các cấu hình liên quan' }));
    await user.click(saveButton(dialog));

    await waitFor(() =>
      expect(adminConfigApi.updateBatch).toHaveBeenCalledWith({
        changes: [
          { key: 'boost_days', value: '4' },
          { key: 'boost_max_days', value: '6' },
        ],
        reason: 'Điều chỉnh theo quý',
        acknowledgedWarnings: false,
      }),
    );
    expect((await within(dialog).findByRole('alert')).textContent).toContain('bội số của số ngày mỗi gói');
    expect(saveButton(dialog).disabled).toBe(true);
  });

  it('khoá nút lưu khi bản xem trước có lỗi và hiện lỗi bằng tiếng Việt', async () => {
    vi.mocked(adminConfigApi.preview).mockResolvedValue(
      previewOf({
        changes: [mainChange, { ...syncedChange, newValue: '40' }],
        errors: [
          {
            message: 'Số ngày đẩy tối đa (40) không được lớn hơn Số ngày hiển thị bài (30).',
            keys: ['boost_max_days'],
            errorCode: 'SETTING_CONFIG_RULE_VIOLATION',
          },
        ],
      }),
    );
    renderPage();
    const { dialog } = await openAndFill('Số ngày mỗi gói đẩy bài', '20');

    expect((await within(dialog).findByRole('alert')).textContent).toContain('không được lớn hơn Số ngày hiển thị bài');
    expect(saveButton(dialog).disabled).toBe(true);
    expect(adminConfigApi.updateBatch).not.toHaveBeenCalled();
  });

  it('bắt tích "Tôi đã hiểu cảnh báo" mới lưu được và gửi acknowledgedWarnings = true', async () => {
    vi.mocked(adminConfigApi.preview).mockResolvedValue(
      previewOf({
        changes: [{ ...mainChange, key: 'boost_fee', oldValue: '8', newValue: '0' }],
        warnings: [{ message: 'Phí đẩy bài được đặt bằng 0, thao tác này sẽ miễn phí hoàn toàn.', keys: ['boost_fee'], errorCode: null }],
      }),
    );
    vi.mocked(adminConfigApi.updateBatch).mockResolvedValue({
      configs: [config('boost_fee', '0', 'Phí đẩy bài', 'FEES', 'Phí và nạp Credit')],
    });
    renderPage();
    const { user, dialog } = await openAndFill('Phí đẩy bài', '0');

    expect(await within(dialog).findByText(/miễn phí hoàn toàn/)).toBeTruthy();
    expect(saveButton(dialog).disabled).toBe(true);

    await user.click(within(dialog).getByRole('checkbox', { name: 'Tôi đã hiểu cảnh báo' }));
    expect(saveButton(dialog).disabled).toBe(false);
    await user.click(saveButton(dialog));

    await waitFor(() =>
      expect(adminConfigApi.updateBatch).toHaveBeenCalledWith({
        changes: [{ key: 'boost_fee', value: '0' }],
        reason: 'Điều chỉnh theo quý',
        acknowledgedWarnings: true,
      }),
    );
  });

  it('giữ hộp thoại và hiện câu báo lỗi của Backend khi giá trị không hợp lệ', async () => {
    vi.mocked(adminConfigApi.updateBatch).mockRejectedValue({
      status: 400,
      message: 'Giá trị cấu hình nằm ngoài khoảng cho phép.',
    });
    renderPage();
    const { user, dialog } = await openAndFill('Phí đăng bài', '9999', 'Thử');
    await waitFor(() => expect(saveButton(dialog).disabled).toBe(false));

    await user.click(saveButton(dialog));

    expect((await within(dialog).findByRole('alert')).textContent).toContain('nằm ngoài khoảng cho phép');
    expect(screen.getByRole('dialog', { name: 'Phí đăng bài' })).toBeTruthy();
  });

  it('khoá không phải số nguyên thì không gọi xem trước và vẫn lưu được', async () => {
    vi.mocked(adminConfigApi.updateBatch).mockResolvedValue({
      configs: [
        config('report_serious_reasons', 'HARASSMENT,COUNTERFEIT', 'Lý do vi phạm nghiêm trọng', 'MODERATION', 'Kiểm duyệt'),
      ],
    });
    renderPage();
    const { user, dialog } = await openAndFill('Lý do vi phạm nghiêm trọng', 'HARASSMENT,COUNTERFEIT');

    await waitFor(() => expect(saveButton(dialog).disabled).toBe(false));
    await user.click(saveButton(dialog));

    await waitFor(() => expect(adminConfigApi.updateBatch).toHaveBeenCalledTimes(1));
    expect(adminConfigApi.preview).not.toHaveBeenCalled();
  });
});
