// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, expect, it, vi } from 'vitest';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import { Transactions } from './Transactions';

vi.mock('../../app/hooks', () => ({ useAppSelector: () => ({ id: 'api_12' }) }));
vi.mock('../../utils/useRealtimeRefresh', () => ({ useRealtimeRefresh: () => undefined }));
vi.mock('../../services/requestApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/requestApi')>()),
  requestApi: { mine: vi.fn(), received: vi.fn() },
}));

function request(id: number, overrides: Partial<RequestResponse>): RequestResponse {
  return {
    id,
    itemId: id + 10,
    receiverId: 12,
    type: 'GIVE',
    status: 'AWAITING_LOGISTICS',
    createdAt: '2026-11-24T08:00:00Z',
    ...overrides,
  };
}

function renderTransactions(path = '/transactions') {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Transactions />
    </MemoryRouter>,
  );
}

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

it('lọc theo trạng thái, tìm không dấu và chuyển sang chi tiết với bộ lọc hiện tại', async () => {
  vi.mocked(requestApi.mine).mockResolvedValue([
    request(101, { itemTitle: 'Đèn bàn', donorName: 'Nguyễn Minh Tú' }),
    request(102, { itemTitle: 'Kệ sách', status: 'COMPLETED' }),
    request(103, { itemTitle: 'Bàn gỗ', status: 'CANCELLED' }),
  ]);
  vi.mocked(requestApi.received).mockResolvedValue([]);
  const user = userEvent.setup();
  renderTransactions('/transactions?status=completed');

  expect(await screen.findByRole('link', { name: /Kệ sách/ })).toBeTruthy();
  expect(screen.getByRole('button', { name: 'Hoàn tất', pressed: true })).toBeTruthy();
  await user.click(screen.getByRole('button', { name: 'Đã hủy' }));
  expect(screen.getAllByRole('link')).toHaveLength(1);
  expect(screen.getByRole('link', { name: /Bàn gỗ/ })).toBeTruthy();
  await user.click(screen.getByRole('button', { name: 'Đang diễn ra' }));

  const search = screen.getByRole('searchbox', { name: 'Tìm theo tên món hoặc người giao dịch' });
  await user.type(search, 'nguyen minh tu');
  const match = screen.getByRole('link', { name: /Đèn bàn/ });
  expect(match.getAttribute('href')).toBe('/transactions/101?status=active&q=nguyen+minh+tu');
  await user.clear(search);
  await user.type(search, 'den ban');
  expect(screen.getAllByRole('link')).toHaveLength(1);
  await user.click(screen.getByRole('button', { name: 'Hoàn tất' }));
  expect(screen.getByRole('heading', { name: 'Không có giao dịch phù hợp' })).toBeTruthy();
  await user.clear(search);
  await user.click(screen.getByRole('button', { name: 'Tất cả' }));
  expect(screen.getAllByRole('link')).toHaveLength(3);
});

it('hiện giao dịch từ hai phía, ưu tiên giao dịch đang xử lý và mở đúng chi tiết', async () => {
  const active = request(101, { itemTitle: 'Đèn bàn', donorName: 'An' });
  vi.mocked(requestApi.mine).mockResolvedValue([
    active,
    request(102, { itemTitle: 'Kệ sách', status: 'COMPLETED', createdAt: '2026-11-25T08:00:00Z' }),
    request(103, { itemTitle: 'Bàn gỗ', status: 'PENDING' }),
  ]);
  vi.mocked(requestApi.received).mockResolvedValue([
    request(104, {
      itemTitle: 'Áo khoác',
      receiverId: 28,
      receiverName: 'Bình',
      status: 'AWAITING_HANDOVER',
    }),
  ]);
  renderTransactions();

  const links = await screen.findAllByRole('link');
  expect(links).toHaveLength(3);
  expect(links[2].getAttribute('href')).toBe('/transactions/102');
  expect(screen.getByRole('link', { name: /Đèn bàn An.*Chờ chốt lịch/ }).getAttribute('href')).toBe(
    '/transactions/101',
  );
  expect(
    screen.getByRole('link', { name: /Áo khoác Bình.*Chờ giao nhận/ }).getAttribute('href'),
  ).toBe('/transactions/104');
  expect(requestApi.mine).toHaveBeenCalledWith(12);
  expect(requestApi.received).toHaveBeenCalledWith(12);
});

it('cho thử tải lại khi lỗi và hiển thị danh sách trống nếu chưa có giao dịch', async () => {
  vi.mocked(requestApi.mine)
    .mockRejectedValueOnce(new Error('Network error'))
    .mockResolvedValue([]);
  vi.mocked(requestApi.received).mockResolvedValue([]);
  renderTransactions();

  await userEvent.click(await screen.findByRole('button', { name: 'Thử tải lại' }));
  expect(await screen.findByRole('heading', { name: 'Chưa có giao dịch' })).toBeTruthy();
});
