// @vitest-environment jsdom

import { act, cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mediaApi } from '../../services/mediaApi';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import { shippingFailedConsequence, shippingLegs, shippingProgressSummary } from '../../utils/shipping';
import { formatDeadline } from '../../utils/request';
import { ShipmentCard } from './ShipmentCard';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

const hoursFromNow = (hours: number) => new Date(Date.now() + hours * 3_600_000).toISOString();

const giveBase: RequestResponse = {
  id: 101,
  itemId: 45,
  receiverId: 12,
  donorId: 9,
  type: 'GIVE',
  status: 'LOGISTICS_CONFIRMED',
  deliveryMethod: 'SHIPPING',
  contactRevealedAt: '2026-11-30T08:00:00Z',
  meetingTime: hoursFromNow(24),
  sendDeadlineAt: hoursFromNow(96),
  itemTitle: 'Máy ảnh',
  receiverAddress: '12 Lê Lợi',
  createdAt: '2026-11-20T08:00:00Z',
};

const swapBase: RequestResponse = {
  ...giveBase,
  type: 'SWAP',
  offeredItemId: 77,
  offeredItemTitle: 'Mũ bảo hiểm',
  donorAddress: '34 Trần Phú',
};

function renderCard(request: RequestResponse, viewerIsDonor: boolean) {
  const handlers = {
    onUpdated: vi.fn(),
    onOpenFailed: vi.fn(),
    onOpenRefuse: vi.fn(),
    onOpenCancelRequest: vi.fn(),
    onOpenRejectCancel: vi.fn(),
  };
  render(
    <ShipmentCard
      request={request}
      currentUserId={viewerIsDonor ? 9 : 12}
      viewerIsDonor={viewerIsDonor}
      partnerName="Bình"
      {...handlers}
    />,
  );
  return handlers;
}

describe('ShipmentCard Cho tặng', () => {
  it('chủ bài chỉ có khối Món bạn gửi, không có nút nhận hàng', () => {
    renderCard(giveBase, true);
    expect(screen.getByText('Món bạn gửi')).toBeTruthy();
    expect(screen.queryByText('Món bạn nhận')).toBeNull();
    expect(screen.getByRole('button', { name: 'Tôi đã gửi hàng' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Tôi đã nhận được hàng' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Đề nghị huỷ giao dịch' })).toBeTruthy();
  });

  it('người nhận chỉ có khối Món bạn nhận, chưa gửi thì chưa báo được khi chưa quá hạn', () => {
    renderCard(giveBase, false);
    expect(screen.getByText('Món bạn nhận')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Tôi đã nhận được hàng' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Giao nhận không thành công' })).toBeNull();
    expect(screen.getByText(/Chưa quá hạn gửi nên chưa báo được/)).toBeTruthy();
    expect(screen.getByText(/Bình chưa gửi hàng, hạn gửi đến/)).toBeTruthy();
  });

  it('quá hạn gửi thì người nhận thấy nút Giao nhận không thành công', async () => {
    const user = userEvent.setup();
    const handlers = renderCard({ ...giveBase, sendDeadlineAt: hoursFromNow(-2) }, false);
    await user.click(screen.getByRole('button', { name: 'Giao nhận không thành công' }));
    expect(handlers.onOpenFailed).toHaveBeenCalledOnce();
  });

  it('chiều đã gửi hiển thị thời điểm gửi và các thao tác của người nhận', () => {
    renderCard(
      { ...giveBase, status: 'AWAITING_HANDOVER', donorShippedAt: hoursFromNow(-5) },
      false,
    );
    expect(screen.getByText('Người gửi báo đã gửi lúc')).toBeTruthy();
    expect(screen.queryByText('Mã vận đơn')).toBeNull();
    expect(screen.getByRole('button', { name: 'Tôi đã nhận được hàng' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Giao nhận không thành công' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Từ chối giao nhận' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Đề nghị huỷ giao dịch' })).toBeNull();
  });

  it('bên nhận bấm Tôi đã nhận được hàng thì gọi xác nhận nhận hàng', async () => {
    const user = userEvent.setup();
    vi.spyOn(requestApi, 'confirmHandover').mockResolvedValue({ ...giveBase, status: 'COMPLETED' });
    const handlers = renderCard(
      { ...giveBase, status: 'AWAITING_HANDOVER', donorShippedAt: hoursFromNow(-5) },
      false,
    );
    await user.click(screen.getByRole('button', { name: 'Tôi đã nhận được hàng' }));
    await waitFor(() => expect(requestApi.confirmHandover).toHaveBeenCalledWith(101, 12, {}));
    expect(handlers.onUpdated).toHaveBeenCalledOnce();
  });

  it('chủ bài xác nhận đã gửi hàng mà không cần nhập mã hoặc chọn ảnh', async () => {
    const user = userEvent.setup();
    const updated: RequestResponse = { ...giveBase, status: 'AWAITING_HANDOVER', donorShippedAt: hoursFromNow(0) };
    const markShipped = vi.spyOn(requestApi, 'markShipped').mockResolvedValue(updated);
    const handlers = renderCard(giveBase, true);

    await user.click(screen.getByRole('button', { name: 'Tôi đã gửi hàng' }));
    const dialog = screen.getByRole('dialog', { name: 'Tôi đã gửi hàng' });
    expect(within(dialog).queryByLabelText('Mã vận đơn')).toBeNull();
    expect(within(dialog).getByLabelText('Ảnh minh chứng (không bắt buộc)')).toBeTruthy();
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận đã gửi' }));

    await waitFor(() => expect(handlers.onUpdated).toHaveBeenCalledWith(updated));
    expect(markShipped).toHaveBeenCalledExactlyOnceWith(101, 9, { mediaIds: undefined });
  });
});

describe('ShipmentCard Trao đổi', () => {
  it('cho biết món của mình đã gửi sẽ được giữ ẩn nếu từ chối món nhận', () => {
    renderCard({
      ...swapBase,
      status: 'AWAITING_HANDOVER',
      donorShippedAt: hoursFromNow(-3),
      receiverShippedAt: hoursFromNow(-2),
    }, true);

    expect(screen.getByText(/Món bạn đã gửi được giữ ẩn/i)).toBeTruthy();
  });

  it('hiện hạn xác nhận đã nhận từ máy chủ theo từng chiều', () => {
    const leg1AutoReceiveAt = hoursFromNow(30);
    const leg2AutoReceiveAt = hoursFromNow(54);
    renderCard({
      ...swapBase,
      status: 'AWAITING_HANDOVER',
      donorShippedAt: hoursFromNow(-10),
      receiverShippedAt: hoursFromNow(-2),
      leg1AutoReceiveAt,
      leg2AutoReceiveAt,
    }, true);
    const sent = within(screen.getByTestId('shipment-leg-1'));
    const received = within(screen.getByTestId('shipment-leg-2'));
    expect(sent.getByText('Bạn báo đã gửi lúc')).toBeTruthy();
    expect(received.getByText('Người gửi báo đã gửi lúc')).toBeTruthy();
    expect(received.getByText(/Nếu bạn không phản hồi trước hạn/)).toBeTruthy();
    expect(sent.getByText('Hạn xác nhận đã nhận')).toBeTruthy();
    expect(sent.getByText(formatDeadline(leg1AutoReceiveAt))).toBeTruthy();
    expect(received.getByText('Hạn xác nhận đã nhận')).toBeTruthy();
    expect(received.getByText(formatDeadline(leg2AutoReceiveAt))).toBeTruthy();
  });

  it('cả hai bên có hai khối, chiều chưa gửi chỉ bên gửi có nút Đã gửi hàng', () => {
    renderCard(swapBase, true);
    expect(screen.getByText('Món bạn gửi')).toBeTruthy();
    expect(screen.getByText('Món bạn nhận')).toBeTruthy();
    const sendBlock = screen.getByTestId('shipment-leg-1');
    expect(within(sendBlock).getByRole('button', { name: 'Đã gửi hàng' })).toBeTruthy();
    const receiveBlock = screen.getByTestId('shipment-leg-2');
    expect(within(receiveBlock).queryByRole('button', { name: 'Đã gửi hàng' })).toBeNull();
  });

  it('mở và đóng nút gửi theo khung giờ kể cả khi đang mở trang', () => {
    vi.useFakeTimers();
    try {
      renderCard({ ...swapBase, exchangeShippingWindowStart: hoursFromNow(0.5), meetingTime: hoursFromNow(1) }, true);
      const button = screen.getByRole<HTMLButtonElement>('button', { name: 'Đã gửi hàng' });
      expect(button.disabled).toBe(true);
      act(() => vi.advanceTimersByTime(30 * 60_000 + 25));
      expect(button.disabled).toBe(false);
      act(() => vi.advanceTimersByTime(30 * 60_000));
      expect(button.disabled).toBe(true);
    } finally {
      vi.useRealTimers();
    }
  });

  it('A đã nhận còn B chưa thì A thấy dòng đang chờ bên kia nhận hàng của họ', () => {
    renderCard(
      {
        ...swapBase,
        status: 'AWAITING_HANDOVER',
        donorShippedAt: hoursFromNow(-30),
        receiverShippedAt: hoursFromNow(-20),
        receiverHandoverAt: hoursFromNow(-10),
      },
      false,
    );
    expect(screen.getByText('Bạn đã nhận, đang chờ Bình nhận hàng của họ.')).toBeTruthy();
  });

  it('người gửi món đổi chỉ xác nhận được sau khi chọn ảnh biên nhận', async () => {
    const user = userEvent.setup();
    const updated: RequestResponse = { ...swapBase, status: 'AWAITING_HANDOVER', receiverShippedAt: hoursFromNow(0) };
    const markShipped = vi.spyOn(requestApi, 'markShipped').mockResolvedValue(updated);
    const upload = vi.spyOn(mediaApi, 'upload').mockResolvedValue({ mediaId: 42, url: '/api/v1/media/42' });
    const handlers = renderCard(swapBase, false);
    const photo = new File(['receipt'], 'bien-nhan.png', { type: 'image/png' });
    await user.click(screen.getByRole('button', { name: 'Đã gửi hàng' }));
    const dialog = screen.getByRole('dialog', { name: 'Đã gửi hàng' });
    const confirm = within(dialog).getByRole<HTMLButtonElement>('button', { name: 'Xác nhận đã gửi' });
    expect(confirm.disabled).toBe(true);
    await user.upload(within(dialog).getByLabelText('Ảnh biên nhận (bắt buộc)'), photo);
    expect(confirm.disabled).toBe(false);
    await user.click(within(dialog).getByRole('button', { name: 'Xác nhận đã gửi' }));

    await waitFor(() => expect(handlers.onUpdated).toHaveBeenCalledWith(updated));
    expect(upload).toHaveBeenCalledExactlyOnceWith(photo);
    expect(markShipped).toHaveBeenCalledExactlyOnceWith(101, 12, { mediaIds: [42] });
  });

  it('có đề nghị huỷ đang chờ thì hiện thẻ phản hồi và ẩn nút nhận hàng', () => {
    renderCard(
      {
        ...swapBase,
        status: 'AWAITING_HANDOVER',
        donorShippedAt: hoursFromNow(-5),
        cancelRequestedBy: 9,
        cancelRequestedAt: hoursFromNow(-1),
        cancelRequestLabel: 'Bên kia chưa gửi hàng',
        cancelRequestExpiresAt: hoursFromNow(23),
      },
      false,
    );
    expect(screen.getByTestId('cancel-request-panel')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Đồng ý huỷ' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Tôi đã nhận được hàng' })).toBeNull();
  });
});

describe('tiện ích giao hàng', () => {
  it('Cho tặng có một chiều, Trao đổi có hai chiều với bước theo dữ liệu', () => {
    expect(shippingLegs(giveBase)).toHaveLength(1);
    const swap = shippingLegs({
      ...swapBase,
      donorShippedAt: hoursFromNow(-5),
      donorHandoverAt: hoursFromNow(-1),
      receiverShippedAt: hoursFromNow(-3),
    });
    expect(swap.map((leg) => leg.step)).toEqual(['SHIPPED', 'RECEIVED']);
    expect(
      shippingLegs({ ...swapBase, donorShippedAt: hoursFromNow(-5), receiverHandoverAt: hoursFromNow(-2) })[0].step,
    ).toBe('RECEIVED');
  });

  it('tóm tắt tiến độ và hậu quả báo chưa nhận theo từng chiều', () => {
    expect(shippingProgressSummary(swapBase)).toBe('Máy ảnh: Chờ gửi. Mũ bảo hiểm: Chờ gửi');
    expect(shippingFailedConsequence(giveBase, false, 'Bình', 48, 24)).toContain('Bình chưa gửi hàng');
    expect(
      shippingFailedConsequence({ ...giveBase, donorShippedAt: hoursFromNow(-2) }, false, 'Bình', 48, 24),
    ).toContain('chuyển sang sự cố giao nhận');
  });
});
