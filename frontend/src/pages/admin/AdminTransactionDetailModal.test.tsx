// @vitest-environment jsdom

import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  adminRequestApi,
  type AdminRequestDetail,
  type AdminRequestRow,
} from '../../services/adminRequestApi';
import { AdminTransactions } from './AdminTransactions';

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
    donor: { userId: 20, name: 'An Chủ Bài', email: 'an@example.com', trustStars: null, rank: null, locked: false },
    receiver: { userId: 10, name: 'Bình Người Nhận', email: null, trustStars: null, rank: null, locked: false },
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

async function openModal() {
  render(
    <MemoryRouter>
      <AdminTransactions />
    </MemoryRouter>,
  );
  await userEvent.click(await screen.findByRole('button', { name: 'Xem chi tiết giao dịch #5' }));
  const dialog = await screen.findByRole('dialog', { name: 'Chi tiết giao dịch #5' });
  await within(dialog).findByText('Thông tin giao dịch');
  return dialog;
}

const rowOf = (dialog: HTMLElement, label: string) =>
  within(dialog).getByText(label).closest<HTMLElement>('.admin-tx-modal-row')!;

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

describe('Hộp thoại chi tiết giao dịch', () => {
  it('giao nhận trực tiếp: hiện lịch, xác nhận từng bên từ dòng thời gian, không hiện phần gửi hàng', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(detail());
    const dialog = await openModal();

    expect(within(dialog).getByText('#5')).toBeTruthy();
    expect(within(dialog).getByText('Cổng trường ĐH FPT')).toBeTruthy();
    expect(within(rowOf(dialog, 'Chủ bài xác nhận lịch')).getByText('Đã đồng ý')).toBeTruthy();
    expect(within(rowOf(dialog, 'Người nhận xác nhận lịch')).getByText('Chưa xác nhận')).toBeTruthy();
    // Email người nhận trống thì báo thiếu, không tự điền.
    expect(within(dialog).getAllByText('Chưa có thông tin').length).toBeGreaterThan(0);
    expect(within(dialog).queryByText('Thông tin gửi hàng')).toBeNull();
    expect(within(dialog).getByText('Lịch giao nhận')).toBeTruthy();
    expect(within(dialog).getByText('Mới nhất')).toBeTruthy();
    // Nút huỷ ở danh sách vẫn giữ nguyên.
    expect(screen.getByRole('button', { name: 'Huỷ giao dịch' })).toBeTruthy();
  });

  it('gửi hàng: chỉ hiện địa chỉ, hạn gửi, trạng thái và mốc xác nhận, không hiện thông tin bên vận chuyển', async () => {
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
          { label: 'Chủ bài gửi hàng (chiều 1)', at: '2026-10-26T09:00:00Z', note: 'GHN123456' },
        ],
        deadlines: { ...detail().deadlines!, sendDeadlineAt: '2026-10-27T09:00:00Z' },
      }),
    );
    const dialog = await openModal();

    expect(within(dialog).getByText('Thông tin gửi hàng')).toBeTruthy();
    expect(within(dialog).queryByText('Lịch giao nhận')).toBeNull();
    expect(within(dialog).getByText('12 Lê Lợi, Quận 1')).toBeTruthy();
    expect(within(dialog).getByText('Đã gửi')).toBeTruthy();
    expect(within(rowOf(dialog, 'Hạn gửi hàng')).queryByText('Chưa có thông tin')).toBeNull();
    expect(within(rowOf(dialog, 'Chủ bài xác nhận đã gửi')).queryByText('Chưa có thông tin')).toBeNull();
    expect(within(rowOf(dialog, 'Người nhận xác nhận đã nhận')).getByText('Chưa có thông tin')).toBeTruthy();
    // Không hiện thông tin của đơn vị vận chuyển, kể cả mã vận đơn trong dòng thời gian.
    expect(within(dialog).queryByText(/Đơn vị vận chuyển|Mã vận đơn/)).toBeNull();
    expect(within(dialog).queryByText('GHN123456')).toBeNull();
    expect(within(dialog).queryByText('Minh chứng')).toBeNull();
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
    const dialog = await openModal();

    expect(
      within(rowOf(dialog, 'Lý do huỷ')).getByText('Do Admin huỷ: Một bên bị khoá tài khoản'),
    ).toBeTruthy();
    expect(within(rowOf(dialog, 'Trạng thái hiện tại')).getByText('Đã hủy')).toBeTruthy();
  });

  it('đóng hộp thoại bằng nút Đóng', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(detail());
    const dialog = await openModal();
    const closeButtons = within(dialog).getAllByRole('button', { name: 'Đóng' });
    await userEvent.click(closeButtons[closeButtons.length - 1]);
    expect(screen.queryByRole('dialog', { name: 'Chi tiết giao dịch #5' })).toBeNull();
  });
});
