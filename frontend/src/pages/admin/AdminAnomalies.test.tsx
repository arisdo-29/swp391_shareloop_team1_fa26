// @vitest-environment jsdom

import { cleanup, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminAnomalyApi, type AnomalyReportResponse } from '../../services/adminAnomalyApi';
import { AdminAnomalies } from './AdminAnomalies';

vi.mock('../../services/adminAnomalyApi', () => ({
  adminAnomalyApi: { report: vi.fn() },
}));

const reportWithData: AnomalyReportResponse = {
  generatedAt: '2026-10-22T03:00:00Z',
  pairWindowDays: 90,
  pairThreshold: 5,
  repeatedPairs: [
    {
      userA: { id: 3, fullName: 'Nguyễn Văn An', email: 'an@fpt.edu.vn' },
      userB: { id: 7, fullName: 'Trần Thị Bình', email: null },
      completedCount: 6,
      lastCompletedAt: '2026-10-20T08:00:00Z',
    },
  ],
  ipWindowDays: 30,
  ipMinAccounts: 3,
  sharedIpGroups: [
    {
      ipAddress: '192.168.1.100',
      accountCount: 3,
      accounts: [
        {
          id: 10,
          fullName: 'Lê Văn Cường',
          email: 'cuong@fpt.edu.vn',
          registeredAt: '2026-10-21T08:00:00Z',
          trustStars: 5,
          status: 'ACTIVE',
          locked: false,
        },
        {
          id: 11,
          fullName: 'Lê Văn Dũng',
          email: 'dung@fpt.edu.vn',
          registeredAt: '2026-10-21T09:00:00Z',
          trustStars: 4,
          status: 'ACTIVE',
          locked: true,
        },
        {
          id: 12,
          fullName: 'Lê Văn Em',
          email: null,
          registeredAt: '2026-10-21T10:00:00Z',
          trustStars: 1,
          status: 'BANNED',
          locked: false,
        },
      ],
    },
  ],
};

function renderComponent() {
  return render(
    <MemoryRouter>
      <AdminAnomalies />
    </MemoryRouter>
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminAnomalies', () => {
  it('hiện cặp giao dịch lặp kèm tên, email và ngưỡng, không hiện id', async () => {
    vi.mocked(adminAnomalyApi.report).mockResolvedValue(reportWithData);

    renderComponent();

    const row = (await screen.findByText('Nguyễn Văn An')).closest('tr')!;
    expect(within(row).getByText('an@fpt.edu.vn')).toBeTruthy();
    expect(within(row).getByText('Trần Thị Bình')).toBeTruthy();
    expect(within(row).getByText('Không có email')).toBeTruthy();
    expect(within(row).getByText('6')).toBeTruthy();
    expect(screen.queryByText(/hơn 5 giao dịch hoàn tất với nhau trong 90 ngày/)).toBeNull();
    expect(screen.queryByText(/#3|#7/)).toBeNull();
  });

  it('hiện danh sách cảnh báo nhiều tài khoản cùng địa chỉ IP kèm link chi tiết', async () => {
    vi.mocked(adminAnomalyApi.report).mockResolvedValue(reportWithData);

    renderComponent();

    expect(await screen.findByText('IP: 192.168.1.100')).toBeTruthy();
    expect(screen.getByText('3 tài khoản')).toBeTruthy();
    expect(screen.getByText('IP có từ 3 tài khoản đăng ký trong 30 ngày')).toBeTruthy();

    const cuongLink = screen.getByRole('link', { name: 'Lê Văn Cường' });
    expect(cuongLink.getAttribute('href')).toBe('/admin/finance/users/10');
    expect(screen.getByText('cuong@fpt.edu.vn')).toBeTruthy();
    expect(screen.getByText('5 ⭐')).toBeTruthy();
    expect(screen.getByText('Đang hoạt động')).toBeTruthy();

    const dungLink = screen.getByRole('link', { name: 'Lê Văn Dũng' });
    expect(dungLink.getAttribute('href')).toBe('/admin/finance/users/11');
    expect(screen.getByText('Đã khóa')).toBeTruthy();

    const emLink = screen.getByRole('link', { name: 'Lê Văn Em' });
    expect(emLink.getAttribute('href')).toBe('/admin/finance/users/12');
    expect(screen.getByText('Bị cấm')).toBeTruthy();
  });

  it('hiện trạng thái rỗng khi chưa có cặp nào vượt ngưỡng và không có nhóm IP nào', async () => {
    vi.mocked(adminAnomalyApi.report).mockResolvedValue({
      ...reportWithData,
      repeatedPairs: [],
      sharedIpGroups: [],
    });

    renderComponent();

    expect(await screen.findByText('Chưa có cảnh báo nào')).toBeTruthy();
    expect(screen.getByText('Không có cảnh báo')).toBeTruthy();
  });

  it('hiện lỗi khi không tải được và không hiện nút làm mới', async () => {
    vi.mocked(adminAnomalyApi.report).mockRejectedValue({
      status: 500,
      code: 'INTERNAL_ERROR',
      message: 'Máy chủ đang bận.',
    });

    renderComponent();
    expect(await screen.findByText('Máy chủ đang bận.')).toBeTruthy();

    expect(screen.queryByRole('button', { name: 'Làm mới' })).toBeNull();
  });
});
