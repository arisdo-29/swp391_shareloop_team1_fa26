// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminFinanceApi } from '../../services/adminFinanceApi';
import { AdminFinanceReport } from './AdminFinancePages';

vi.mock('../../services/adminFinanceApi', () => ({
  adminFinanceApi: {
    report: vi.fn(),
    reportExcel: vi.fn(),
  },
}));

const summary = {
  from: null,
  to: null,
  cashOrderCount: 0,
  cashAmountVnd: 0,
  creditInTotal: 0,
  creditInByType: [],
  creditOutTotal: 0,
  creditOutByType: [],
  reconciliation: { cashCredits: 0, topUpCredits: 0, difference: 0, matched: true },
  cashByDay: [],
};

beforeEach(() => {
  vi.mocked(adminFinanceApi.report).mockResolvedValue(summary);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.restoreAllMocks();
});

// Trang tải biểu đồ bằng lazy import nên khi chạy chung cả bộ test có thể vượt 5 giây mặc định.
describe('AdminFinanceReport Excel', { timeout: 20_000 }, () => {
  it('xuất file bằng đúng bộ lọc đã áp dụng và thông báo thành công', async () => {
    const user = userEvent.setup();
    const blob = new Blob(['xlsx'], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' });
    vi.mocked(adminFinanceApi.reportExcel).mockResolvedValue({
      blob,
      fileName: 'bao-cao-tai-chinh_01-10-2026_31-10-2026.xlsx',
    });
    const createObjectURL = vi.fn(() => 'blob:finance-report');
    const revokeObjectURL = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectURL });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revokeObjectURL });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    render(
      <MemoryRouter>
        <AdminFinanceReport />
      </MemoryRouter>,
    );
    await screen.findByText('Đối chiếu tiền thu và Credit nạp');
    expect(screen.getByText('Khớp')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Credit trừ theo loại' })).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'Credit cộng theo loại' })).toBeNull();

    await user.type(screen.getByLabelText('Từ ngày'), '2026-10-01');
    await user.type(screen.getByLabelText('Đến ngày'), '2026-10-31');
    await user.click(screen.getByRole('button', { name: 'Lọc' }));
    await waitFor(() =>
      expect(adminFinanceApi.report).toHaveBeenLastCalledWith({
        from: '2026-10-01',
        to: '2026-10-31',
      }),
    );

    await user.click(screen.getByRole('button', { name: 'Xuất báo cáo Excel' }));
    await waitFor(() =>
      expect(adminFinanceApi.reportExcel).toHaveBeenCalledWith({
        from: '2026-10-01',
        to: '2026-10-31',
      }),
    );
    expect(createObjectURL).toHaveBeenCalledWith(blob);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:finance-report');
    expect(await screen.findByText('Đã tải báo cáo Excel theo bộ lọc hiện tại.')).toBeTruthy();
  });

  it('hiển thị lỗi khi tải Excel thất bại', async () => {
    const user = userEvent.setup();
    vi.mocked(adminFinanceApi.reportExcel).mockRejectedValue({
      status: 403,
      message: 'Forbidden',
    });
    render(
      <MemoryRouter>
        <AdminFinanceReport />
      </MemoryRouter>,
    );
    await screen.findByText('Đối chiếu tiền thu và Credit nạp');

    await user.click(screen.getByRole('button', { name: 'Xuất báo cáo Excel' }));
    await waitFor(() =>
      expect(screen.getByRole('alert').textContent).toContain('không có quyền quản trị'),
    );
  });

  it('báo khoảng ngày sai mà không có nút thử lại và không gọi lại API', async () => {
    // given
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <AdminFinanceReport />
      </MemoryRouter>,
    );
    await screen.findByText('Đối chiếu tiền thu và Credit nạp');
    await user.type(screen.getByLabelText('Từ ngày'), '2026-10-10');
    await user.type(screen.getByLabelText('Đến ngày'), '2026-10-01');

    // when
    await user.click(screen.getByRole('button', { name: 'Lọc' }));

    // then
    expect(screen.getByText('Ngày bắt đầu đang sau ngày kết thúc. Hãy chọn Từ ngày sớm hơn hoặc trùng với Đến ngày.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Thử lại' })).toBeNull();
    expect(adminFinanceApi.report).toHaveBeenCalledTimes(1);
  });
});

it('A11: hiển thị lý do còn lệch khi có dòng nạp thiếu đơn', async () => {
  vi.mocked(adminFinanceApi.report).mockResolvedValue({
    ...summary,
    reconciliation: {
      ...summary.reconciliation,
      matched: false,
      unlinkedEntryCount: 1,
      unlinkedCredits: 20,
      unlinkedEntries: [{ id: 42, userId: 1, amount: 20, createdAt: '2026-11-17T04:00:00Z' }],
    },
  });

  render(<MemoryRouter><AdminFinanceReport /></MemoryRouter>);

  expect(await screen.findByText('Có chênh lệch')).toBeTruthy();
  expect(screen.getByText('Có 1 dòng nạp Credit không gắn với đơn thanh toán, tổng 20 Credit.')).toBeTruthy();
  expect(screen.getByText(/Dòng #42/)).toBeTruthy();
}, 20_000);
