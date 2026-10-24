// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { adminAnomalyApi } from '../../services/adminAnomalyApi';
import { AdminAnomalyContent } from './AdminAnomalyContent';

vi.mock('../../services/adminAnomalyApi', () => ({
  adminAnomalyApi: { getReport: vi.fn() },
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminAnomalyContent', () => {
  it('hiển thị các cặp giao dịch lặp từ Backend', async () => {
    vi.mocked(adminAnomalyApi.getReport).mockResolvedValue({
      generatedAt: '2026-10-22T03:00:00Z',
      pairWindowDays: 90,
      pairThreshold: 5,
      repeatedPairs: [
        {
          userA: { id: 3, fullName: 'Nguyễn Văn An', email: 'an@example.com' },
          userB: { id: 7, fullName: 'Trần Thị Bình', email: 'binh@example.com' },
          completedCount: 6,
          lastCompletedAt: '2026-10-20T08:00:00Z',
        },
      ],
    });

    render(<AdminAnomalyContent />);

    expect(await screen.findByText('Nguyễn Văn An')).toBeTruthy();
    expect(screen.getByText('Trần Thị Bình')).toBeTruthy();
    expect(screen.getByText('6')).toBeTruthy();
    expect(adminAnomalyApi.getReport).toHaveBeenCalledOnce();
  });

  it('hiển thị trạng thái rỗng khi Backend không trả cảnh báo', async () => {
    vi.mocked(adminAnomalyApi.getReport).mockResolvedValue({
      generatedAt: '2026-10-22T03:00:00Z',
      pairWindowDays: 90,
      pairThreshold: 5,
      repeatedPairs: [],
    });

    render(<AdminAnomalyContent />);

    expect(await screen.findByText('Không có cảnh báo bất thường')).toBeTruthy();
  });

  it('hiển thị lỗi phân quyền và cho phép tải lại', async () => {
    const user = userEvent.setup();
    vi.mocked(adminAnomalyApi.getReport)
      .mockRejectedValueOnce({ status: 403 })
      .mockResolvedValueOnce({
        generatedAt: '2026-10-22T03:00:00Z',
        pairWindowDays: 90,
        pairThreshold: 5,
        repeatedPairs: [],
      });

    render(<AdminAnomalyContent />);

    expect(await screen.findByText('Tài khoản hiện tại không có quyền xem cảnh báo bất thường.')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Thử tải lại' }));
    await waitFor(() => expect(adminAnomalyApi.getReport).toHaveBeenCalledTimes(2));
  });
});
