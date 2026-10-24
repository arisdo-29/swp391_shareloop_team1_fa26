// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminAnomalyApi, type AnomalyReportResponse } from '../../services/adminAnomalyApi';
import { AdminAnomalies } from './AdminAnomalies';

vi.mock('../../services/adminAnomalyApi', () => ({
  adminAnomalyApi: { report: vi.fn() },
}));

const reportWithPair: AnomalyReportResponse = {
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
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminAnomalies', () => {
  it('hiện cặp giao dịch lặp kèm tên, email và ngưỡng, không hiện id', async () => {
    vi.mocked(adminAnomalyApi.report).mockResolvedValue(reportWithPair);

    render(<AdminAnomalies />);

    const row = (await screen.findByText('Nguyễn Văn An')).closest('tr')!;
    expect(within(row).getByText('an@fpt.edu.vn')).toBeTruthy();
    expect(within(row).getByText('Trần Thị Bình')).toBeTruthy();
    expect(within(row).getByText('Không có email')).toBeTruthy();
    expect(within(row).getByText('6')).toBeTruthy();
    expect(screen.getByText(/hơn 5 giao dịch hoàn tất với nhau trong 90 ngày/)).toBeTruthy();
    expect(screen.queryByText(/#3|#7/)).toBeNull();
  });

  it('hiện trạng thái rỗng khi chưa có cặp nào vượt ngưỡng', async () => {
    vi.mocked(adminAnomalyApi.report).mockResolvedValue({ ...reportWithPair, repeatedPairs: [] });

    render(<AdminAnomalies />);

    expect(await screen.findByText('Chưa có cảnh báo nào')).toBeTruthy();
  });

  it('hiện lỗi khi không tải được và cho làm mới', async () => {
    const user = userEvent.setup();
    vi.mocked(adminAnomalyApi.report)
      .mockRejectedValueOnce({ status: 500, code: 'INTERNAL_ERROR', message: 'Máy chủ đang bận.' })
      .mockResolvedValueOnce(reportWithPair);

    render(<AdminAnomalies />);
    expect(await screen.findByText('Máy chủ đang bận.')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Làm mới' }));

    expect(await screen.findByText('Nguyễn Văn An')).toBeTruthy();
    await waitFor(() => expect(adminAnomalyApi.report).toHaveBeenCalledTimes(2));
  });
});
