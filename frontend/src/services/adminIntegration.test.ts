import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminCategoryApi } from './adminCategoryApi';
import { adminFinanceApi, financeCsvFileName } from './adminFinanceApi';
import { adminUserApi } from './adminUserApi';
import { http } from './http';

afterEach(() => vi.restoreAllMocks());

describe('Admin API integrations', () => {
  it('xuất CSV bằng đúng endpoint, bộ lọc, responseType và tên file Backend', async () => {
    const blob = new Blob(['csv'], { type: 'text/csv' });
    const get = vi.spyOn(http, 'get').mockResolvedValue({
      data: blob,
      headers: { 'content-disposition': 'attachment; filename="finance-report_2026-10.csv"' },
    });

    await expect(
      adminFinanceApi.reportCsv({ from: '2026-10-01', to: '2026-10-31' }),
    ).resolves.toEqual({ blob, fileName: 'finance-report_2026-10.csv' });
    expect(get).toHaveBeenCalledWith('/api/v1/admin/finance/report.csv', {
      params: { from: '2026-10-01', to: '2026-10-31' },
      responseType: 'blob',
    });
  });

  it('tra cứu người dùng kèm Credit và ví một người dùng đúng endpoint', async () => {
    const summary = {
      userId: 7,
      fullName: 'Lan An',
      email: 'an@example.com',
      phone: '0900000007',
      availableCredit: 25,
      heldCredit: 5,
      totalCredit: 30,
      totalToppedUpCredit: 50,
    };
    const get = vi
      .spyOn(http, 'get')
      .mockResolvedValueOnce({ data: { items: [summary], page: 0, size: 20, totalElements: 1 } })
      .mockResolvedValueOnce({ data: summary });

    await adminFinanceApi.users({ keyword: 'an', page: 0, size: 20 });
    await expect(adminFinanceApi.userWallet(7)).resolves.toEqual(summary);

    expect(get).toHaveBeenNthCalledWith(1, '/api/v1/admin/finance/users', {
      params: { keyword: 'an', page: 0, size: 20 },
    });
    expect(get).toHaveBeenNthCalledWith(2, '/api/v1/admin/finance/users/7/wallet');
  });

  it('danh sách đơn nạp không ép status và submittedOnly khi không truyền', async () => {
    const get = vi
      .spyOn(http, 'get')
      .mockResolvedValue({ data: { items: [], page: 0, size: 10, totalElements: 0 } });

    await adminFinanceApi.topUpOrders({ page: 0, size: 10 });

    expect(get).toHaveBeenCalledWith('/api/v1/admin/finance/topup-orders', {
      params: { page: 0, size: 10 },
    });
  });

  it('danh sách tài khoản của Admin gửi bộ lọc và phân trang', async () => {
    const get = vi
      .spyOn(http, 'get')
      .mockResolvedValue({ data: { items: [], page: 0, size: 20, totalElements: 0 } });

    await adminUserApi.list({ keyword: 'lan', status: 'LOCKED', role: 'USER', page: 1, size: 20 });

    expect(get).toHaveBeenCalledWith('/api/v1/admin/users', {
      params: { keyword: 'lan', status: 'LOCKED', role: 'USER', page: 1, size: 20 },
    });
  });

  it('sắp xếp lại danh mục gửi toàn bộ id theo thứ tự mới', async () => {
    const put = vi.spyOn(http, 'put').mockResolvedValue({ data: [] });

    await adminCategoryApi.reorder([3, 1, 2]);

    expect(put).toHaveBeenCalledWith('/api/v1/admin/categories/reorder', {
      categoryIds: [3, 1, 2],
    });
  });

  it('đọc được filename UTF-8 và có tên mặc định an toàn', () => {
    expect(financeCsvFileName("attachment; filename*=UTF-8''b%C3%A1o-c%C3%A1o.csv")).toBe(
      'báo-cáo.csv',
    );
    expect(financeCsvFileName(undefined)).toBe('finance-report.csv');
  });
});
