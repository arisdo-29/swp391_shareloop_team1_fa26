// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminFinanceApi, type AdminLedgerPage, type AdminLedgerRow } from '../../services/adminFinanceApi';
import { AdminLedger } from './AdminLedger';

vi.mock('../../services/adminFinanceApi', () => ({
  adminFinanceApi: { ledger: vi.fn(), ledgerCsv: vi.fn() },
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
    for (const hidden of ['TOP_UP', 'POST_FEE', 'ADMIN_ADJUST', 'ABCD1234EFGH5678IJKL9012MNOP3456']) {
      expect(text).not.toContain(hidden);
    }
  });

  it('tên bài mở trang chi tiết bài và mã đơn mở trang nạp tiền đã lọc theo mã', async () => {
    renderPage();
    await screen.findByText('Xe đạp cũ');

    const itemLink = screen.getByRole('link', { name: 'Xe đạp cũ' });
    expect(itemLink.getAttribute('href')).toBe('/admin/items/77');
    const orderLink = screen.getByRole('link', { name: '#ABCD...3456' });
    expect(orderLink.getAttribute('href')).toBe(
      '/admin/finance/topup-orders?q=ABCD1234EFGH5678IJKL9012MNOP3456',
    );
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

  it('xuất CSV theo bộ lọc đang chọn', async () => {
    const user = userEvent.setup();
    Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:csv'), revokeObjectURL: vi.fn() });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    vi.mocked(adminFinanceApi.ledgerCsv).mockResolvedValue({
      blob: new Blob(['a']),
      fileName: 'lich-su-giao-dich-credit.csv',
    });
    renderPage();
    await screen.findByText('Xe đạp cũ');
    await user.selectOptions(screen.getByLabelText('Loại giao dịch'), 'TOP_UP');

    await user.click(screen.getByRole('button', { name: 'Xuất CSV' }));

    await waitFor(() => expect(adminFinanceApi.ledgerCsv).toHaveBeenCalledWith({ type: 'TOP_UP' }));
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
