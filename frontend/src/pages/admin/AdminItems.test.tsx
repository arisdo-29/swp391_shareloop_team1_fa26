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
  row({ id: 5, title: 'Ghế gỗ', status: 'PENDING_REVIEW', approvedAt: null }),
  row({ id: 6, title: 'Quạt máy', status: 'TRADED', boosting: true, boostedUntil: '2026-11-20T03:00:00Z' }),
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
    // Danh sách (size 20) tải lại một lần sau khi ẩn, các lượt size 1 là thẻ thống kê.
    await waitFor(() =>
      expect(vi.mocked(adminItemApi.list).mock.calls.filter(([params]) => params.size === 20)).toHaveLength(2),
    );
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

  it('thẻ thống kê đọc tổng số toàn hệ thống, không đếm trang hiện tại', async () => {
    const totals: Record<string, number> = { all: 120, PENDING_REVIEW: 8, visible: 70, RESERVED: 6, TRADED: 30, hidden: 4 };
    vi.mocked(adminItemApi.list).mockImplementation(async (params) => {
      if (params.size === 20) return { items, page: 0, size: 20, totalElements: items.length };
      const key = params.active === false ? 'hidden' : params.active ? 'visible' : params.status ?? 'all';
      return { items: [], page: 0, size: 1, totalElements: totals[key] };
    });
    renderPage();

    const stats = screen.getByLabelText('Thống kê bài đăng');
    await waitFor(() => expect(within(stats).getByText('Tổng bài đăng').parentElement?.textContent).toContain('120'));
    expect(within(stats).getByText('Chờ duyệt').parentElement?.textContent).toContain('8');
    await waitFor(() => expect(within(stats).getByText('Đang hiển thị').parentElement?.textContent).toContain('70'));
    expect(within(stats).getByText('Đã giữ chỗ').parentElement?.textContent).toContain('6');
    expect(within(stats).getByText('Đã hoàn tất').parentElement?.textContent).toContain('30');
    expect(within(stats).getByText('Đã ẩn').parentElement?.textContent).toContain('4');
    expect(adminItemApi.list).toHaveBeenCalledWith({ status: 'APPROVED', active: true, page: 0, size: 1 });
    expect(adminItemApi.list).toHaveBeenCalledWith({ active: false, page: 0, size: 1 });
  });

  it('không ghi Đang hiện cho bài chưa duyệt, có nhãn Đang đẩy và nút Xem chi tiết', async () => {
    renderPage();
    await screen.findByText('Ghế gỗ');

    const pendingRow = screen.getByText('Ghế gỗ').closest('tr') as HTMLElement;
    expect(within(pendingRow).queryByText('Đang hiện')).toBeNull();
    expect(within(pendingRow).getByText('Chưa hiện, đang chờ duyệt')).toBeTruthy();
    const approvedRow = screen.getByText('Xe đạp cũ').closest('tr') as HTMLElement;
    expect(within(approvedRow).getByText('Đang hiện')).toBeTruthy();
    expect(within(approvedRow).getByRole('link', { name: 'Xem chi tiết' }).getAttribute('href')).toBe('/admin/items/1');

    const boostedRow = screen.getByText('Quạt máy').closest('tr') as HTMLElement;
    expect(within(boostedRow).getByText('Đang đẩy').getAttribute('title')).toContain('Đang được đẩy đến');
  });

  it('nút Ẩn bị khoá có tooltip giải thích lý do', async () => {
    renderPage();
    await screen.findByText('Bàn học');

    const reservedRow = screen.getByText('Bàn học').closest('tr') as HTMLElement;
    const hide = within(reservedRow).getByRole('button', { name: 'Ẩn' });
    expect(hide.parentElement?.getAttribute('title')).toContain('hủy giao dịch');
    expect(document.getElementById(hide.getAttribute('aria-describedby') ?? '')?.textContent).toContain('hủy giao dịch');
    const tradedRow = screen.getByText('Quạt máy').closest('tr') as HTMLElement;
    expect(within(tradedRow).getByRole('button', { name: 'Ẩn' }).parentElement?.getAttribute('title')).toBe(
      'Bài đã giao dịch xong nên không thể ẩn.',
    );
  });

  it('nút Xóa bộ lọc đưa mọi bộ lọc về mặc định', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Xe đạp cũ');
    const clear = screen.getByRole('button', { name: 'Xóa bộ lọc' }) as HTMLButtonElement;
    expect(clear.disabled).toBe(true);

    await user.selectOptions(screen.getByLabelText('Trạng thái'), 'APPROVED');
    await user.click(screen.getByLabelText('Chỉ bài đang đẩy'));
    await waitFor(() => expect(clear.disabled).toBe(false));
    await user.click(clear);

    await waitFor(() => expect(adminItemApi.list).toHaveBeenLastCalledWith({ page: 0, size: 20 }));
    expect((screen.getByLabelText('Trạng thái') as HTMLSelectElement).value).toBe('');
    expect((screen.getByLabelText('Chỉ bài đang đẩy') as HTMLInputElement).checked).toBe(false);
    expect(clear.disabled).toBe(true);
  });
});
