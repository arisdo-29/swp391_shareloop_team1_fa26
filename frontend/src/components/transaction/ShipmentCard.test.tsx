// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import { shippingFailedConsequence, shippingLegs, shippingProgressSummary } from '../../utils/shipping';
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

  it('chiều đã gửi: người nhận thấy mã vận đơn, ba nút nhận, không thành công và từ chối', () => {
    renderCard(
      { ...giveBase, status: 'AWAITING_HANDOVER', donorShippedAt: hoursFromNow(-5), donorTrackingCode: 'GHN123' },
      false,
    );
    expect(screen.getByText('GHN123')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Tôi đã nhận được hàng' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Giao nhận không thành công' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Từ chối giao nhận' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Đề nghị huỷ giao dịch' })).toBeNull();
  });

  it('bên nhận bấm Tôi đã nhận được hàng thì gọi xác nhận nhận hàng', async () => {
    const user = userEvent.setup();
    vi.spyOn(requestApi, 'confirmHandover').mockResolvedValue({ ...giveBase, status: 'COMPLETED' });
    const handlers = renderCard(
      { ...giveBase, status: 'AWAITING_HANDOVER', donorShippedAt: hoursFromNow(-5), donorTrackingCode: 'GHN123' },
      false,
    );
    await user.click(screen.getByRole('button', { name: 'Tôi đã nhận được hàng' }));
    await waitFor(() => expect(requestApi.confirmHandover).toHaveBeenCalledWith(101, 12, {}));
    expect(handlers.onUpdated).toHaveBeenCalledOnce();
  });
});

describe('ShipmentCard Trao đổi', () => {
  it('cả hai bên có hai khối, chiều chưa gửi chỉ bên gửi có nút Tôi đã gửi hàng', () => {
    renderCard(swapBase, true);
    expect(screen.getByText('Món bạn gửi')).toBeTruthy();
    expect(screen.getByText('Món bạn nhận')).toBeTruthy();
    const sendBlock = screen.getByTestId('shipment-leg-1');
    expect(within(sendBlock).getByRole('button', { name: 'Tôi đã gửi hàng' })).toBeTruthy();
    const receiveBlock = screen.getByTestId('shipment-leg-2');
    expect(within(receiveBlock).queryByRole('button', { name: 'Tôi đã gửi hàng' })).toBeNull();
  });

  it('A đã nhận còn B chưa thì A thấy dòng đang chờ bên kia nhận hàng của họ', () => {
    renderCard(
      {
        ...swapBase,
        status: 'AWAITING_HANDOVER',
        donorShippedAt: hoursFromNow(-30),
        donorTrackingCode: 'GHN111',
        receiverShippedAt: hoursFromNow(-20),
        receiverTrackingCode: 'VTP222',
        receiverHandoverAt: hoursFromNow(-10),
      },
      false,
    );
    expect(screen.getByText('Bạn đã nhận, đang chờ Bình nhận hàng của họ.')).toBeTruthy();
  });

  it('gửi hàng mở hộp nhập mã vận đơn 3 đến 50 ký tự rồi gọi API', async () => {
    const user = userEvent.setup();
    vi.spyOn(requestApi, 'markShipped').mockResolvedValue({ ...swapBase, status: 'AWAITING_HANDOVER' });
    const handlers = renderCard(swapBase, true);

    await user.click(screen.getByRole('button', { name: 'Tôi đã gửi hàng' }));
    const submit = screen.getByRole('button', { name: 'Xác nhận đã gửi' }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText(/^Mã vận đơn/), { target: { value: 'ab' } });
    expect(screen.getByText('Mã vận đơn phải từ 3 đến 50 ký tự.')).toBeTruthy();
    expect(submit.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText(/^Mã vận đơn/), { target: { value: 'GHN12345' } });
    await user.click(submit);

    await waitFor(() =>
      expect(requestApi.markShipped).toHaveBeenCalledWith(101, 9, { trackingCode: 'GHN12345', mediaIds: undefined }),
    );
    expect(handlers.onUpdated).toHaveBeenCalledOnce();
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
