// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminItemApi, type AdminItemRow } from '../../services/adminItemApi';
import { catalogApi } from '../../services/catalogApi';
import { AdminItems } from './AdminItems';

vi.mock('../../services/adminItemApi', () => ({
  adminItemApi: { list: vi.fn(), hide: vi.fn(), unhide: vi.fn() },
}));
vi.mock('../../services/catalogApi', () => ({
  catalogApi: { categories: vi.fn() },
}));

function row(overrides: Partial<AdminItemRow>): AdminItemRow {
  return {
    id: 1,
    title: 'Xe đạp cũ',
    imageUrl: null,
    ownerId: 11,
    ownerName: 'Lan An',
    ownerEmail: 'an@example.com',
    categoryName: 'Đồ thể thao',
    offerType: 'GIVE',
    status: 'APPROVED',
    active: true,
    hiddenSource: null,
    postFeePaid: 0,
    pendingRequestCount: 2,
    createdAt: '2026-11-01T03:00:00Z',
    approvedAt: '2026-11-02T03:00:00Z',
    expireAt: '2026-12-02T03:00:00Z',
    boostedUntil: null,
    boosting: false,
    ...overrides,
  };
}

const items = [
  row({}),
  row({ id: 2, title: 'Sách giáo khoa', active: false, hiddenSource: 'ADMIN', postFeePaid: 5 }),
  row({ id: 3, title: 'Bàn học', status: 'RESERVED' }),
  row({ id: 4, title: 'Đèn bàn', active: false, hiddenSource: 'OWNER_LOCKED' }),
];

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminItems />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.mocked(adminItemApi.list).mockResolvedValue({ items, page: 0, size: 20, totalElements: items.length });
  vi.mocked(catalogApi.categories).mockResolvedValue([
    { id: 5, name: 'Đồ thể thao', parentId: null, restricted: false, boostable: true, sortOrder: 1, activeItemCount: 3 },
  ]);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminItems', () => {
  it('mở trang là tải mọi bài ở mọi trạng thái', async () => {
    renderPage();

    await screen.findByText('Xe đạp cũ');
    expect(adminItemApi.list).toHaveBeenCalledWith({ page: 0, size: 20 });
    expect(screen.getByText('Sách giáo khoa')).toBeTruthy();
  });

  it('hiện ai đã ẩn bài bằng chữ tiếng Việt và không lộ enum thô', async () => {
    renderPage();
    await screen.findByText('Xe đạp cũ');

    expect(screen.getByText('Đang ẩn: Admin đã ẩn')).toBeTruthy();
    expect(screen.getByText('Đang ẩn: Chủ bài bị khoá')).toBeTruthy();
    expect(within(screen.getByRole('table')).getByText('Đã giữ chỗ')).toBeTruthy();
    const text = document.body.textContent ?? '';
    for (const hidden of ['OWNER_LOCKED', 'RESERVED', 'APPROVED', 'GIVE']) {
      expect(text).not.toContain(hidden);
    }
  });

  it('lọc theo trạng thái, hiển thị, danh mục và chỉ bài đang đẩy gửi đúng tham số', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Xe đạp cũ');
    await screen.findByRole('option', { name: 'Đồ thể thao' });

    await user.selectOptions(screen.getByLabelText('Trạng thái'), 'APPROVED');
    await user.selectOptions(screen.getByLabelText('Hiển thị'), 'hidden');
    await user.selectOptions(screen.getByLabelText('Danh mục'), '5');
    await user.click(screen.getByLabelText('Chỉ bài đang đẩy'));

    await waitFor(() =>
      expect(adminItemApi.list).toHaveBeenLastCalledWith({
        status: 'APPROVED',
        active: false,
        categoryId: 5,
        boosted: true,
        page: 0,
        size: 20,
      }),
    );
  });

  it('nút Ẩn bị khoá ở bài đã có giao dịch', async () => {
    renderPage();
    await screen.findByText('Bàn học');

    const reservedRow = screen.getByText('Bàn học').closest('tr') as HTMLElement;
    expect((within(reservedRow).getByRole('button', { name: 'Ẩn' }) as HTMLButtonElement).disabled).toBe(true);
    const normalRow = screen.getByText('Xe đạp cũ').closest('tr') as HTMLElement;
    expect((within(normalRow).getByRole('button', { name: 'Ẩn' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('ẩn bài bắt buộc có lý do rồi gọi API và tải lại danh sách', async () => {
    const user = userEvent.setup();
    vi.mocked(adminItemApi.hide).mockResolvedValue({} as never);
    renderPage();
    await screen.findByText('Xe đạp cũ');

    await user.click(within(screen.getByText('Xe đạp cũ').closest('tr') as HTMLElement).getByRole('button', { name: 'Ẩn' }));
    const dialog = await screen.findByRole('dialog');
    const confirm = within(dialog).getByRole('button', { name: 'Ẩn bài' }) as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);
    await user.type(within(dialog).getByLabelText('Lý do ẩn (bắt buộc)'), 'Hàng giả theo báo cáo');
    await user.click(confirm);

    await waitFor(() => expect(adminItemApi.hide).toHaveBeenCalledWith(1, 'Hàng giả theo báo cáo'));
    await waitFor(() => expect(adminItemApi.list).toHaveBeenCalledTimes(2));
  });

  it('báo lỗi của backend ngay trong hộp thoại khi bài có giao dịch đang chạy', async () => {
    const user = userEvent.setup();
    vi.mocked(adminItemApi.hide).mockRejectedValue({
      status: 409,
      message: 'Bài đang có giao dịch chạy, hãy huỷ giao dịch ở trang Giao dịch trước khi ẩn bài.',
    });
    renderPage();
    await screen.findByText('Xe đạp cũ');
    await user.click(within(screen.getByText('Xe đạp cũ').closest('tr') as HTMLElement).getByRole('button', { name: 'Ẩn' }));
    const dialog = await screen.findByRole('dialog');

    await user.type(within(dialog).getByLabelText('Lý do ẩn (bắt buộc)'), 'Vi phạm nội quy');
    await user.click(within(dialog).getByRole('button', { name: 'Ẩn bài' }));

    expect(
      await within(dialog).findByText('Bài đang có giao dịch chạy, hãy huỷ giao dịch ở trang Giao dịch trước khi ẩn bài.'),
    ).toBeTruthy();
  });

  it('hiện lại bài đã ẩn gọi đúng API', async () => {
    const user = userEvent.setup();
    vi.mocked(adminItemApi.unhide).mockResolvedValue({} as never);
    renderPage();
    await screen.findByText('Sách giáo khoa');

    await user.click(
      within(screen.getByText('Sách giáo khoa').closest('tr') as HTMLElement).getByRole('button', { name: 'Hiện lại' }),
    );
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText('Lý do hiện lại (bắt buộc)'), 'Đã kiểm tra lại');
    await user.click(within(dialog).getByRole('button', { name: 'Hiện lại' }));

    await waitFor(() => expect(adminItemApi.unhide).toHaveBeenCalledWith(2, 'Đã kiểm tra lại'));
  });

  it('báo lỗi khi tải thất bại và cho thử lại', async () => {
    const user = userEvent.setup();
    vi.mocked(adminItemApi.list)
      .mockRejectedValueOnce({ status: 0, message: 'Không kết nối được máy chủ, vui lòng thử lại.' })
      .mockResolvedValueOnce({ items, page: 0, size: 20, totalElements: items.length });
    renderPage();
    expect(await screen.findByText('Không kết nối được máy chủ, vui lòng thử lại.')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Thử lại' }));

    expect(await screen.findByText('Xe đạp cũ')).toBeTruthy();
  });
});
