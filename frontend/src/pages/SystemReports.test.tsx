// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { systemReportApi, type SystemReport } from '../services/systemReportApi';
import { SystemReports } from './SystemReports';

vi.mock('../services/systemReportApi', () => ({
  systemReportApi: {
    submit: vi.fn(),
    listMine: vi.fn(),
    detailMine: vi.fn(),
    listAdmin: vi.fn(),
    detailAdmin: vi.fn(),
    resolve: vi.fn(),
  },
}));

const openReport: SystemReport = {
  id: 21,
  title: 'Không mở được tin nhắn',
  description: 'Trang tin nhắn bị trắng khi mở từ trang chủ.',
  status: 'OPEN',
  createdAt: '2026-11-24T08:00:00Z',
  resolution: null,
  resolvedAt: null,
  reporterName: null,
};

const resolvedReport: SystemReport = {
  ...openReport,
  status: 'RESOLVED',
  resolution: 'Đã sửa lỗi mở trang tin nhắn.',
  resolvedAt: '2026-11-24T09:30:00Z',
};

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

function renderReports(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/me/system-reports/:reportId?" element={<SystemReports />} />
        <Route path="/admin/system-reports/:reportId?" element={<SystemReports inAdmin />} />
      </Routes>
    </MemoryRouter>,
  );
}

it('gửi báo cáo rồi mở chi tiết đang chờ xử lý', async () => {
  vi.mocked(systemReportApi.listMine).mockResolvedValue({
    items: [],
    page: 0,
    totalPages: 0,
    totalElements: 0,
  });
  vi.mocked(systemReportApi.submit).mockResolvedValue(openReport);
  vi.mocked(systemReportApi.detailMine).mockResolvedValue(openReport);
  renderReports('/me/system-reports');
  const user = userEvent.setup();

  await user.type(screen.getByLabelText('Tiêu đề'), openReport.title);
  await user.type(screen.getByLabelText(/^Mô tả lỗi/), openReport.description);
  await user.click(screen.getByRole('button', { name: 'Gửi báo cáo' }));

  expect(await screen.findByText('Chờ xử lý')).toBeTruthy();
  expect(systemReportApi.submit).toHaveBeenCalledWith(openReport.title, openReport.description);
  expect(systemReportApi.detailMine).toHaveBeenCalledWith('21');
});

it('admin đọc báo cáo đã gửi rồi ghi kết quả xử lý', async () => {
  const adminReport = { ...openReport, reporterName: 'Nguyễn Minh Tú' };
  vi.mocked(systemReportApi.listAdmin).mockResolvedValue({
    items: [adminReport],
    page: 0,
    totalPages: 1,
    totalElements: 1,
  });
  vi.mocked(systemReportApi.detailAdmin).mockResolvedValue(adminReport);
  vi.mocked(systemReportApi.resolve).mockResolvedValue({
    ...resolvedReport,
    reporterName: adminReport.reporterName,
  });
  renderReports('/admin/system-reports');
  const user = userEvent.setup();

  await user.click(await screen.findByRole('link', { name: /Không mở được tin nhắn/ }));
  await user.type(await screen.findByLabelText(/^Kết quả xử lý/), resolvedReport.resolution!);
  await user.click(screen.getByRole('button', { name: 'Hoàn tất xử lý' }));

  expect(await screen.findByText('Đã xử lý')).toBeTruthy();
  expect(screen.getByText(resolvedReport.resolution!)).toBeTruthy();
  expect(systemReportApi.resolve).toHaveBeenCalledWith(21, resolvedReport.resolution);
});

it('người gửi mở báo cáo từ link thông báo để xem kết quả', async () => {
  vi.mocked(systemReportApi.detailMine).mockResolvedValue(resolvedReport);
  renderReports('/me/system-reports/21');

  expect(await screen.findByText('Đã xử lý')).toBeTruthy();
  expect(screen.getByText(resolvedReport.resolution!)).toBeTruthy();
  expect(screen.queryByRole('button', { name: 'Hoàn tất xử lý' })).toBeNull();
});
