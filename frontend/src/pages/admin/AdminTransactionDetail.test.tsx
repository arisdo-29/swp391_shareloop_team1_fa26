// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminRequestApi, type AdminRequestDetail } from '../../services/adminRequestApi';
import { AdminTransactionDetail } from './AdminTransactionDetail';

// Ảnh riêng tư tải bằng token: ở test này chỉ cần hook trả blob URL có sẵn.
vi.mock('../../utils/usePrivateMediaUrl', () => ({
  usePrivateMediaUrl: () => ({ url: 'blob:test-image', loading: false, failed: false }),
}));

vi.mock('../../services/adminRequestApi', () => ({
  adminRequestApi: { detail: vi.fn(), messages: vi.fn(), cancel: vi.fn(), list: vi.fn() },
}));

function detail(overrides: Partial<AdminRequestDetail> = {}): AdminRequestDetail {
  return {
    id: 5,
    type: 'SWAP',
    status: 'COMPLETED',
    donor: { userId: 20, name: 'An Chủ Bài', email: 'an@example.com', trustStars: 5, rank: 'SILVER', locked: false },
    receiver: { userId: 10, name: 'Bình Người Nhận', email: 'binh@example.com', trustStars: 4, rank: 'BRONZE', locked: true },
    item: { itemId: 1, title: 'Xe đạp cũ', imageUrl: null, status: 'TRADED', offerType: 'SWAP' },
    offeredItem: { itemId: 2, title: 'Ghế xoay', imageUrl: null, status: 'TRADED', offerType: 'SWAP' },
    deliveryMethod: 'IN_PERSON',
    meetingPlace: 'Cổng trường',
    meetingTime: '2026-10-25T09:00:00Z',
    timeline: [
      { label: 'Người nhận gửi yêu cầu', at: '2026-10-20T08:00:00Z', note: null },
      { label: 'Chủ bài chọn người nhận', at: '2026-10-21T08:00:00Z', note: null },
      { label: 'Giao dịch hoàn tất', at: '2026-10-25T10:00:00Z', note: null },
    ],
    photos: [{ mediaId: 3, url: '/api/v1/media/3', stage: 'HANDOVER', uploaderName: 'An Chủ Bài', createdAt: '2026-10-25T09:30:00Z' }],
    reports: [],
    points: [{ userName: 'An Chủ Bài', role: 'DONOR', points: 10 }],
    messagesViewable: false,
    cancellable: false,
    createdAt: '2026-10-20T08:00:00Z',
    ...overrides,
  };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/transactions/5']}>
      <Routes>
        <Route path="/admin/transactions/:transactionId" element={<AdminTransactionDetail />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.mocked(adminRequestApi.detail).mockResolvedValue(detail());
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('AdminTransactionDetail', () => {
  it('hiện hai bên, hai món, giao nhận, dòng thời gian, ảnh và điểm bằng tiếng Việt', async () => {
    renderPage();

    await screen.findByText('Hai bên');
    expect(adminRequestApi.detail).toHaveBeenCalledWith(5);
    expect(screen.getByText('An Chủ Bài', { selector: 'p.font-semibold' })).toBeTruthy();
    expect(screen.getByText('Tài khoản đang bị khoá')).toBeTruthy();
    expect(screen.getByText('Ghế xoay')).toBeTruthy();
    expect(screen.getByText('Gặp trực tiếp')).toBeTruthy();
    expect(screen.getByText('Cổng trường')).toBeTruthy();
    expect(screen.getByText('Giao dịch hoàn tất')).toBeTruthy();
    expect(screen.getByText('Lúc giao nhận, An Chủ Bài')).toBeTruthy();
    expect(screen.getByText('+10')).toBeTruthy();
    const text = document.body.textContent ?? '';
    for (const hidden of ['IN_PERSON', 'HANDOVER', 'TRADED', 'SILVER', 'COMPLETED', 'DONOR']) {
      expect(text).not.toContain(hidden);
    }
  });

  it('giao dịch hoàn tất không có nút huỷ, giao dịch đang chạy thì có', async () => {
    const { unmount } = renderPage();
    await screen.findByText('Hai bên');
    expect(screen.queryByRole('button', { name: 'Huỷ giao dịch' })).toBeNull();
    unmount();

    vi.mocked(adminRequestApi.detail).mockResolvedValue(detail({ status: 'AWAITING_HANDOVER', cancellable: true }));
    renderPage();
    expect(await screen.findByRole('button', { name: 'Huỷ giao dịch' })).toBeTruthy();
  });

  it('huỷ giao dịch từ trang chi tiết dùng hộp thoại có lý do rồi tải lại', async () => {
    const user = userEvent.setup();
    vi.mocked(adminRequestApi.detail).mockResolvedValue(detail({ status: 'AWAITING_HANDOVER', cancellable: true }));
    vi.mocked(adminRequestApi.cancel).mockResolvedValue({} as never);
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Huỷ giao dịch' }));
    const dialog = await screen.findByRole('dialog');
    await user.type(within(dialog).getByLabelText(/Lý do huỷ/), 'Hai bên không liên lạc được');
    await user.click(within(dialog).getByRole('button', { name: 'Huỷ giao dịch' }));

    await waitFor(() => expect(adminRequestApi.cancel).toHaveBeenCalledWith(5, 'Hai bên không liên lạc được'));
    await waitFor(() => expect(adminRequestApi.detail).toHaveBeenCalledTimes(2));
  });

  it('giao dịch chưa có báo cáo thì không có nút xem tin nhắn và giải thích lý do', async () => {
    renderPage();

    await screen.findByText('Tin nhắn');
    expect(screen.queryByRole('button', { name: 'Xem tin nhắn' })).toBeNull();
    expect(screen.getByText('Chỉ xem được tin nhắn khi giao dịch có báo cáo hoặc khiếu nại.')).toBeTruthy();
  });

  it('giao dịch có báo cáo thì chỉ tải tin nhắn khi bấm Xem tin nhắn', async () => {
    const user = userEvent.setup();
    vi.mocked(adminRequestApi.detail).mockResolvedValue(
      detail({
        status: 'DISPUTED',
        messagesViewable: true,
        reports: [
          { id: 9, reporterName: 'Bình Người Nhận', targetType: 'REQUEST', reason: 'NO_SHOW', stage: 'AFTER', status: 'OPEN', createdAt: '2026-10-26T08:00:00Z' },
        ],
      }),
    );
    vi.mocked(adminRequestApi.messages).mockResolvedValue([
      { id: 1, senderName: 'Bình Người Nhận', senderRole: 'Người nhận', kind: 'TEXT', content: 'Mình đến trễ chút nhé', mediaUrl: null, itemTitle: null, createdAt: '2026-10-25T08:00:00Z' },
    ]);
    renderPage();
    await screen.findByText('Không đến hẹn', { exact: false });
    expect(adminRequestApi.messages).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Xem tin nhắn' }));

    expect(await screen.findByText('Mình đến trễ chút nhé')).toBeTruthy();
    expect(adminRequestApi.messages).toHaveBeenCalledTimes(1);
    expect(screen.getByText(/Người nhận \(Bình Người Nhận\)/)).toBeTruthy();
  });

  it('hiện lỗi của backend khi không xem được tin nhắn', async () => {
    const user = userEvent.setup();
    vi.mocked(adminRequestApi.detail).mockResolvedValue(detail({ messagesViewable: true }));
    vi.mocked(adminRequestApi.messages).mockRejectedValue({
      status: 409,
      message: 'Chỉ xem được tin nhắn khi giao dịch có báo cáo hoặc khiếu nại.',
    });
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Xem tin nhắn' }));

    expect(
      await screen.findAllByText('Chỉ xem được tin nhắn khi giao dịch có báo cáo hoặc khiếu nại.'),
    ).toBeTruthy();
  });

  it('hiện ảnh giao nhận kèm giai đoạn và người đăng bằng tiếng Việt', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(
      detail({
        photos: [
          { mediaId: 3, url: '/api/v1/media/3', stage: 'HANDOVER', uploaderName: 'An Chủ Bài', createdAt: '2026-10-25T09:30:00Z' },
          { mediaId: 4, url: '/api/v1/media/4', stage: 'HANDOVER', uploaderName: null, createdAt: '2026-10-25T09:40:00Z' },
        ],
      }),
    );
    renderPage();

    await screen.findByText('Hai bên');
    expect(screen.getAllByAltText('Ảnh giao nhận')).toHaveLength(2);
    expect(screen.getByText('Lúc giao nhận, An Chủ Bài')).toBeTruthy();
    expect(screen.getByText('Lúc giao nhận')).toBeTruthy();
    expect(screen.queryByText('Chưa có ảnh giao nhận.')).toBeNull();
  });

  it('giao dịch chưa có ảnh thì báo chưa có ảnh giao nhận', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(detail({ photos: [] }));
    renderPage();

    await screen.findByText('Hai bên');
    expect(screen.getByText('Chưa có ảnh giao nhận.')).toBeTruthy();
    expect(screen.queryByAltText('Ảnh giao nhận')).toBeNull();
  });

  it('báo lỗi khi không tải được và cho thử lại', async () => {
    const user = userEvent.setup();
    vi.mocked(adminRequestApi.detail)
      .mockRejectedValueOnce({ status: 404, message: 'Không tìm thấy giao dịch.' })
      .mockResolvedValueOnce(detail());
    renderPage();
    expect(await screen.findByText('Không tìm thấy dữ liệu yêu cầu.')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Thử lại' }));

    expect(await screen.findByText('Hai bên')).toBeTruthy();
  });
});

describe('AdminTransactionDetail luật huỷ có xác nhận, dời lịch và giao hàng hai chiều', () => {
  it('hiện đề nghị huỷ đang chờ cùng hạn phản hồi và số lần dời lịch', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(
      detail({
        status: 'LOGISTICS_CONFIRMED',
        cancelRequest: {
          state: 'PENDING',
          requestedByRole: 'Chủ bài',
          requestedAt: '2026-11-30T08:00:00Z',
          label: 'Tôi bận đột xuất',
          reason: 'Đi công tác',
          expiresAt: '2026-12-01T08:00:00Z',
        },
        reschedule: {
          count: 1,
          pending: true,
          proposedByRole: 'Người nhận',
          proposedAt: '2026-11-30T09:00:00Z',
          proposedMeetingTime: '2026-12-03T09:00:00Z',
          proposedMeetingPlace: null,
          expiresAt: '2026-12-01T09:00:00Z',
        },
      }),
    );
    renderPage();

    await screen.findByText('Đề nghị huỷ');
    expect(screen.getByText('Đang chờ phản hồi')).toBeTruthy();
    expect(screen.getByText('Tôi bận đột xuất. Đi công tác')).toBeTruthy();
    expect(screen.getByText('Dời lịch', { selector: 'h2' })).toBeTruthy();
    expect(screen.getAllByText('Số lần đã dời được đồng ý').length).toBeGreaterThan(0);
    expect(screen.getByText('Giờ hẹn hiện tại')).toBeTruthy();
    expect(screen.getByText('Chủ bài xác nhận lịch ban đầu')).toBeTruthy();
    expect(screen.getByText('Người nhận xác nhận lịch ban đầu')).toBeTruthy();
    expect(document.body.textContent).not.toContain('PENDING');
  });

  it('đề nghị huỷ đã xử lý hiện kết quả bằng chữ, không hiện mã', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(
      detail({
        status: 'DISPUTED',
        cancelRequest: {
          state: 'REJECTED',
          requestedByRole: 'Người nhận',
          requestedAt: '2026-11-30T08:00:00Z',
          label: 'Bên kia chưa gửi hàng',
          reason: null,
          expiresAt: null,
        },
      }),
    );
    renderPage();

    await screen.findByText('Đề nghị huỷ');
    expect(screen.getByText(/bên kia không đồng ý nên giao dịch sang sự cố giao nhận/)).toBeTruthy();
    expect(document.body.textContent).not.toContain('REJECTED');
  });

  it('Giao hàng hai chiều hiện từng chiều với địa chỉ, ngày gửi, mã vận đơn và ngày nhận', async () => {
    vi.mocked(adminRequestApi.detail).mockResolvedValue(
      detail({
        status: 'AWAITING_HANDOVER',
        deliveryMethod: 'SHIPPING',
        meetingPlace: null,
        autoConfirmed: false,
        shippingLegs: [
          {
            leg: 1,
            itemTitle: 'Xe đạp cũ',
            senderRole: 'Chủ bài',
            receiverRole: 'Người nhận',
            address: '12 Lê Lợi',
            shippedAt: '2026-11-25T08:00:00Z',
            trackingCode: 'GHN111',
            receivedAt: '2026-11-26T08:00:00Z',
            step: 'Đã nhận',
          },
          {
            leg: 2,
            itemTitle: 'Ghế xoay',
            senderRole: 'Người nhận',
            receiverRole: 'Chủ bài',
            address: '34 Trần Phú',
            shippedAt: null,
            trackingCode: null,
            receivedAt: null,
            step: 'Chờ gửi',
          },
        ],
        deadlines: {
          autoCancelAt: null,
          autoConfirmAt: null,
          cancelRequestExpiresAt: null,
          rescheduleExpiresAt: null,
          sendDeadlineAt: '2026-12-01T08:00:00Z',
          leg1AutoReceiveAt: null,
          leg2AutoReceiveAt: null,
        },
      }),
    );
    renderPage();

    await screen.findByText('Thông tin gửi hàng');
    const first = within(screen.getByTestId('admin-leg-1'));
    expect(first.getByText('12 Lê Lợi')).toBeTruthy();
    expect(first.getByText('GHN111')).toBeTruthy();
    expect(first.getByText('Xe đạp cũ')).toBeTruthy();
    expect(first.getByText('Đã nhận')).toBeTruthy();
    const second = within(screen.getByTestId('admin-leg-2'));
    expect(second.getByText('34 Trần Phú')).toBeTruthy();
    expect(second.getByText(/Chờ gửi/)).toBeTruthy();
    expect(screen.getByText('Ngày gửi hàng dự kiến')).toBeTruthy();
    expect(screen.getByText('Hạn gửi hàng')).toBeTruthy();
    expect(document.body.textContent).not.toContain('SHIPPING');
  });
});
