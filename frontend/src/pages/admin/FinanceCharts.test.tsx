// @vitest-environment jsdom

import { cleanup, render, screen, within } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it } from 'vitest';
import { CashByDayChart, CreditByTypeChart } from './FinanceCharts';

// jsdom không có ResizeObserver mà Recharts cần để đo khung biểu đồ.
beforeAll(() => {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
});

afterEach(cleanup);

describe('FinanceCharts', () => {
  it('báo chưa có dữ liệu thay vì vẽ biểu đồ rỗng', () => {
    render(<CashByDayChart days={[]} />);
    expect(screen.getByText('Chưa có dữ liệu trong khoảng đã chọn.')).toBeTruthy();

    cleanup();
    render(<CreditByTypeChart title="Credit cộng theo loại" tone="credit" rows={[]} />);
    expect(screen.getByText('Chưa có dữ liệu trong khoảng đã chọn.')).toBeTruthy();
  });

  it('tiền thu theo ngày có bảng thay thế đủ ngày, tiền và số đơn theo giờ Việt Nam', () => {
    render(
      <CashByDayChart
        days={[
          { date: '2026-10-05', amountVnd: 50000, orderCount: 2 },
          { date: '2026-10-06', amountVnd: 120000, orderCount: 1 },
        ]}
      />,
    );

    expect(screen.getByRole('figure', { name: 'Tiền thu theo ngày' })).toBeTruthy();
    const table = screen.getByText('Xem dạng bảng').closest('details')!.querySelector('table')!;
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows[0].textContent).toContain('05/10/2026');
    expect(rows[0].textContent).toContain('2');
    expect(rows[1].textContent).toContain('06/10/2026');
  });

  it('biểu đồ theo loại có tên gọi để trình đọc màn hình nhận biết', () => {
    render(
      <CreditByTypeChart
        title="Credit trừ theo loại"
        tone="debit"
        rows={[{ label: 'Phí đăng bài', value: 40, detail: '8 bút toán' }]}
      />,
    );

    expect(screen.getByRole('figure', { name: 'Credit trừ theo loại' })).toBeTruthy();
  });
});
