// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { actions, store } from '../../app/store';
import { chatApi } from '../../services/chatApi';
import { itemApi, type ItemResponse } from '../../services/itemApi';
import {
  requestApi,
  type RequestMediaResponse,
  type RequestResponse,
} from '../../services/requestApi';
import type { User } from '../../types/domain';
import { Messages } from './Messages';
import { TransactionProgress } from './TransactionProgress';

vi.mock('../../services/requestApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/requestApi')>()),
  requestApi: {
    mine: vi.fn(),
    forItem: vi.fn(),
    received: vi.fn(),
    get: vi.fn(),
    media: vi.fn(),
    cancelTransaction: vi.fn(),
    handoverFailed: vi.fn(),
    refuseHandover: vi.fn(),
    getContact: vi.fn(),
    confirmLogistics: vi.fn(),
    select: vi.fn(),
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
// Trang Tin nhắn tải lại ví sau khi chọn người; test không bọc WalletProvider nên mock hook.
vi.mock('../../app/useWallet', () => ({ useWallet: () => ({ refresh: vi.fn() }) }));

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
async function renderMessages(
  viewerId: number,
  current: RequestResponse,
  media: RequestMediaResponse[] = [],
  ownedItems: ItemResponse[] = viewerId === DONOR_ID ? [item()] : [],
) {
  store.dispatch(actions.loginFromApi(user(viewerId)));
  vi.mocked(requestApi.mine).mockResolvedValue(viewerId === RECEIVER_ID ? [current] : []);
  vi.mocked(itemApi.mine).mockResolvedValue(ownedItems);
  vi.mocked(requestApi.forItem).mockResolvedValue([current]);
  vi.mocked(requestApi.received).mockResolvedValue([current]);
  vi.mocked(requestApi.get).mockResolvedValue(current);
  vi.mocked(requestApi.media).mockResolvedValue(media);
  vi.mocked(itemApi.detail).mockResolvedValue(item());
  vi.mocked(chatApi.list).mockResolvedValue([]);
  vi.mocked(chatApi.pinnedItems).mockResolvedValue([]);
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/requests/101']}>
        <Routes>
          <Route path="/requests/:requestId" element={<Messages />} />
          <Route path="/messages/:conversationId/progress" element={<TransactionProgress />} />
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

  it.each([
    { viewerId: RECEIVER_ID, senderId: DONOR_ID, side: 'left', actor: 'An Chủ Bài' },
    { viewerId: RECEIVER_ID, senderId: RECEIVER_ID, side: 'right', actor: 'Bạn' },
    { viewerId: DONOR_ID, senderId: RECEIVER_ID, side: 'left', actor: 'Bình Người Nhận' },
    { viewerId: DONOR_ID, senderId: DONOR_ID, side: 'right', actor: 'Bạn' },
  ])('canh thẻ lịch hẹn sang $side cho người xem $viewerId khi người lưu là $senderId', async ({ viewerId, senderId, side, actor }) => {
    vi.mocked(chatApi.list).mockResolvedValueOnce([{
      id: 1,
      requestId: 101,
      senderId,
      kind: 'SCHEDULE_SET',
      content: 'Lịch hẹn đã được lưu',
      mediaId: null,
      refItemId: null,
      itemCard: null,
      offerStatus: null,
      moderation: 'PASS',
      blockedReason: null,
      createdAt: '2026-10-20T10:00:00Z',
    }]);
    await renderMessages(viewerId, request({
      status: 'AWAITING_LOGISTICS',
      contactRevealedAt: undefined,
      deliveryMethod: 'IN_PERSON',
      meetingTime: new Date(Date.now() + 86_400_000).toISOString(),
      meetingPlace: 'Cổng trường',
    }));

    const card = await screen.findByRole('link', { name: /Xem lịch hẹn/ });
    expect(within(card).getByText(`${actor} đã lưu lịch hẹn`)).toBeTruthy();
    expect(card.closest('[data-message-align]')?.getAttribute('data-message-align')).toBe(side);
    expect(card.getAttribute('href')).toBe('/messages/101/progress#lich-giao-nhan');
    await userEvent.setup().click(card);

    const scheduleHeading = await screen.findByRole('heading', { name: 'Lịch giao nhận' });
    await waitFor(() => expect(document.activeElement).toBe(scheduleHeading));
    expect(Element.prototype.scrollIntoView).toHaveBeenCalledWith({ block: 'start' });
    expect(screen.getByRole('button', { name: 'Xác nhận lịch' })).toBeTruthy();
    expect(requestApi.confirmLogistics).not.toHaveBeenCalled();
  });
});

describe('Modal chọn người cho yêu cầu SWAP', () => {
  it('xem đúng món đề xuất mà không tự xác nhận giao dịch', async () => {
    const userEvents = userEvent.setup();
    await renderMessages(
      DONOR_ID,
      request({
        type: 'SWAP',
        status: 'PENDING',
        offeredItemId: 78,
        offeredItemTitle: 'Máy xay cà phê',
      }),
    );

    await userEvents.click(screen.getByRole('button', { name: 'Chọn người này' }));
    const dialog = await screen.findByRole('dialog', { name: 'Xác nhận chọn người giao dịch' });
    const offeredCard = within(dialog).getByRole('article', { name: 'Món đề xuất' });
    await userEvents.click(within(offeredCard).getByRole('button', { name: 'Xem chi tiết' }));

    expect(screen.getByTestId('location').textContent).toBe(
      '/items/78?from=messages&requestId=101&selectRequest=101',
    );
    expect(requestApi.select).not.toHaveBeenCalled();
  });

  it('bài đang chờ duyệt lại thì hiện đúng câu lỗi và khoá nút xác nhận', async () => {
    // given: backend từ chối vì bài vừa sửa đang chờ duyệt lại
    const userEvents = userEvent.setup();
    await renderMessages(DONOR_ID, request({ status: 'PENDING', contactRevealedAt: undefined }));
    vi.mocked(requestApi.select).mockRejectedValue({
      status: 409,
      code: 'REQUEST_ITEM_UNDER_REVIEW',
      message: 'Lỗi',
    });

    // when
    await userEvents.click(screen.getByRole('button', { name: 'Chọn người này' }));
    const dialog = await screen.findByRole('dialog', { name: 'Xác nhận chọn người giao dịch' });
    await userEvents.click(within(dialog).getByRole('button', { name: 'Xác nhận chọn' }));

    // then
    expect(
      await within(dialog).findByText(
        'Bài đang chờ duyệt lại sau khi sửa, bạn chọn người sau khi bài được duyệt.',
      ),
    ).toBeTruthy();
    expect(
      (within(dialog).getByRole('button', { name: 'Xác nhận chọn' }) as HTMLButtonElement).disabled,
    ).toBe(true);
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

describe('Chia sẻ bài trong tin nhắn', () => {
  it('hiện nút mờ và lý do khi người nhận đồ Cho tặng không được chia sẻ', async () => {
    await renderMessages(RECEIVER_ID, request({ status: 'RESERVED' }));
    const share = screen.getByRole('button', { name: 'Chia sẻ bài' }) as HTMLButtonElement;
    expect(share.disabled).toBe(true);
    expect(screen.getByText('Người nhận đồ Cho tặng không thể chia sẻ bài đăng.')).toBeTruthy();
  });

  it('hiện nút mờ khi không có bài đã duyệt', async () => {
    await renderMessages(DONOR_ID, request({ status: 'RESERVED' }));
    const share = screen.getByRole('button', { name: 'Chia sẻ bài' }) as HTMLButtonElement;
    expect(share.disabled).toBe(true);
    expect(screen.getByText('Bạn chưa có bài đã duyệt để chia sẻ.')).toBeTruthy();
  });

  it('mở danh sách bài đã duyệt từ nút cạnh Gửi ảnh', async () => {
    const events = userEvent.setup();
    await renderMessages(
      DONOR_ID,
      request({ status: 'RESERVED' }),
      [],
      [item({ id: 46, status: 'APPROVED', title: 'Đèn bàn' })],
    );
    const share = screen.getByRole('button', { name: 'Chia sẻ bài' }) as HTMLButtonElement;
    expect(share.disabled).toBe(false);
    await events.click(share);
    expect(screen.getByRole('combobox', { name: 'Chia sẻ bài của bạn' })).toBeTruthy();
    expect(screen.getByRole('option', { name: 'Kèm bài: Đèn bàn' })).toBeTruthy();
  });

  it('báo bài đã gỡ khi backend từ chối thẻ chia sẻ', async () => {
    const events = userEvent.setup();
    vi.mocked(chatApi.send).mockRejectedValue({ code: 'CHAT_ITEM_NOT_APPROVED', status: 400, message: 'raw' });
    await renderMessages(
      DONOR_ID,
      request({ status: 'RESERVED' }),
      [],
      [item({ id: 46, status: 'APPROVED', title: 'Đèn bàn' })],
    );
    await events.click(screen.getByRole('button', { name: 'Chia sẻ bài' }));
    await events.selectOptions(screen.getByRole('combobox', { name: 'Chia sẻ bài của bạn' }), '46');
    await events.click(screen.getByRole('button', { name: 'Gửi tin nhắn' }));

    expect(await screen.findByText('Bài đăng này không còn khả dụng để chia sẻ. Vui lòng chọn bài khác.')).toBeTruthy();
    expect(chatApi.send).toHaveBeenCalledWith(101, { content: null, mediaId: null, refItemId: 46 });
  });

  it('khóa ô nhập và nút chia sẻ khi giao dịch đã huỷ', async () => {
    await renderMessages(DONOR_ID, request({ status: 'CANCELLED' }));
    expect((screen.getByRole('textbox', { name: 'Tin nhắn' }) as HTMLInputElement).disabled).toBe(true);
    expect((screen.getByRole('button', { name: 'Chia sẻ bài' }) as HTMLButtonElement).disabled).toBe(true);
  });
});

describe('Yêu cầu theo từng trạng thái chưa mở chat', () => {
  it('yêu cầu không được chọn: báo đúng câu, không có ô soạn tin, vẫn xem được chi tiết bài', async () => {
    // when
    await renderMessages(RECEIVER_ID, request({ status: 'REJECTED', contactRevealedAt: undefined }));

    // then
    expect(
      screen.getByText(
        'Chủ bài đã chọn người khác cho món này. Bạn vẫn xem lại được yêu cầu của mình.',
      ),
    ).toBeTruthy();
    expect(
      screen.getByText('Yêu cầu không được chọn nên cuộc trò chuyện không mở.'),
    ).toBeTruthy();
    expect(screen.queryByText('Cuộc trò chuyện mở khi chủ bài chọn yêu cầu này.')).toBeNull();
    expect(screen.queryByRole('textbox', { name: 'Tin nhắn' })).toBeNull();
    expect(screen.getByText('Bạn đã xin nhận')).toBeTruthy();
  });

  it('yêu cầu đang chờ giữ câu cũ và ô soạn tin bị khoá', async () => {
    // when
    await renderMessages(RECEIVER_ID, request({ status: 'PENDING', contactRevealedAt: undefined }));

    // then
    expect(screen.getByText('Cuộc trò chuyện mở khi chủ bài chọn yêu cầu này.')).toBeTruthy();
    expect((screen.getByRole('textbox', { name: 'Tin nhắn' }) as HTMLInputElement).disabled).toBe(true);
    expect(screen.queryByText(/Chủ bài đã chọn người khác/)).toBeNull();
  });

  it('giao dịch đã huỷ giữ câu Giao dịch đã bị huỷ', async () => {
    // when
    await renderMessages(RECEIVER_ID, request({ status: 'CANCELLED' }));

    // then
    expect(screen.getByText('Giao dịch đã bị huỷ.')).toBeTruthy();
    expect(screen.queryByText(/Chủ bài đã chọn người khác/)).toBeNull();
  });
});

describe('Khung chat gọn', () => {
  it('SWAP chỉ hiện đúng hai món ở đầu khung chat và không lặp món đề xuất bên dưới', async () => {
    // given: API thẻ ghim trả cả bài đăng gốc và món đề xuất
    const current = request({
      type: 'SWAP',
      offeredItemId: 77,
      offeredItemTitle: 'Mũ bảo hiểm',
    });
    vi.mocked(chatApi.pinnedItems).mockResolvedValue([
      {
        id: 45,
        title: 'Xe đạp cũ',
        offerType: 'GIVE',
        status: 'RESERVED',
        imageUrl: null,
        donorId: DONOR_ID,
      },
      {
        id: 77,
        title: 'Mũ bảo hiểm',
        offerType: 'SWAP',
        status: 'RESERVED',
        imageUrl: '/api/v1/media/77',
        donorId: RECEIVER_ID,
      },
    ]);

    // when
    store.dispatch(actions.loginFromApi(user(RECEIVER_ID)));
    vi.mocked(requestApi.mine).mockResolvedValue([current]);
    vi.mocked(itemApi.mine).mockResolvedValue([]);
    vi.mocked(requestApi.forItem).mockResolvedValue([current]);
    vi.mocked(requestApi.received).mockResolvedValue([current]);
    vi.mocked(requestApi.get).mockResolvedValue(current);
    vi.mocked(requestApi.media).mockResolvedValue([]);
    vi.mocked(itemApi.detail).mockImplementation((itemId) =>
      itemId === 45 ? Promise.resolve(item()) : Promise.reject(new Error('Item unavailable')),
    );
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
    expect(within(chat).getAllByText('Mũ bảo hiểm')).toHaveLength(1);
    expect(within(chat).getByRole('img', { name: 'Mũ bảo hiểm' }).getAttribute('src'))
      .toContain('/api/v1/media/77');
    expect(within(chat).getAllByRole('button', { name: 'Xem chi tiết' })).toHaveLength(2);
    expect(within(chat).getByRole('button', { name: 'Xem tiến trình' })).toBeTruthy();
    expect(within(chat).getByRole('button', { name: 'Xem hồ sơ' })).toBeTruthy();
  });
});

describe('Banner khiếu nại trong khung chat', () => {
  it('bên bị khiếu nại thấy banner có nút Giải trình ngay trên ô soạn tin', async () => {
    // when
    await renderMessages(
      DONOR_ID,
      request({
        status: 'DISPUTED',
        disputes: [
          {
            id: 7,
            reason: 'NOT_AS_DESCRIBED',
            openedByMe: false,
            status: 'AWAITING_RESPONSE',
            responseDueAt: '2099-01-01T03:00:00Z',
            respondedAt: null,
            canRespond: true,
          },
        ],
      }),
    );

    // then
    expect(await screen.findByRole('button', { name: 'Giải trình ngay' })).toBeTruthy();
    expect(screen.getByText(/đã khiếu nại giao dịch này: Không đúng mô tả/)).toBeTruthy();
  });

  it('bên khiếu nại chỉ thấy banner thông tin', async () => {
    // when
    await renderMessages(
      RECEIVER_ID,
      request({
        status: 'DISPUTED',
        disputes: [
          {
            id: 7,
            reason: 'NOT_AS_DESCRIBED',
            openedByMe: true,
            status: 'AWAITING_RESPONSE',
            responseDueAt: '2099-01-01T03:00:00Z',
            respondedAt: null,
            canRespond: false,
          },
        ],
      }),
    );

    // then
    expect(await screen.findByText(/Bạn đã khiếu nại giao dịch này: Không đúng mô tả/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Giải trình ngay' })).toBeNull();
  });
});
