// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { catalogApi } from '../../services/catalogApi';
import { itemApi, type ItemResponse } from '../../services/itemApi';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import { Activities } from './Activities';

// vi.hoisted để biến mock dùng được bên trong vi.mock (vi.mock được đưa lên đầu file).
const { refreshWallet } = vi.hoisted(() => ({ refreshWallet: vi.fn() }));

vi.mock('../../app/hooks', () => ({
  useAppSelector: () => ({ id: 'api_10', name: 'Chủ bài', role: 'user' }),
}));

vi.mock('../../app/store', () => ({
  selectCurrentUser: vi.fn(),
}));

vi.mock('../../app/useWallet', () => ({
  useWallet: () => ({
    wallet: { available: 100, held: 0, total: 100 },
    loading: false,
    error: '',
    refresh: refreshWallet,
    replaceWallet: vi.fn(),
  }),
}));

vi.mock('../../services/catalogApi', () => ({
  catalogApi: {
    categories: vi.fn(),
    areas: vi.fn(),
  },
}));

vi.mock('../../services/itemApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/itemApi')>()),
  itemApi: {
    mine: vi.fn(),
    detail: vi.fn(),
    getListingFees: vi.fn(),
  },
}));

vi.mock('../../services/requestApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/requestApi')>()),
  requestApi: {
    mine: vi.fn(),
    forItem: vi.fn(),
    select: vi.fn(),
  },
}));

const giftItem: ItemResponse = {
  id: 31,
  donorId: 10,
  categoryId: 7,
  areaId: 1,
  offerType: 'GIVE',
  title: 'Áo khoác gió',
  description: 'Còn tốt.',
  desiredItem: null,
  condition: 'GOOD',
  defectNote: null,
  brand: null,
  images: [],
  defectImages: [],
  operatingStatus: null,
  status: 'APPROVED',
  createdAt: '2026-11-01T08:00:00Z',
  updatedAt: '2026-11-01T08:00:00Z',
  expireAt: '2099-01-01T08:00:00Z',
  boostedUntil: null,
};

const receiveOnlyRequest: RequestResponse = {
  id: 501,
  itemId: 31,
  receiverId: 42,
  type: 'GIVE',
  status: 'PENDING',
  createdAt: '2026-11-02T08:00:00Z',
  receiverName: 'Tester 2',
  itemOfferType: 'GIVE',
  conversionFee: 7,
};

// Gửi chéo loại: kèm món vào bài Cho nên type vẫn là GIVE nhưng chú thích phải là "Trao đổi".
const swapOfferRequest: RequestResponse = {
  ...receiveOnlyRequest,
  id: 502,
  receiverId: 43,
  receiverName: 'Tester 3',
  offeredItemId: 60,
  offeredItemTitle: 'Ghế xoay',
};

function renderActivities() {
  render(
    <MemoryRouter>
      <Activities />
    </MemoryRouter>,
  );
}

// Hàng của một người xin trong danh sách yêu cầu (thẻ gần nhất có lớp grid chứa tên người đó).
function requestRow(requesterName: string) {
  return screen.getByRole('link', { name: requesterName }).closest('.grid') as HTMLElement;
}

async function openSelectDialog(requesterName: string) {
  fireEvent.click(await screen.findByRole('button', { name: /Xem \d+ yêu cầu/ }));
  fireEvent.click(within(requestRow(requesterName)).getByRole('button', { name: 'Chọn người này' }));
  return screen.getByRole('dialog');
}

beforeEach(() => {
  vi.mocked(catalogApi.categories).mockResolvedValue([
    { id: 7, name: 'Quần áo', parentId: null, restricted: false, boostable: true, sortOrder: 1, activeItemCount: 1 },
  ]);
  vi.mocked(catalogApi.areas).mockResolvedValue([]);
  vi.mocked(itemApi.mine).mockResolvedValue([giftItem]);
  vi.mocked(itemApi.detail).mockResolvedValue(giftItem);
  vi.mocked(requestApi.mine).mockResolvedValue([]);
  vi.mocked(requestApi.forItem).mockResolvedValue([receiveOnlyRequest, swapOfferRequest]);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('nhãn yêu cầu ở Bài của tôi', () => {
  it('nhãn theo việc có kèm món, không theo type của yêu cầu', async () => {
    // given
    renderActivities();

    // when
    fireEvent.click(await screen.findByRole('button', { name: 'Xem 2 yêu cầu' }));

    // then
    expect(within(requestRow('Tester 2')).getByText('Xin nhận')).toBeTruthy();
    expect(within(requestRow('Tester 3')).getByText('Trao đổi')).toBeTruthy();
    expect(within(requestRow('Tester 3')).getByRole('link', { name: 'Ghế xoay' }).getAttribute('href')).toBe(
      '/items/60',
    );
  });
});

describe('hộp Xác nhận chọn người', () => {
  it('chọn yêu cầu kèm món ở bài Cho hỏi trao đổi và cảnh báo phí từ conversionFee', async () => {
    // given
    renderActivities();

    // when
    const dialog = await openSelectDialog('Tester 3');

    // then
    expect(within(dialog).getByText('Bạn muốn trao đổi Áo khoác gió lấy Ghế xoay với Tester 3?')).toBeTruthy();
    expect(
      within(dialog).getByText(
        'Bài đang là Cho tặng. Chọn người này thì bài chuyển sang Trao đổi và bạn trả 7 Credit phí đăng Trao đổi. Các yêu cầu khác sẽ bị từ chối.',
      ),
    ).toBeTruthy();
  });

  it('chọn yêu cầu không kèm món ở bài Trao đổi cảnh báo chuyển sang Cho tặng', async () => {
    // given
    vi.mocked(itemApi.mine).mockResolvedValue([{ ...giftItem, offerType: 'SWAP' }]);
    vi.mocked(requestApi.forItem).mockResolvedValue([
      { ...receiveOnlyRequest, type: 'SWAP', itemOfferType: 'SWAP', conversionFee: 0 },
    ]);
    renderActivities();

    // when
    const dialog = await openSelectDialog('Tester 2');

    // then
    expect(within(dialog).getByText('Bạn muốn chọn Tester 2 để nhận Áo khoác gió?')).toBeTruthy();
    expect(
      within(dialog).getByText(
        'Bài đang là Trao đổi. Chọn người này thì bài chuyển sang Cho tặng; phí đăng đã trả không được hoàn.',
      ),
    ).toBeTruthy();
  });

  it('backend báo thiếu Credit thì giữ hộp mở và hiện nút Nạp Credit', async () => {
    // given
    vi.mocked(requestApi.select).mockRejectedValue({
      status: 402,
      code: 'WALLET_INSUFFICIENT_BALANCE',
      message: 'Không đủ Credit',
    });
    renderActivities();
    const dialog = await openSelectDialog('Tester 3');

    // when
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận' }));

    // then
    expect(await within(dialog).findByText('Cần 7 Credit để chuyển bài sang Trao đổi.')).toBeTruthy();
    expect(within(dialog).getByRole('link', { name: 'Nạp Credit' }).getAttribute('href')).toBe('/credit');
    expect(refreshWallet).not.toHaveBeenCalled();
  });

  it('chọn thành công thì tải lại ví', async () => {
    // given
    vi.mocked(requestApi.select).mockResolvedValue({ ...swapOfferRequest, type: 'SWAP', status: 'RESERVED' });
    renderActivities();
    const dialog = await openSelectDialog('Tester 3');

    // when
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận' }));

    // then
    await vi.waitFor(() => expect(refreshWallet).toHaveBeenCalledTimes(1));
    expect(requestApi.select).toHaveBeenCalledWith(502, 10);
  });
});
