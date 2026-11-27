// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminFinanceApi, type AdminLedgerPage, type AdminLedgerRow } from '../../services/adminFinanceApi';
import { AdminLedger } from './AdminLedger';

vi.mock('../../services/adminFinanceApi', () => ({
  adminFinanceApi: { ledger: vi.fn(), ledgerExcel: vi.fn() },
}));

// Bộ chọn người dùng có API riêng, ở đây chỉ cần biết trang gọi nó đúng cách.
vi.mock('./AdminUserPicker', () => ({
  AdminUserPicker: ({ onSelect }: { onSelect: (user: unknown) => void }) => (
    <button
      type="button"
      onClick={() => onSelect({ id: 11, fullName: 'Lan An', email: 'an@example.com' })}
    >
      Chọn Lan An
    </button>
  ),
}));

function row(overrides: Partial<AdminLedgerRow>): AdminLedgerRow {
  return {
    id: 1,
    createdAt: '2026-11-01T03:00:00Z',
    userId: 11,
    userName: 'Lan An',
    userEmail: 'an@example.com',
    type: 'TOP_UP',
    amount: 50,
    balanceAfter: 50,
    itemId: null,
    itemTitle: null,
    orderCode: null,
    note: null,
    createdByName: null,
    ...overrides,
  };
}

const page: AdminLedgerPage = {
  items: [
    row({ id: 3, type: 'ADMIN_ADJUST', amount: -5, balanceAfter: 45, note: 'Trừ nhầm', createdByName: 'Quản trị' }),
    row({ id: 2, type: 'POST_FEE', amount: -10, balanceAfter: 40, itemId: 77, itemTitle: 'Xe đạp cũ' }),
    row({ id: 1, orderCode: 'ABCD1234EFGH5678IJKL9012MNOP3456' }),
  ],
  page: 0,
  size: 20,
  totalElements: 3,
  totalIn: 50,
  totalOut: -15,
};

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminLedger />
    </MemoryRouter>,
  );
}

function NavigationProbe() {
  const location = useLocation();
  return (
    <div>
      <p data-testid="navigation-path">{location.pathname}</p>
      <p data-testid="navigation-state">{JSON.stringify(location.state)}</p>
    </div>
  );
}

beforeEach(() => {
  vi.mocked(adminFinanceApi.ledger).mockResolvedValue(page);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminLedger', () => {
  it('mở trang là tải toàn bộ sổ cái, hiện tổng vào và ra', async () => {
    renderPage();

    await screen.findByText('Xe đạp cũ');
    expect(adminFinanceApi.ledger).toHaveBeenCalledWith({ page: 0, size: 20 });
    expect(screen.getByText('+50 Credit')).toBeTruthy();
    expect(screen.getByText('-15 Credit')).toBeTruthy();
  });

  it('hiện nhãn loại bằng tiếng Việt, số dương xanh số âm đỏ và không lộ enum thô', async () => {
    renderPage();
    await screen.findByText('Xe đạp cũ');

    const table = screen.getByRole('table');
    expect(within(table).getByText('Phí đăng bài')).toBeTruthy();
    expect(within(table).getByText('Admin điều chỉnh')).toBeTruthy();
    expect(within(table).getByText('Nạp Credit')).toBeTruthy();
    expect(within(table).getByText('+50').className).toContain('text-success');
    expect(within(table).getByText('-10').className).toContain('text-error');
    const text = document.body.textContent ?? '';
    for (const hidden of ['TOP_UP', 'POST_FEE', 'ADMIN_ADJUST']) {
      expect(text).not.toContain(hidden);
    }
  });

  it('tên bài mở trang chi tiết bài và mã đơn mở trang nạp tiền đã lọc theo mã', async () => {
    renderPage();
    await screen.findByText('Xe đạp cũ');

    const itemLink = screen.getByRole('link', { name: 'Xe đạp cũ' });
    expect(itemLink.getAttribute('href')).toBe('/admin/items/77');
    const orderLink = screen.getByRole('link', {
      name: 'Mã đơn: ABCD1234EFGH5678IJKL9012MNOP3456',
    });
    expect(orderLink.getAttribute('href')).toBe(
      '/admin/finance/topup-orders?q=ABCD1234EFGH5678IJKL9012MNOP3456',
    );
  });

  it('mở modal chi tiết chỉ đọc với dữ liệu và liên kết bài đăng API cung cấp', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Xe đạp cũ');

    const itemRow = screen.getByText('Xe đạp cũ').closest('tr') as HTMLElement;
    await user.click(within(itemRow).getByRole('button', { name: 'Xem chi tiết' }));

    const dialog = screen.getByRole('dialog', { name: 'Chi tiết giao dịch Credit' });
    expect(within(dialog).getByText('#2')).toBeTruthy();
    expect(within(dialog).getByText('Phí đăng bài')).toBeTruthy();
    expect(within(dialog).getByText('-10 Credit').className).toContain('text-error');
    expect(within(dialog).getByText('40 Credit')).toBeTruthy();
    expect(within(dialog).getByText('Trừ Credit đăng bài: Xe đạp cũ')).toBeTruthy();
    const relatedItem = within(dialog).getByRole('link', { name: 'Xe đạp cũ' });
    expect(relatedItem.getAttribute('href')).toBe('/admin/items/77');
    expect(within(dialog).getByRole('link', { name: 'Xem chi tiết bài đăng' })).toBeTruthy();
    expect(within(dialog).queryByText('Số dư trước giao dịch')).toBeNull();

    await user.click(within(dialog).getByRole('button', { name: 'Đóng' }));
    expect(screen.queryByRole('dialog', { name: 'Chi tiết giao dịch Credit' })).toBeNull();
  });

  it('đi tới đúng trang chi tiết bài đăng và mang theo trạng thái quay lại', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/admin/finance/ledger']}>
        <Routes>
          <Route path="/admin/finance/ledger" element={<AdminLedger />} />
          <Route path="/admin/items/:itemId" element={<NavigationProbe />} />
        </Routes>
      </MemoryRouter>,
    );
    await screen.findByText('Xe đạp cũ');

    const itemRow = screen.getByText('Xe đạp cũ').closest('tr') as HTMLElement;
    await user.click(within(itemRow).getByRole('button', { name: 'Xem chi tiết' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('link', { name: 'Xem chi tiết bài đăng' }));

    expect(screen.getByTestId('navigation-path').textContent).toBe('/admin/items/77');
    expect(screen.getByTestId('navigation-state').textContent).toContain('"ledgerReturn"');
    expect(screen.getByTestId('navigation-state').textContent).toContain('"page":0');
  });

  it('khôi phục bộ lọc và trang phân trang khi quay lại từ trang chi tiết', async () => {
    render(
      <MemoryRouter
        initialEntries={[
          {
            pathname: '/admin/finance/ledger',
            state: {
              restoreLedger: {
                type: 'BOOST_FEE',
                user: null,
                keywordInput: 'xe đạp',
                fromDate: '2026-11-01',
                toDate: '2026-11-20',
                page: 2,
              },
            },
          },
        ]}
      >
        <AdminLedger />
      </MemoryRouter>,
    );

    await waitFor(() =>
      expect(adminFinanceApi.ledger).toHaveBeenCalledWith({
        type: 'BOOST_FEE',
        q: 'xe đạp',
        from: '2026-11-01',
        to: '2026-11-20',
        page: 2,
        size: 20,
      }),
    );
    expect((screen.getByLabelText('Loại giao dịch') as HTMLSelectElement).value).toBe('BOOST_FEE');
    expect((screen.getByLabelText('Tìm giao dịch Credit') as HTMLInputElement).value).toBe('xe đạp');
  });

  it('modal hiển thị Credit cộng màu xanh và đơn nạp liên quan', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Xe đạp cũ');

    const orderLink = screen.getByRole('link', {
      name: 'Mã đơn: ABCD1234EFGH5678IJKL9012MNOP3456',
    });
    const topUpRow = orderLink.closest('tr') as HTMLElement;
    await user.click(within(topUpRow).getByRole('button', { name: 'Xem chi tiết' }));

    const dialog = screen.getByRole('dialog', { name: 'Chi tiết giao dịch Credit' });
    expect(within(dialog).getByText('+50 Credit').className).toContain('text-success');
    const relatedOrder = within(dialog).getByRole('link', {
      name: 'Mã đơn: ABCD1234EFGH5678IJKL9012MNOP3456',
    });
    expect(relatedOrder.getAttribute('href')).toBe(
      '/admin/finance/topup-orders?q=ABCD1234EFGH5678IJKL9012MNOP3456',
    );
  });

  it('hiển thị nội dung giao dịch theo đầy đủ loại giao dịch thực tế từ API', async () => {
    vi.mocked(adminFinanceApi.ledger).mockResolvedValue({
      ...page,
      items: [
        row({ id: 1, type: 'TOP_UP', orderCode: 'NAP-001' }),
        row({ id: 2, type: 'POST_FEE', amount: -10, itemId: 21, itemTitle: 'Bàn học gỗ' }),
        row({ id: 3, type: 'EDIT_FEE', amount: -2, itemId: 22, itemTitle: 'Ghế học sinh' }),
        row({ id: 4, type: 'RENEW_FEE', amount: -3, itemId: 23, itemTitle: 'Tủ sách nhỏ' }),
        row({ id: 5, type: 'BOOST_FEE', amount: -4, itemId: 24, itemTitle: 'Xe đạp trẻ em' }),
        row({ id: 6, type: 'AI_SEARCH_FEE', amount: -1 }),
        row({ id: 7, type: 'AI_SEARCH_FEE', amount: 1, note: 'Hoàn phí AI #6' }),
        row({ id: 8, type: 'ADMIN_ADJUST', amount: 5, note: 'Hỗ trợ sự kiện' }),
        row({ id: 9, type: 'ADMIN_ADJUST', amount: -5, note: 'Điều chỉnh sai lệch' }),
      ],
      totalElements: 9,
    });

    renderPage();
    await screen.findByText('Bàn học gỗ');

    const table = screen.getByRole('table');
    expect(within(table).getByRole('columnheader', { name: 'Nội dung giao dịch' })).toBeTruthy();
    expect(within(table).getByRole('columnheader', { name: 'Xem chi tiết' })).toBeTruthy();
    const tableText = table.textContent ?? '';
    for (const content of [
      'Nạp Credit thành công – Mã đơn: NAP-001',
      'Trừ Credit đăng bài: Bàn học gỗ',
      'Trừ Credit sửa bài: Ghế học sinh',
      'Trừ Credit gia hạn bài: Tủ sách nhỏ',
      'Trừ Credit đẩy bài: Xe đạp trẻ em',
      'Trừ Credit sử dụng Trợ lý AI',
      'Hoàn Credit – Hoàn phí AI #6',
      'Admin cộng Credit – Hỗ trợ sự kiện',
      'Admin trừ Credit – Điều chỉnh sai lệch',
    ]) {
      expect(tableText).toContain(content);
    }
  });

  it('không tự tạo dữ liệu chi tiết khi API không cung cấp', async () => {
    vi.mocked(adminFinanceApi.ledger).mockResolvedValue({
      ...page,
      items: [
        row({ id: 1, type: 'TOP_UP' }),
        row({ id: 2, type: 'POST_FEE', amount: -10, itemId: 77 }),
        row({ id: 3, type: 'ADMIN_ADJUST', amount: 5 }),
      ],
    });

    renderPage();
    await screen.findByText('Nạp Credit thành công');

    const tableText = screen.getByRole('table').textContent ?? '';
    expect(tableText).toContain('Trừ Credit đăng bài');
    expect(tableText).toContain('Admin cộng Credit');
    expect(tableText).not.toContain('Bài đã xóa');
    expect(tableText).not.toContain('Mã đơn:');
  });

  it('hiện người thực hiện ở dòng Admin điều chỉnh', async () => {
    renderPage();
    await screen.findByText('Xe đạp cũ');

    expect(screen.getByText('Thực hiện bởi Quản trị')).toBeTruthy();
  });

  it('lọc theo loại, người dùng và khoảng ngày gửi đúng tham số', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Xe đạp cũ');

    await user.selectOptions(screen.getByLabelText('Loại giao dịch'), 'POST_FEE');
    await user.click(screen.getByRole('button', { name: 'Chọn Lan An' }));
    await user.type(screen.getByLabelText('Từ ngày'), '2026-11-01');

    await waitFor(() =>
      expect(adminFinanceApi.ledger).toHaveBeenLastCalledWith({
        type: 'POST_FEE',
        userId: 11,
        from: '2026-11-01',
        page: 0,
        size: 20,
      }),
    );
  });

  it('ô tìm kiếm gửi từ khoá sau khi ngừng gõ', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Xe đạp cũ');

    await user.type(screen.getByLabelText('Tìm giao dịch Credit'), 'trừ nhầm');

    await waitFor(() =>
      expect(adminFinanceApi.ledger).toHaveBeenLastCalledWith({ q: 'trừ nhầm', page: 0, size: 20 }),
    );
  });

  it('khoảng ngày ngược thì báo lỗi', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Xe đạp cũ');

    await user.type(screen.getByLabelText('Từ ngày'), '2026-11-20');
    await user.type(screen.getByLabelText('Đến ngày'), '2026-11-10');

    expect(await screen.findByText('"Từ ngày" không được sau "Đến ngày".')).toBeTruthy();
  });

  it('xuất Excel theo bộ lọc đang chọn', async () => {
    const user = userEvent.setup();
    const createObjectURL = vi.fn(() => 'blob:xlsx');
    const revokeObjectURL = vi.fn();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    vi.mocked(adminFinanceApi.ledgerExcel).mockResolvedValue({
      blob: new Blob(['a']),
      fileName: 'lich-su-giao-dich-credit.xlsx',
    });
    renderPage();
    await screen.findByText('Xe đạp cũ');
    await user.selectOptions(screen.getByLabelText('Loại giao dịch'), 'TOP_UP');

    await user.click(screen.getByRole('button', { name: 'Xuất Excel' }));

    await waitFor(() => expect(adminFinanceApi.ledgerExcel).toHaveBeenCalledWith({ type: 'TOP_UP' }));
    expect(createObjectURL).toHaveBeenCalled();
    expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe('lich-su-giao-dich-credit.xlsx');
  });

  it('báo lỗi khi tải thất bại và cho thử lại', async () => {
    const user = userEvent.setup();
    vi.mocked(adminFinanceApi.ledger)
      .mockRejectedValueOnce({ status: 0, message: 'Không kết nối được máy chủ, vui lòng thử lại.' })
      .mockResolvedValueOnce(page);
    renderPage();
    expect(await screen.findByText('Không kết nối được máy chủ, vui lòng thử lại.')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Thử lại' }));

    expect(await screen.findByText('Xe đạp cũ')).toBeTruthy();
  });
});
