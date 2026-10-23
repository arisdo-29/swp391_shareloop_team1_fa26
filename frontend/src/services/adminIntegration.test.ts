import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminFinanceApi, financeCsvFileName } from './adminFinanceApi';
import { adminReputationApi } from './adminReputationApi';
import { http } from './http';

afterEach(() => vi.restoreAllMocks());

describe('Admin API integrations', () => {
  it('gọi đúng endpoint và body khi cấp sao ân hạn', async () => {
    const response = { userId: 42, trustStars: 1, graceStarUsed: true };
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: response });

    await expect(adminReputationApi.grantGraceStar(42, 'Đã xác minh giải trình')).resolves.toEqual(
      response,
    );
    expect(post).toHaveBeenCalledWith('/api/v1/admin/users/42/grace-star', {
      reason: 'Đã xác minh giải trình',
    });
  });

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

  it('đọc được filename UTF-8 và có tên mặc định an toàn', () => {
    expect(financeCsvFileName("attachment; filename*=UTF-8''b%C3%A1o-c%C3%A1o.csv")).toBe(
      'báo-cáo.csv',
    );
    expect(financeCsvFileName(undefined)).toBe('finance-report.csv');
  });
});
