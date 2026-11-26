// @vitest-environment jsdom

import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  adminRequestApi,
  type AdminRequestDetail,
  type AdminRequestRow,
} from '../../services/adminRequestApi';
import { AdminTransactions } from './AdminTransactions';
import { AdminTransactionDetail } from './AdminTransactionDetail';

vi.mock('../../services/adminRequestApi', () => ({
  adminRequestApi: { list: vi.fn(), detail: vi.fn(), cancel: vi.fn() },
}));

const baseRow: AdminRequestRow = {
  id: 5,
  itemId: 1,
  itemTitle: 'Xe đạp cũ',
  type: 'GIVE',
  status: 'AWAITING_HANDOVER',
  donorId: 20,
  donorName: 'An Chủ Bài',
  receiverId: 10,
  receiverName: 'Bình Người Nhận',
  meetingTime: '2026-10-25T09:00:00Z',
  createdAt: '2026-10-20T08:00:00Z',
  flagReason: null,
  deliveryMethod: 'IN_PERSON',
};

function detail(overrides: Partial<AdminRequestDetail> = {}): AdminRequestDetail {
  return {
    id: 5,
    type: 'GIVE',
    status: 'AWAITING_HANDOVER',
    donor: {
      userId: 20,
      name: 'An Chủ Bài',
      email: 'an@example.com',
      trustStars: null,
      rank: null,
      locked: false,
    },
    receiver: {
      userId: 10,
      name: 'Bình Người Nhận',
      email: null,
      trustStars: null,
      rank: null,
      locked: false,
    },
    item: { itemId: 1, title: 'Xe đạp cũ', imageUrl: null, status: 'RESERVED', offerType: 'GIVE' },
    offeredItem: null,
    deliveryMethod: 'IN_PERSON',
    meetingPlace: 'Cổng trường ĐH FPT',
    meetingTime: '2026-10-25T09:00:00Z',
    timeline: [
      { label: 'Người nhận gửi yêu cầu', at: '2026-10-20T08:00:00Z', note: null },
      { label: 'Chủ bài đồng ý lịch hẹn', at: '2026-10-21T08:00:00Z', note: null },
    ],
    photos: [],
    reports: [],
    points: [],
    messagesViewable: false,
    cancellable: true,
    createdAt: '2026-10-20T08:00:00Z',
    deadlines: {
      autoCancelAt: null,
      autoConfirmAt: '2026-10-28T09:00:00Z',
      cancelRequestExpiresAt: null,
      rescheduleExpiresAt: null,
      sendDeadlineAt: null,
      leg1AutoReceiveAt: null,
      leg2AutoReceiveAt: null,
    },
    shippingLegs: [],
    ...overrides,
  };
}

async function openDetail(search = '') {
  render(
    <MemoryRouter initialEntries={[`/admin/transactions${search}`]}>
      <Routes>
        <Route path="/admin/transactions" element={<AdminTransactions />} />
        <Route path="/admin/transactions/:transactionId" element={<AdminTransactionDetail />} />
      </Routes>
    </MemoryRouter>,
  );
  await userEvent.click(await screen.findByRole('link', { name: 'Xem giao dịch Xe đạp cũ' }));
  await screen.findByRole('heading', { level: 1, name: 'Xe đạp cũ' });
}

const rowOf = (label: string) =>
  screen.getByText(label).closest<HTMLElement>('.admin-transaction-detail-row')!;

beforeEach(() => {
  vi.mocked(adminRequestApi.list).mockResolvedValue({
    items: [baseRow],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  });
  vi.mocked(adminRequestApi.cancel).mockResolvedValue({} as never);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('Mở chi tiết từ danh sách giao dịch', () => {
  it('giao nhận trực tiếp: hiện lịch, xác nhận từng bên từ dòng thời gian, không hiện phần gửi hàng', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(
      detail({
        status: 'AWAITING_LOGISTICS',
        deadlines: {
          ...detail().deadlines!,
          autoCancelAt: '2026-10-24T09:00:00Z',
          autoConfirmAt: null,
        },
      }),
    );
    await openDetail();

    expect(within(rowOf('Giờ hẹn đề xuất')).getByText(/16:00.*25\/10\/2026/)).toBeTruthy();
    expect(screen.getByText('Cổng trường ĐH FPT')).toBeTruthy();
    expect(within(rowOf('Chủ bài xác nhận lịch')).getByText('Đã đồng ý')).toBeTruthy();
    expect(within(rowOf('Người nhận xác nhận lịch')).getByText('Chưa xác nhận')).toBeTruthy();
    expect(within(rowOf('Hạn xác nhận lịch')).getByText(/16:00.*24\/10\/2026/)).toBeTruthy();
    expect(screen.queryByText('Thông tin gửi hàng')).toBeNull();
    expect(screen.getByText('Lịch giao nhận')).toBeTruthy();
    expect(screen.getByText('Dòng thời gian')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Huỷ giao dịch' })).toBeTruthy();
  });

  it('gửi hàng: hiện hai xác nhận lịch, hạn gửi và mốc tự ghi nhận của đúng chiều', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(
      detail({
        deliveryMethod: 'SHIPPING',
        shippingLegs: [
          {
            leg: 1,
            itemTitle: 'Xe đạp cũ',
            senderRole: 'Chủ bài',
            receiverRole: 'Người nhận',
            address: '12 Lê Lợi, Quận 1',
            shippedAt: '2026-10-26T09:00:00Z',
            trackingCode: 'GHN123456',
            receivedAt: null,
            step: 'Đã gửi',
          },
        ],
        timeline: [
          { label: 'Người nhận gửi yêu cầu', at: '2026-10-20T08:00:00Z', note: null },
          { label: 'Chủ bài đồng ý lịch hẹn', at: '2026-10-21T08:00:00Z', note: null },
          { label: 'Người nhận đồng ý lịch hẹn', at: '2026-10-22T09:00:00Z', note: null },
          { label: 'Chủ bài gửi hàng (chiều 1)', at: '2026-10-26T09:00:00Z', note: 'GHN123456' },
        ],
        deadlines: {
          ...detail().deadlines!,
          sendDeadlineAt: '2026-10-27T09:00:00Z',
          leg1AutoReceiveAt: '2026-10-29T09:00:00Z',
        },
      }),
    );
    await openDetail();

    expect(screen.getByText('Thông tin gửi hàng')).toBeTruthy();
    expect(screen.getByText('Lịch gửi hàng')).toBeTruthy();
    expect(screen.queryByText('Lịch giao nhận')).toBeNull();
    expect(within(rowOf('Chủ bài xác nhận lịch')).getByText(/15:00.*21\/10\/2026/)).toBeTruthy();
    expect(within(rowOf('Người nhận xác nhận lịch')).getByText(/16:00.*22\/10\/2026/)).toBeTruthy();
    expect(screen.getByText('12 Lê Lợi, Quận 1')).toBeTruthy();
    expect(screen.getByText('Đã gửi')).toBeTruthy();
    expect(within(rowOf('Hạn gửi hàng')).getByText(/16:00.*27\/10\/2026/)).toBeTruthy();
    expect(within(rowOf('Chủ bài xác nhận đã gửi')).queryByText('Chưa có thông tin')).toBeNull();
    expect(within(rowOf('Thời điểm ghi nhận đã nhận')).getByText('Chưa có thông tin')).toBeTruthy();
    const leg = within(screen.getByTestId('admin-leg-1'));
    expect(leg.getByText('Tự ghi nhận đã nhận lúc')).toBeTruthy();
    expect(leg.getByText(/16:00.*29\/10\/2026/)).toBeTruthy();
    expect(within(rowOf('Mã vận đơn')).getByText('GHN123456')).toBeTruthy();
    expect(screen.queryByText(/Mở.*chi tiết đầy đủ|Thu gọn chi tiết/)).toBeNull();
  });

  it('giao dịch đã huỷ hiện lý do huỷ lấy từ mốc huỷ', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(
      detail({
        status: 'CANCELLED',
        cancellable: false,
        timeline: [
          { label: 'Người nhận gửi yêu cầu', at: '2026-10-20T08:00:00Z', note: null },
          { label: 'Do Admin huỷ', at: '2026-10-22T08:00:00Z', note: 'Một bên bị khoá tài khoản' },
        ],
      }),
    );
    await openDetail();

    expect(screen.getByText('Do Admin huỷ')).toBeTruthy();
    expect(screen.getByText('Lý do: Một bên bị khoá tài khoản')).toBeTruthy();
    expect(screen.getByText(/Cho tặng\. Đã hủy/)).toBeTruthy();
  });

  it('giữ bộ lọc khi trở về danh sách', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(detail());
    await openDetail('?deliveryMethod=SHIPPING&page=1');
    const back = screen.getByRole('link', { name: 'Về danh sách' });
    expect(back.getAttribute('href')).toBe('/admin/transactions?deliveryMethod=SHIPPING&page=1');
    await userEvent.click(back);
    expect(await screen.findByRole('heading', { level: 1, name: 'Giao dịch' })).toBeTruthy();
    expect((screen.getByLabelText('Hình thức giao nhận') as HTMLSelectElement).value).toBe(
      'SHIPPING',
    );
  });
});
