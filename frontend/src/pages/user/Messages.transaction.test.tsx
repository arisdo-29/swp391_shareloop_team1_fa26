// @vitest-environment jsdom

import { cleanup, render, screen, within } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { actions, store } from '../../app/store';
import { chatApi } from '../../services/chatApi';
import { itemApi, type ItemResponse } from '../../services/itemApi';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import type { User } from '../../types/domain';
import { Messages } from './Messages';

vi.mock('../../services/requestApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/requestApi')>()),
  requestApi: {
    mine: vi.fn(),
    forItem: vi.fn(),
    get: vi.fn(),
    media: vi.fn(),
    cancelTransaction: vi.fn(),
    reportHandoverIssue: vi.fn(),
    getContact: vi.fn(),
  },
}));
vi.mock('../../services/itemApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/itemApi')>()),
  itemApi: { mine: vi.fn(), detail: vi.fn() },
}));
vi.mock('../../services/chatApi', () => ({
  chatApi: { list: vi.fn(), send: vi.fn(), pinnedItems: vi.fn() },
}));
vi.mock('../../services/mediaApi', () => ({ mediaApi: { upload: vi.fn() } }));

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

const request = (overrides: Partial<RequestResponse> = {}): RequestResponse => ({
  id: 101,
  itemId: 45,
  receiverId: RECEIVER_ID,
  type: 'GIVE',
  status: 'LOGISTICS_CONFIRMED',
  contactRevealedAt: '2026-10-21T08:00:00Z',
  createdAt: '2026-10-20T09:00:00Z',
  itemTitle: 'Xe đạp cũ',
  donorId: DONOR_ID,
  donorName: 'An Chủ Bài',
  receiverName: 'Bình Người Nhận',
  ...overrides,
});

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location">{`${location.pathname}${location.search}`}</span>;
}

/** Dựng trang Tin nhắn cho người dùng `viewerId` với giao dịch `current` đang mở. */
async function renderMessages(viewerId: number, current: RequestResponse) {
  store.dispatch(actions.loginFromApi(user(viewerId)));
  const ownedItems = viewerId === DONOR_ID ? [item()] : [];
  vi.mocked(requestApi.mine).mockResolvedValue(viewerId === RECEIVER_ID ? [current] : []);
  vi.mocked(itemApi.mine).mockResolvedValue(ownedItems);
  vi.mocked(requestApi.forItem).mockResolvedValue([current]);
  vi.mocked(requestApi.get).mockResolvedValue(current);
  vi.mocked(requestApi.media).mockResolvedValue([]);
  vi.mocked(itemApi.detail).mockResolvedValue(item());
  vi.mocked(chatApi.list).mockResolvedValue([]);
  vi.mocked(chatApi.pinnedItems).mockResolvedValue([]);
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/requests/101']}>
        <Routes>
          <Route path="/requests/:requestId" element={<Messages />} />
          <Route path="/items/:itemId" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
  await screen.findByRole('region', { name: /Cuộc trò chuyện với/ });
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

// Huỷ giao dịch, hạn giải trình, báo chưa nhận được: đã chuyển sang TransactionProgress.test.tsx
// cùng với chức năng (trang Tiến trình giao dịch).
describe('Trang Tin nhắn sau khi tách trang Tiến trình', () => {
  it('không còn cột Tiến trình giao dịch cũ, chỉ còn nút Xem tiến trình', async () => {
    await renderMessages(RECEIVER_ID, request());

    expect(screen.queryByLabelText('Tiến trình giao dịch')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Huỷ giao dịch' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Xem tiến trình' })).toBeTruthy();
  });
});

describe('Gửi ảnh trong giao dịch Cho tặng', () => {
  it('ẩn nút Gửi ảnh và dòng gợi ý với người nhận đồ Cho tặng', async () => {
    await renderMessages(RECEIVER_ID, request({ type: 'GIVE', status: 'RESERVED' }));
    await screen.findByPlaceholderText('Nhập tin nhắn...');

    expect(screen.queryByText('Gửi ảnh')).toBeNull();
    expect(screen.queryByText('Gửi ảnh được sau khi hai bên chốt lịch hẹn.')).toBeNull();
  });

  it('chủ bài trong giao dịch Cho tặng vẫn thấy nút Gửi ảnh', async () => {
    await renderMessages(DONOR_ID, request({ type: 'GIVE' }));
    await screen.findByPlaceholderText('Nhập tin nhắn...');

    expect(screen.getByText('Gửi ảnh')).toBeTruthy();
  });

  it('người nhận trong giao dịch Trao đổi vẫn thấy nút Gửi ảnh', async () => {
    await renderMessages(RECEIVER_ID, request({ type: 'SWAP' }));
    await screen.findByPlaceholderText('Nhập tin nhắn...');

    expect(screen.getByText('Gửi ảnh')).toBeTruthy();
  });
});

describe('Khung chat gọn', () => {
  it('chỉ có một thẻ món chính, dải thẻ ghim không lặp lại món đó', async () => {
    // given: thẻ ghim gồm cả món chính và một món đề nghị
    vi.mocked(chatApi.pinnedItems).mockResolvedValue([
      { id: 45, title: 'Xe đạp cũ', offerType: 'GIVE', status: 'RESERVED', imageUrl: null, donorId: DONOR_ID },
      { id: 77, title: 'Mũ bảo hiểm', offerType: 'SWAP', status: 'RESERVED', imageUrl: null, donorId: RECEIVER_ID },
    ]);

    // when
    store.dispatch(actions.loginFromApi(user(RECEIVER_ID)));
    vi.mocked(requestApi.mine).mockResolvedValue([request()]);
    vi.mocked(itemApi.mine).mockResolvedValue([]);
    vi.mocked(requestApi.forItem).mockResolvedValue([request()]);
    vi.mocked(requestApi.get).mockResolvedValue(request());
    vi.mocked(requestApi.media).mockResolvedValue([]);
    vi.mocked(itemApi.detail).mockResolvedValue(item());
    vi.mocked(chatApi.list).mockResolvedValue([]);
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/requests/101']}>
          <Routes>
            <Route path="/requests/:requestId" element={<Messages />} />
          </Routes>
        </MemoryRouter>
      </Provider>,
    );
    const chat = await screen.findByRole('region', { name: 'Cuộc trò chuyện với An Chủ Bài' });
    await within(chat).findByText('Mũ bảo hiểm');

    // then
    expect(within(chat).getAllByText('Xe đạp cũ')).toHaveLength(1);
    expect(within(chat).getByRole('button', { name: 'Xem tiến trình' })).toBeTruthy();
    expect(within(chat).getByRole('button', { name: 'Xem hồ sơ' })).toBeTruthy();
  });
});
