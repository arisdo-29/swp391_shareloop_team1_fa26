// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { notificationApi, type NotificationResponse } from '../../services/notificationApi';
import { Notifications } from './Notifications';

vi.mock('../../services/notificationApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../services/notificationApi')>();
  return {
    ...original,
    notificationApi: {
      listAll: vi.fn(),
      markRead: vi.fn(),
      markManyRead: vi.fn(),
      deleteMultiple: vi.fn(),
      clearRead: vi.fn(),
    },
  };
});
vi.mock('../../components/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

function notification(overrides: Partial<NotificationResponse>): NotificationResponse {
  return {
    id: 1,
    type: 'NEW_REQUEST',
    title: 'Có yêu cầu mới',
    body: 'Bình vừa gửi yêu cầu.',
    link: '/activities',
    isRead: false,
    createdAt: '2026-11-01T08:00:00Z',
    ...overrides,
  };
}

const entries = [
  notification({ id: 1, type: 'POST_APPROVED', title: 'Bài đăng đã được duyệt', isRead: true }),
  notification({ id: 2, type: 'DISPUTE_OPENED', title: 'Giao dịch bị khiếu nại' }),
  notification({
    id: 3,
    type: 'TRUST_STARS_DEDUCTED',
    title: 'Bạn bị trừ sao uy tín',
    isRead: true,
  }),
];

function renderPage() {
  render(
    <MemoryRouter>
      <Notifications />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.mocked(notificationApi.listAll).mockResolvedValue(entries);
});

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

describe('Notifications', () => {
  it('lọc theo loại Khiếu nại & Giải trình thì chỉ còn thông báo khiếu nại', async () => {
    // given
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Bài đăng đã được duyệt');

    // when
    await user.selectOptions(screen.getByLabelText('Lọc theo loại'), 'complaint');

    // then
    expect(screen.getByText('Giao dịch bị khiếu nại')).toBeTruthy();
    expect(screen.queryByText('Bài đăng đã được duyệt')).toBeNull();
    expect(screen.queryByText('Bạn bị trừ sao uy tín')).toBeNull();
  });

  it('lọc Chưa đọc thì ẩn thông báo đã đọc', async () => {
    // given
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Bài đăng đã được duyệt');

    // when
    await user.click(screen.getByRole('button', { name: 'Chưa đọc' }));

    // then
    expect(screen.getByText('Giao dịch bị khiếu nại')).toBeTruthy();
    expect(screen.queryByText('Bài đăng đã được duyệt')).toBeNull();
  });

  it('xoá các dòng đã chọn thì gọi API và bỏ khỏi danh sách', async () => {
    // given
    const user = userEvent.setup();
    vi.mocked(notificationApi.deleteMultiple).mockResolvedValue(undefined);
    renderPage();
    await user.click(await screen.findByLabelText('Chọn thông báo Giao dịch bị khiếu nại'));

    // when
    await user.click(screen.getByRole('button', { name: 'Xóa đã chọn' }));

    // then
    await waitFor(() => expect(screen.queryByText('Giao dịch bị khiếu nại')).toBeNull());
    expect(notificationApi.deleteMultiple).toHaveBeenCalledWith([2]);
  });

  it('dọn sạch thông báo đã đọc thì chỉ giữ thông báo chưa đọc', async () => {
    // given
    const user = userEvent.setup();
    vi.mocked(notificationApi.clearRead).mockResolvedValue(2);
    renderPage();
    await screen.findByText('Bài đăng đã được duyệt');

    // when
    await user.click(screen.getByRole('button', { name: 'Dọn sạch thông báo đã đọc' }));

    // then
    await waitFor(() => expect(screen.queryByText('Bài đăng đã được duyệt')).toBeNull());
    expect(screen.getByText('Giao dịch bị khiếu nại')).toBeTruthy();
  });
});
