// @vitest-environment jsdom

import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { actions, store } from '../../app/store';
import { chatApi, type MessageResponse } from '../../services/chatApi';
import { itemApi, type ItemResponse } from '../../services/itemApi';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import type { User } from '../../types/domain';
import { Messages } from './Messages';

// vi.hoisted để biến mock dùng được bên trong vi.mock (vi.mock được đưa lên đầu file).
const { refreshWallet } = vi.hoisted(() => ({ refreshWallet: vi.fn() }));

vi.mock('../../services/requestApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/requestApi')>()),
  requestApi: { mine: vi.fn(), forItem: vi.fn(), get: vi.fn(), select: vi.fn() },
}));
vi.mock('../../services/itemApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/itemApi')>()),
  itemApi: { mine: vi.fn(), detail: vi.fn() },
}));
vi.mock('../../services/chatApi', () => ({
  chatApi: {
    list: vi.fn(),
    send: vi.fn(),
    pinnedItems: vi.fn(),
    proposeOfferChange: vi.fn(),
    acceptOfferChange: vi.fn(),
    declineOfferChange: vi.fn(),
  },
}));
vi.mock('../../services/mediaApi', () => ({ mediaApi: { upload: vi.fn() } }));
vi.mock('../../app/useWallet', () => ({ useWallet: () => ({ refresh: refreshWallet }) }));

const DONOR_ID = 9;
const RECEIVER_ID = 12;

const user = (id: number): User => ({
  id: `api_${id}`,
  username: `u${id}`,
  password: '',
  name: `Người dùng ${id}`,
  email: `u${id}@example.com`,
  phone: '0900000000',
  district: 'Quận 1',
  avatarInitials: 'ND',
  totalCredit: 0,
  availableCredit: 0,
  holdCredit: 0,
  rewardPoints: 0,
  reputationStars: 5,
  rank: 'BRONZE',
  status: 'active',
  role: 'user',
  joinedAt: '2026-01-01T00:00:00Z',
  totalTx: 0,
});

const item = (overrides: Partial<ItemResponse> = {}): ItemResponse => ({
  id: 45,
  donorId: DONOR_ID,
  categoryId: 1,
  areaId: 1,
  offerType: 'GIVE',
  title: 'Xe đạp cũ',
  description: 'Xe còn tốt',
  desiredItem: null,
  condition: 'GOOD',
  defectNote: null,
  brand: null,
  images: [],
  defectImages: [],
  operatingStatus: null,
  status: 'RESERVED',
  createdAt: '2026-10-20T08:00:00Z',
  updatedAt: '2026-10-20T08:00:00Z',
  expireAt: null,
  boostedUntil: null,
  ...overrides,
});

// Món người nhận có thể đem đề nghị đổi.
const receiverChair = item({ id: 60, donorId: RECEIVER_ID, title: 'Ghế xoay', status: 'APPROVED' });

const request = (overrides: Partial<RequestResponse> = {}): RequestResponse => ({
  id: 101,
  itemId: 45,
  receiverId: RECEIVER_ID,
  type: 'GIVE',
  status: 'RESERVED',
  createdAt: '2026-10-20T09:00:00Z',
  itemTitle: 'Xe đạp cũ',
  donorId: DONOR_ID,
  donorName: 'An Chủ Bài',
  receiverName: 'Bình Người Nhận',
  itemOfferType: 'GIVE',
  conversionFee: 7,
  ...overrides,
});

const pendingOffer: MessageResponse = {
  id: 31,
  requestId: 101,
  senderId: RECEIVER_ID,
  kind: 'OFFER_CHANGE',
  content: 'Đổi ghế nhé',
  mediaId: null,
  refItemId: 60,
  itemCard: {
    id: 60,
    title: 'Ghế xoay',
    offerType: 'GIVE',
    status: 'APPROVED',
    imageUrl: null,
    donorId: RECEIVER_ID,
  },
  offerStatus: 'PENDING',
  moderation: 'PASS',
  blockedReason: null,
  createdAt: '2026-10-21T09:00:00Z',
};

/** Dựng trang Tin nhắn cho người dùng `viewerId` với giao dịch `current` và danh sách tin `messages`. */
async function renderMessages(viewerId: number, current: RequestResponse, messages: MessageResponse[] = []) {
  store.dispatch(actions.loginFromApi(user(viewerId)));
  vi.mocked(requestApi.mine).mockResolvedValue(viewerId === RECEIVER_ID ? [current] : []);
  vi.mocked(itemApi.mine).mockResolvedValue(viewerId === DONOR_ID ? [item()] : [receiverChair]);
  vi.mocked(requestApi.forItem).mockResolvedValue(viewerId === DONOR_ID ? [current] : []);
  vi.mocked(requestApi.get).mockResolvedValue(current);
  vi.mocked(itemApi.detail).mockResolvedValue(item());
  vi.mocked(chatApi.list).mockResolvedValue(messages);
  vi.mocked(chatApi.pinnedItems).mockResolvedValue([]);
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/requests/101']}>
        <Routes>
          <Route path="/requests/:requestId" element={<Messages />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
  await screen.findByRole('region', { name: /Cuộc trò chuyện với/ });
  await screen.findByPlaceholderText('Nhập tin nhắn...');
}

beforeEach(() => {
  vi.stubGlobal('scrollTo', vi.fn());
  Element.prototype.scrollIntoView = vi.fn();
});

afterEach(() => {
  cleanup();
  store.dispatch(actions.logout());
  vi.clearAllMocks();
  vi.unstubAllGlobals();
});

describe('nút đề nghị kèm món đổi', () => {
  it('người nhận giao dịch Cho tặng thấy nút đề nghị', async () => {
    // when
    await renderMessages(RECEIVER_ID, request());

    // then
    expect(screen.getByRole('button', { name: 'Đề nghị kèm món đổi' })).toBeTruthy();
  });

  it('người nhận giao dịch Trao đổi không thấy nút đề nghị', async () => {
    // when
    await renderMessages(RECEIVER_ID, request({ type: 'SWAP', offeredItemId: 60, itemOfferType: 'SWAP' }));

    // then
    expect(screen.queryByRole('button', { name: 'Đề nghị kèm món đổi' })).toBeNull();
  });

  it('chủ bài không thấy nút đề nghị', async () => {
    // when
    await renderMessages(DONOR_ID, request());

    // then
    expect(screen.queryByRole('button', { name: 'Đề nghị kèm món đổi' })).toBeNull();
  });

  it('giao dịch đã chốt lịch thì ẩn nút đề nghị', async () => {
    // when
    await renderMessages(RECEIVER_ID, request({ status: 'LOGISTICS_CONFIRMED' }));

    // then
    expect(screen.queryByRole('button', { name: 'Đề nghị kèm món đổi' })).toBeNull();
  });

  it('đang có đề nghị chờ thì nút thành Đổi món đề nghị', async () => {
    // when
    await renderMessages(RECEIVER_ID, request(), [pendingOffer]);

    // then
    expect(await screen.findByRole('button', { name: 'Đổi món đề nghị' })).toBeTruthy();
  });

  it('gửi đề nghị gọi API với món đã chọn', async () => {
    // given
    const userEvents = userEvent.setup();
    vi.mocked(chatApi.proposeOfferChange).mockResolvedValue(pendingOffer);
    await renderMessages(RECEIVER_ID, request());
    await userEvents.click(screen.getByRole('button', { name: 'Đề nghị kèm món đổi' }));
    const dialog = screen.getByRole('dialog', { name: 'Đề nghị kèm món đổi' });

    // when
    await userEvents.click(within(dialog).getByRole('button', { name: /Ghế xoay/ }));
    await userEvents.click(within(dialog).getByRole('button', { name: 'Gửi đề nghị' }));

    // then
    expect(chatApi.proposeOfferChange).toHaveBeenCalledWith(101, 60, undefined);
  });
});

describe('chủ bài trả lời đề nghị đổi món', () => {
  it('chủ bài thấy Đồng ý và Từ chối ở đề nghị đang chờ', async () => {
    // when
    await renderMessages(DONOR_ID, request(), [pendingOffer]);

    // then
    expect(await screen.findByRole('button', { name: 'Đồng ý' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Từ chối' })).toBeTruthy();
    expect(screen.getByText('Đang chờ')).toBeTruthy();
  });

  it('người nhận không thấy nút Đồng ý ở đề nghị của mình', async () => {
    // when
    await renderMessages(RECEIVER_ID, request(), [pendingOffer]);
    await screen.findByText('Đang chờ');

    // then
    expect(screen.queryByRole('button', { name: 'Đồng ý' })).toBeNull();
  });

  it('đồng ý gọi đúng API, ghi rõ phí, tải lại thẻ ghim và ví', async () => {
    // given
    const userEvents = userEvent.setup();
    vi.mocked(chatApi.acceptOfferChange).mockResolvedValue(
      request({ type: 'SWAP', offeredItemId: 60, itemOfferType: 'SWAP', conversionFee: 0 }),
    );
    await renderMessages(DONOR_ID, request(), [pendingOffer]);
    await userEvents.click(await screen.findByRole('button', { name: 'Đồng ý' }));
    const dialog = screen.getByRole('dialog', { name: 'Đồng ý trao đổi' });
    expect(
      within(dialog).getByText(
        'Đồng ý trao đổi Xe đạp cũ lấy Ghế xoay? Bài chuyển sang Trao đổi và bạn trả 7 Credit phí đăng Trao đổi.',
      ),
    ).toBeTruthy();

    // when
    await userEvents.click(within(dialog).getByRole('button', { name: 'Đồng ý' }));

    // then
    expect(chatApi.acceptOfferChange).toHaveBeenCalledWith(101, 31);
    await vi.waitFor(() => expect(chatApi.pinnedItems).toHaveBeenCalledTimes(2));
    expect(refreshWallet).toHaveBeenCalledTimes(1);
  });

  it('thiếu Credit thì báo nạp, hộp vẫn mở và đề nghị vẫn chờ', async () => {
    // given
    const userEvents = userEvent.setup();
    vi.mocked(chatApi.acceptOfferChange).mockRejectedValue({
      status: 402,
      code: 'WALLET_INSUFFICIENT_BALANCE',
      message: 'Không đủ Credit',
    });
    await renderMessages(DONOR_ID, request(), [pendingOffer]);
    await userEvents.click(await screen.findByRole('button', { name: 'Đồng ý' }));
    const dialog = screen.getByRole('dialog', { name: 'Đồng ý trao đổi' });

    // when
    await userEvents.click(within(dialog).getByRole('button', { name: 'Đồng ý' }));

    // then
    expect(await within(dialog).findByText('Cần 7 Credit để chuyển bài sang Trao đổi.')).toBeTruthy();
    expect(within(dialog).getByRole('link', { name: 'Nạp Credit' }).getAttribute('href')).toBe('/credit');
    expect(screen.getByText('Đang chờ')).toBeTruthy();
    expect(chatApi.pinnedItems).toHaveBeenCalledTimes(1);
    expect(refreshWallet).not.toHaveBeenCalled();
  });

  it('từ chối gọi API và hiện trạng thái Đã từ chối', async () => {
    // given
    const userEvents = userEvent.setup();
    vi.mocked(chatApi.declineOfferChange).mockResolvedValue({ ...pendingOffer, offerStatus: 'DECLINED' });
    await renderMessages(DONOR_ID, request(), [pendingOffer]);

    // when
    await userEvents.click(await screen.findByRole('button', { name: 'Từ chối' }));

    // then
    expect(chatApi.declineOfferChange).toHaveBeenCalledWith(101, 31);
    expect(await screen.findByText('Đã từ chối')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Đồng ý' })).toBeNull();
  });
});
