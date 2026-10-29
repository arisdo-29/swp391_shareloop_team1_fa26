// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
  await screen.findByLabelText('Tiến trình giao dịch');
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

describe('Huỷ giao dịch trong bảng Tiến trình', () => {
  it('mở bài đăng kèm nguồn từ trang tin nhắn', async () => {
    const userEvents = userEvent.setup();
    await renderMessages(RECEIVER_ID, request());

    await userEvents.click(await screen.findByRole('button', { name: 'Xem bài đăng' }));

    expect(screen.getByTestId('location').textContent).toBe('/items/45?from=messages');
  });

  it.each(['RESERVED', 'AWAITING_LOGISTICS', 'LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER'] as const)(
    'hiện khối Huỷ giao dịch và chú thích khi trạng thái %s',
    async (status) => {
      await renderMessages(RECEIVER_ID, request({ status }));

      expect(await screen.findByRole('button', { name: 'Huỷ giao dịch' })).toBeTruthy();
      expect(
        screen.getByText('Cần nêu lý do. Bài đăng mở lại cho người khác, không mất phí đăng lần nữa.'),
      ).toBeTruthy();
    },
  );

  it.each(['COMPLETED', 'DISPUTED', 'CANCELLED'] as const)('không hiện khi trạng thái %s', async (status) => {
    await renderMessages(RECEIVER_ID, request({ status }));
    await screen.findByText('Xem bài đăng');

    expect(screen.queryByRole('button', { name: 'Huỷ giao dịch' })).toBeNull();
  });

  it('bắt buộc lý do rồi gọi API và cập nhật trạng thái trả về', async () => {
    const userEvents = userEvent.setup();
    vi.mocked(requestApi.cancelTransaction).mockResolvedValue(
      request({ status: 'CANCELLED', cancelReason: '[Người nhận] Tôi bận việc đột xuất' }),
    );
    await renderMessages(RECEIVER_ID, request());

    await userEvents.click(await screen.findByRole('button', { name: 'Huỷ giao dịch' }));
    const dialog = await screen.findByRole('dialog');
    const submit = within(dialog).getByRole('button', { name: 'Huỷ giao dịch' }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);

    const reasonBox = within(dialog).getByLabelText(/Lý do huỷ/);
    expect(reasonBox.getAttribute('maxlength')).toBe('500');
    await userEvents.type(reasonBox, 'Tôi bận việc đột xuất');
    expect(within(dialog).getByText('21/500')).toBeTruthy();
    await userEvents.click(submit);

    await waitFor(() =>
      expect(requestApi.cancelTransaction).toHaveBeenCalledWith(101, RECEIVER_ID, 'Tôi bận việc đột xuất'),
    );
    expect(await screen.findByText(/Lý do: \[Người nhận\] Tôi bận việc đột xuất/)).toBeTruthy();
  });
});

describe('Báo chưa nhận được / chưa giao được', () => {
  it('người nhận thấy nút Tôi chưa nhận được khi chủ bài đã xác nhận còn mình chưa', async () => {
    await renderMessages(
      RECEIVER_ID,
      request({ status: 'AWAITING_HANDOVER', donorHandoverAt: '2026-10-25T10:00:00Z' }),
    );

    expect(await screen.findByRole('button', { name: 'Tôi chưa nhận được' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Tôi đã nhận được đồ' })).toBeTruthy();
  });

  it('chủ bài thấy nút Tôi chưa giao được khi người nhận đã xác nhận còn mình chưa', async () => {
    await renderMessages(
      DONOR_ID,
      request({ status: 'AWAITING_HANDOVER', receiverHandoverAt: '2026-10-25T10:00:00Z' }),
    );

    expect(await screen.findByRole('button', { name: 'Tôi chưa giao được' })).toBeTruthy();
  });

  it('không hiện nút khi bên kia chưa xác nhận hoặc mình đã xác nhận', async () => {
    await renderMessages(RECEIVER_ID, request({ status: 'AWAITING_HANDOVER' }));
    await screen.findByRole('button', { name: 'Tôi đã nhận được đồ' });
    expect(screen.queryByRole('button', { name: 'Tôi chưa nhận được' })).toBeNull();
  });

  it('gửi ghi chú và cập nhật giao dịch sang khiếu nại', async () => {
    const userEvents = userEvent.setup();
    vi.mocked(requestApi.reportHandoverIssue).mockResolvedValue(
      request({ status: 'DISPUTED', donorHandoverAt: '2026-10-25T10:00:00Z' }),
    );
    await renderMessages(
      RECEIVER_ID,
      request({ status: 'AWAITING_HANDOVER', donorHandoverAt: '2026-10-25T10:00:00Z' }),
    );

    await userEvents.click(await screen.findByRole('button', { name: 'Tôi chưa nhận được' }));
    const dialog = await screen.findByRole('dialog');
    await userEvents.type(within(dialog).getByLabelText(/Mô tả sự cố/), 'Chờ mãi không thấy ai giao');
    await userEvents.click(within(dialog).getByRole('button', { name: 'Gửi báo cáo' }));

    await waitFor(() =>
      expect(requestApi.reportHandoverIssue).toHaveBeenCalledWith(101, RECEIVER_ID, {
        note: 'Chờ mãi không thấy ai giao',
        evidenceMediaIds: undefined,
      }),
    );
    expect(await screen.findByText(/Giao dịch đang có khiếu nại/)).toBeTruthy();
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
