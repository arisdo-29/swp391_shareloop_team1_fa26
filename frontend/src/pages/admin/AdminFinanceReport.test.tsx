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
    reportCsv: vi.fn(),
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

describe('AdminFinanceReport CSV', () => {
  it('xuất file bằng đúng bộ lọc đã áp dụng và thông báo thành công', async () => {
    const user = userEvent.setup();
    const blob = new Blob(['csv'], { type: 'text/csv' });
    vi.mocked(adminFinanceApi.reportCsv).mockResolvedValue({
      blob,
      fileName: 'finance-report.csv',
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

    await user.type(screen.getByLabelText('Từ ngày'), '2026-10-01');
    await user.type(screen.getByLabelText('Đến ngày'), '2026-10-31');
    await user.click(screen.getByRole('button', { name: 'Lọc' }));
    await waitFor(() =>
      expect(adminFinanceApi.report).toHaveBeenLastCalledWith({
        from: '2026-10-01',
        to: '2026-10-31',
      }),
    );

    await user.click(screen.getByRole('button', { name: 'Xuất báo cáo CSV' }));
    await waitFor(() =>
      expect(adminFinanceApi.reportCsv).toHaveBeenCalledWith({
        from: '2026-10-01',
        to: '2026-10-31',
      }),
    );
    expect(createObjectURL).toHaveBeenCalledWith(blob);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:finance-report');
    expect(await screen.findByText('Đã tải báo cáo CSV theo bộ lọc hiện tại.')).toBeTruthy();
  });

  it('hiển thị lỗi khi tải CSV thất bại', async () => {
    const user = userEvent.setup();
    vi.mocked(adminFinanceApi.reportCsv).mockRejectedValue({
      status: 403,
      message: 'Forbidden',
    });
    render(
      <MemoryRouter>
        <AdminFinanceReport />
      </MemoryRouter>,
    );
    await screen.findByText('Đối chiếu tiền thu và Credit nạp');

    await user.click(screen.getByRole('button', { name: 'Xuất báo cáo CSV' }));
    await waitFor(() =>
      expect(screen.getByRole('alert').textContent).toContain('không có quyền quản trị'),
    );
  });
});
