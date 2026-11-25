// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { CashByDayChart, CreditByTypeChart } from './FinanceCharts';

// jsdom không có ResizeObserver mà Recharts cần để đo khung biểu đồ.
beforeAll(() => {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

function LocationProbe() {
  const location = useLocation();
  return <output>{location.pathname + location.search}</output>;
}

describe('FinanceCharts', () => {
  it('báo chưa có dữ liệu thay vì vẽ biểu đồ rỗng', () => {
    render(<CashByDayChart days={[]} />);
    expect(screen.getByText('Chưa có dữ liệu trong khoảng đã chọn.')).toBeTruthy();

    cleanup();
    render(<CreditByTypeChart title="Credit cộng theo loại" rows={[]} />);
    expect(screen.getByText('Chưa có dữ liệu trong khoảng đã chọn.')).toBeTruthy();
  });

  it('mỗi cột và mỗi dòng bảng mở đúng ngày thanh toán', async () => {
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
      width: 640,
      height: 260,
      x: 0,
      y: 0,
      top: 0,
      right: 640,
      bottom: 260,
      left: 0,
      toJSON: () => ({}),
    });
    render(
      <MemoryRouter>
        <CashByDayChart
          days={[
            { date: '2026-10-05', amountVnd: 50000, orderCount: 2 },
            { date: '2026-10-06', amountVnd: 120000, orderCount: 1 },
          ]}
        />
        <LocationProbe />
      </MemoryRouter>,
    );

    expect(screen.getByRole('figure', { name: 'Tiền thu theo ngày' })).toBeTruthy();
    const table = screen.getByText('Xem dạng bảng').closest('details')!.querySelector('table')!;
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows[0].textContent).toContain('05/10/2026');
    expect(rows[0].textContent).toContain('2');
    expect(rows[1].textContent).toContain('06/10/2026');
    expect(rows[0].querySelector('a')?.getAttribute('href')).toBe(
      '/admin/finance/topup-orders?from=2026-10-05&to=2026-10-05&paidDate=true&status=SUCCESS',
    );
    fireEvent.click(await screen.findByLabelText('Xem giao dịch nạp tiền ngày 06/10/2026'));
    expect(screen.getByRole('status').textContent).toBe(
      '/admin/finance/topup-orders?from=2026-10-06&to=2026-10-06&paidDate=true&status=SUCCESS',
    );
  });

  it('biểu đồ theo loại có tên gọi để trình đọc màn hình nhận biết', () => {
    render(
      <CreditByTypeChart
        title="Credit trừ theo loại"
        rows={[
          { label: 'Phí đăng bài', value: -40, detail: '8 lượt' },
          { label: 'Phí gia hạn bài', value: -10, detail: '2 lượt' },
        ]}
      />,
    );

    expect(screen.getByRole('figure', { name: 'Credit trừ theo loại' })).toBeTruthy();
    expect(screen.getByText('40 Credit · 80%')).toBeTruthy();
    expect(screen.getByText('10 Credit · 20%')).toBeTruthy();
  });
});
