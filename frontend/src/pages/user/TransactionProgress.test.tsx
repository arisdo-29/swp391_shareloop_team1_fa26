// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { actions, store } from '../../app/store';
import { itemApi } from '../../services/itemApi';
import { mediaApi } from '../../services/mediaApi';
import { subscribeRealtime, type RealtimeUpdate } from '../../services/realtime';
import { requestApi, type RequestDispute, type RequestResponse } from '../../services/requestApi';
import type { ItemRequestStatus, User } from '../../types/domain';
import { respondDispute } from '../../services/reportApi';
import { TransactionProgress } from './TransactionProgress';

// Ảnh riêng tư tải bằng token: ở test này chỉ cần hook trả blob URL có sẵn.
vi.mock('../../utils/usePrivateMediaUrl', () => ({
  usePrivateMediaUrl: () => ({ url: 'blob:test-image', loading: false, failed: false }),
}));

vi.mock('../../services/requestApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/requestApi')>()),
  requestApi: {
    get: vi.fn(),
    forItem: vi.fn(),
    cancel: vi.fn(),
    select: vi.fn(),
    cancelTransaction: vi.fn(),
    handoverFailed: vi.fn(),
    refuseHandover: vi.fn(),
    getContact: vi.fn(),
    updateSchedule: vi.fn(),
    confirmHandover: vi.fn(),
    attachMedia: vi.fn(),
  },
}));
vi.mock('../../services/itemApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/itemApi')>()),
  itemApi: { detail: vi.fn() },
}));
vi.mock('../../services/mediaApi', () => ({ mediaApi: { upload: vi.fn() } }));
vi.mock('../../services/realtime', () => ({ subscribeRealtime: vi.fn(() => () => undefined) }));
vi.mock('../../services/reportApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/reportApi')>()),
  respondDispute: vi.fn(),
}));

const DONOR_ID = 9;
const RECEIVER_ID = 12;
const OUTSIDER_ID = 30;

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

const request = (overrides: Partial<RequestResponse> = {}): RequestResponse => ({
  id: 101,
  itemId: 45,
  receiverId: RECEIVER_ID,
  type: 'GIVE',
  status: 'LOGISTICS_CONFIRMED',
  contactRevealedAt: '2026-10-21T08:00:00Z',
  meetingTime: '2026-10-31T11:30:00Z',
  meetingPlace: 'Cổng số 2 chợ Bà Chiểu',
  createdAt: '2026-10-20T09:00:00Z',
  itemTitle: 'Xe đạp cũ',
  donorId: DONOR_ID,
  donorName: 'An Chủ Bài',
  receiverName: 'Bình Người Nhận',
  disputeExpired: false,
  handoverMedia: [],
  ...overrides,
});

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location">{`${location.pathname}${location.search}`}</span>;
}

/** Dựng trang Tiến trình cho người xem `viewerId`, chi tiết giao dịch là `current`. */
async function renderProgress(viewerId: number, current: RequestResponse, path = '/messages/101/progress') {
  store.dispatch(actions.loginFromApi(user(viewerId)));
  vi.mocked(requestApi.get).mockResolvedValue(current);
  vi.mocked(requestApi.forItem).mockResolvedValue([current]);
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/messages/:conversationId/progress" element={<TransactionProgress />} />
          <Route path="/transactions/:conversationId" element={<TransactionProgress fromTransactions />} />
          <Route path="*" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
  await screen.findByRole('heading', { level: 1, name: 'Chi tiết giao dịch' });
}

beforeEach(() => {
  vi.stubGlobal('scrollTo', vi.fn());
  // jsdom chưa có object URL; ảnh chờ gửi cần URL khác nhau cho mỗi tệp.
  let objectUrlCount = 0;
  URL.createObjectURL = vi.fn(() => `blob:preview-${(objectUrlCount += 1)}`);
  URL.revokeObjectURL = vi.fn();
});

afterEach(() => {
  cleanup();
  store.dispatch(actions.logout());
  vi.clearAllMocks();
  vi.unstubAllGlobals();
});

describe('Cảnh báo lịch giao nhận theo thời gian thực', () => {
  const notice = 'Thông tin lịch giao nhận vừa được cập nhật. Vui lòng kiểm tra lại.';
  const pendingSchedule = () => request({
    status: 'AWAITING_LOGISTICS',
    contactRevealedAt: undefined,
    deliveryMethod: 'IN_PERSON',
    meetingTime: new Date(Date.now() + 86_400_000).toISOString(),
    scheduleRevision: 1,
  });
  const emitUpdate = (kind: 'READY' | 'REQUEST' = 'REQUEST') => {
    const listener = vi.mocked(subscribeRealtime).mock.lastCall?.[0];
    const update: RealtimeUpdate = { kind, resourceId: kind === 'READY' ? null : 101, userId: RECEIVER_ID };
    act(() => listener?.(update));
  };

  it('đổi địa điểm và báo ngay trong khung lịch đang mở', async () => {
    const current = pendingSchedule();
    await renderProgress(RECEIVER_ID, current);
    const schedule = within(screen.getByRole('region', { name: 'Lịch giao nhận' }));
    expect(schedule.queryByText(notice)).toBeNull();

    vi.mocked(requestApi.get).mockResolvedValue({
      ...current, scheduleRevision: 2, meetingTime: new Date(Date.now() + 90_000_000).toISOString(),
      meetingPlace: 'Cổng thư viện',
    });
    emitUpdate();

    expect((await schedule.findByRole('alert')).textContent).toBe(notice);
    expect(schedule.getByDisplayValue('Cổng thư viện')).toBeTruthy();
    expect(schedule.queryByDisplayValue('Cổng số 2 chợ Bà Chiểu')).toBeNull();
  });

  it('báo khi địa chỉ được sửa trong lúc mất kết nối, kể cả khi địa chỉ vẫn được ẩn', async () => {
    const current: RequestResponse = {
      ...pendingSchedule(),
      deliveryMethod: 'SHIPPING',
      meetingPlace: undefined,
      donorAddressProvided: true,
    };
    await renderProgress(RECEIVER_ID, current);
    const schedule = within(screen.getByRole('region', { name: 'Lịch giao nhận' }));
    expect(schedule.queryByText(notice)).toBeNull();

    vi.mocked(requestApi.get).mockResolvedValue({ ...current, scheduleRevision: 2 });
    emitUpdate('READY');

    expect((await schedule.findByRole('alert')).textContent).toBe(notice);
  });

  it('bỏ qua cập nhật giao dịch không làm đổi lịch', async () => {
    const current = request({ scheduleRevision: 1 });
    await renderProgress(RECEIVER_ID, current);
    vi.mocked(requestApi.get).mockResolvedValue({ ...current, itemTitle: 'Xe đạp đã đổi tên' });
    emitUpdate();
    await screen.findAllByText('Xe đạp đã đổi tên');
    expect(within(screen.getByRole('region', { name: 'Lịch giao nhận' })).queryByText(notice)).toBeNull();
  });

  it('cập nhật lịch do chính mình gửi lại mà không tự cảnh báo', async () => {
    const current = pendingSchedule();
    const updated = { ...current, meetingPlace: 'Sảnh thư viện', scheduleRevision: 2 };
    vi.mocked(requestApi.updateSchedule).mockResolvedValue(updated);
    await renderProgress(RECEIVER_ID, current);
    const schedule = within(screen.getByRole('region', { name: 'Lịch giao nhận' }));

    fireEvent.change(schedule.getByRole('textbox', { name: 'Địa điểm gặp' }), {
      target: { value: updated.meetingPlace },
    });
    fireEvent.click(schedule.getByRole('checkbox', { name: /cam kết trách nhiệm/ }));
    fireEvent.click(schedule.getByRole('button', { name: 'Gửi lại lịch hẹn' }));
    await waitFor(() => expect(requestApi.updateSchedule).toHaveBeenCalledTimes(1));
    vi.mocked(requestApi.get).mockResolvedValue({ ...updated, itemTitle: 'Xe đạp vừa cập nhật' });
    emitUpdate();
    await screen.findAllByText('Xe đạp vừa cập nhật');

    expect(schedule.queryByText(notice)).toBeNull();
  });
});

describe('Bảo vệ trang Tiến trình', () => {
  it.each([403, 404])('người ngoài giao dịch (backend trả %i) thấy EmptyState, không có thao tác', async (status) => {
    // given
    store.dispatch(actions.loginFromApi(user(OUTSIDER_ID)));
    vi.mocked(requestApi.get).mockRejectedValue({ status, code: 'REQUEST_FORBIDDEN', message: '' });

    // when
    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/messages/101/progress']}>
          <Routes>
            <Route path="/messages/:conversationId/progress" element={<TransactionProgress />} />
          </Routes>
        </MemoryRouter>
      </Provider>,
    );

    // then
    expect(await screen.findByText('Không xem được giao dịch này')).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'Các bước' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Hủy giao dịch' })).toBeNull();
  });

  it('gọi API chi tiết đúng id trên đường dẫn và người đang đăng nhập', async () => {
    // when
    await renderProgress(RECEIVER_ID, request());

    // then
    expect(requestApi.get).toHaveBeenCalledWith(101, RECEIVER_ID);
  });
});

describe('Các bước theo trạng thái', () => {
  it.each<[ItemRequestStatus, number]>([
    ['RESERVED', 1],
    ['AWAITING_LOGISTICS', 1],
    ['LOGISTICS_CONFIRMED', 2],
    ['AWAITING_HANDOVER', 2],
    ['COMPLETED', 4],
    ['DISPUTED', 4],
  ])('%s hiện %i trên 4 bước đã xong', async (status, done) => {
    // when
    await renderProgress(RECEIVER_ID, request({ status }));

    // then
    expect(screen.getByLabelText(`Đã xong ${done} trên 4 bước`)).toBeTruthy();
  });

  it.each<ItemRequestStatus>(['PENDING', 'REJECTED', 'CANCELLED'])(
    '%s chưa vào giao dịch nên không vẽ 4 bước',
    async (status) => {
      // when
      await renderProgress(RECEIVER_ID, request({ status }));

      // then
      expect(screen.queryByLabelText(/trên 4 bước/)).toBeNull();
    },
  );

  it('hoàn tất: hiện giờ hẹn, giờ hoàn tất và thông báo kết quả', async () => {
    // when
    await renderProgress(
      RECEIVER_ID,
      request({ status: 'COMPLETED', completedAt: '2026-10-30T03:36:00Z', disputeExpired: true }),
    );

    // then
    expect(screen.getByText('Hẹn 18:30 31/10/2026')).toBeTruthy();
    expect(screen.getByText('10:36 30/10/2026')).toBeTruthy();
    expect(screen.getByText(/Giao dịch đã hoàn tất lúc 10:36 30\/10\/2026/)).toBeTruthy();
  });

  it('không được chọn và đã hủy hiện Alert kèm lý do', async () => {
    // when
    await renderProgress(
      RECEIVER_ID,
      request({ status: 'CANCELLED', cancelReason: '[Chủ bài] Món đã hỏng' }),
    );

    // then
    expect(screen.getByText(/Lý do: \[Chủ bài\] Món đã hỏng/)).toBeTruthy();
  });

  it('không được chọn hiện thông báo chủ bài chọn người khác', async () => {
    // when
    await renderProgress(RECEIVER_ID, request({ status: 'REJECTED' }));

    // then
    expect(screen.getByText('Chủ bài đã chọn người khác cho món này.')).toBeTruthy();
  });
});

describe('Khiếu nại dùng cờ disputeExpired của backend', () => {
  it('đã hết hạn thì hiện dòng Đã hết hạn khiếu nại và ẩn nút khiếu nại', async () => {
    // given: complaintDeadline còn ở tương lai nhưng backend nói đã hết hạn, trang theo backend
    await renderProgress(
      RECEIVER_ID,
      request({
        status: 'COMPLETED',
        disputeExpired: true,
        complaintDeadline: '2099-01-01T00:00:00Z',
      }),
    );

    // then
    expect(screen.getByText('Đã hết hạn khiếu nại')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Khiếu nại giao dịch' })).toBeNull();
  });

  it('còn hạn thì hiện nút Khiếu nại giao dịch', async () => {
    // given: complaintDeadline đã qua nhưng backend nói còn hạn, trang theo backend
    await renderProgress(
      RECEIVER_ID,
      request({
        status: 'COMPLETED',
        disputeExpired: false,
        complaintDeadline: '2026-10-31T07:30:00Z',
      }),
    );

    // then
    expect(screen.getByRole('button', { name: 'Khiếu nại giao dịch' })).toBeTruthy();
  });
});

describe('Nút theo trạng thái và vai', () => {
  it('đang chờ chọn: người xin thấy Hủy yêu cầu, không thấy Chọn người này', async () => {
    // when
    await renderProgress(RECEIVER_ID, request({ status: 'PENDING', contactRevealedAt: undefined }));

    // then
    expect(screen.getByRole('button', { name: 'Hủy yêu cầu' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Chọn người này' })).toBeNull();
  });

  it('đang chờ chọn: chủ bài thấy Chọn người này khi bài chưa có người được chọn', async () => {
    // when
    await renderProgress(DONOR_ID, request({ status: 'PENDING', contactRevealedAt: undefined }));

    // then
    expect(await screen.findByRole('button', { name: 'Chọn người này' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Hủy yêu cầu' })).toBeNull();
  });

  it('chờ chốt lịch: chỉ hiện nút đồng ý, chưa có số điện thoại', async () => {
    // when
    await renderProgress(
      RECEIVER_ID,
      request({ status: 'AWAITING_LOGISTICS', contactRevealedAt: undefined, deliveryMethod: 'IN_PERSON' }),
    );

    // then
    expect(screen.queryByRole('button', { name: 'Gửi lịch hẹn' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Đồng ý lịch hẹn này' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Xem số điện thoại' })).toBeNull();
  });

  it('đã chốt lịch: hiện thời gian, địa điểm và Xem số điện thoại gọi API liên lạc', async () => {
    // given
    const userEvents = userEvent.setup();
    vi.mocked(requestApi.getContact).mockResolvedValue({
      userId: DONOR_ID,
      fullName: 'An Chủ Bài',
      email: 'an@example.com',
      phone: '0901234567',
    });
    await renderProgress(RECEIVER_ID, request());

    // when
    await userEvents.click(screen.getByRole('button', { name: 'Xem số điện thoại' }));

    // then
    expect(screen.getByText('Cổng số 2 chợ Bà Chiểu')).toBeTruthy();
    expect(await screen.findByText('0901234567')).toBeTruthy();
    expect(requestApi.getContact).toHaveBeenCalledWith(101, RECEIVER_ID);
  });

  it('thẻ đối tác ghi vai thật và có Nhắn tin, Xem hồ sơ, Báo cáo', async () => {
    // given
    const userEvents = userEvent.setup();
    await renderProgress(RECEIVER_ID, request());

    // then
    expect(screen.getByRole('heading', { name: 'Người cho tặng' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Nhắn tin' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Báo cáo' })).toBeTruthy();

    // when
    await userEvents.click(screen.getByRole('button', { name: 'Xem hồ sơ' }));

    // then
    expect(screen.getByTestId('location').textContent).toBe(`/users/${DONOR_ID}`);
  });

  it('Quay lại tin nhắn về đúng cuộc trò chuyện', async () => {
    // given
    const userEvents = userEvent.setup();
    await renderProgress(RECEIVER_ID, request());

    // when
    await userEvents.click(screen.getByRole('button', { name: 'Quay lại tin nhắn' }));

    // then
    expect(screen.getByTestId('location').textContent).toBe('/requests/101');
  });

  it('trở về danh sách khi mở chi tiết từ mục Giao dịch', async () => {
    const userEvents = userEvent.setup();
    await renderProgress(RECEIVER_ID, request(), '/transactions/101?status=active&q=xe');

    expect(requestApi.get).toHaveBeenCalledWith(101, RECEIVER_ID);
    await userEvents.click(screen.getByRole('button', { name: 'Về danh sách giao dịch' }));
    expect(screen.getByTestId('location').textContent).toBe('/transactions?status=active&q=xe');
  });

  it('mở bài đăng kèm nguồn từ trang tin nhắn', async () => {
    // given
    const userEvents = userEvent.setup();
    await renderProgress(RECEIVER_ID, request());

    // when
    await userEvents.click(screen.getByRole('button', { name: 'Xem bài đăng' }));

    // then
    expect(screen.getByTestId('location').textContent).toBe('/items/45?from=messages');
  });
});

describe('Ảnh giao nhận', () => {
  it('hiện thumbnail 100px và mở ảnh lớn trong modal', async () => {
    const userEvents = userEvent.setup();
    // when
    await renderProgress(
      RECEIVER_ID,
      request({
        handoverMedia: [
          {
            mediaId: 7,
            url: '/api/v1/media/7',
            stage: 'HANDOVER',
            uploadedBy: DONOR_ID,
            createdAt: '2026-10-31T11:40:00Z',
          },
        ],
      }),
    );

    // then
    expect(screen.getByText('1 ảnh')).toBeTruthy();
    expect(screen.getByText('Giao nhận 18:40 31/10/2026')).toBeTruthy();
    const thumbnail = screen.getByRole('img', { name: 'Ảnh giao nhận' });
    expect(thumbnail.className).toContain('size-[100px]');
    expect(thumbnail.className).toContain('object-cover');
    expect(screen.queryByRole('button', { name: /Xóa ảnh/ })).toBeNull();

    await userEvents.click(screen.getByRole('button', { name: 'Xem ảnh giao nhận' }));
    const dialog = screen.getByRole('dialog', { name: 'Xem ảnh giao nhận' });
    expect(within(dialog).getByRole('img', { name: 'Ảnh giao nhận kích thước lớn' }))
      .toBeTruthy();
    await userEvents.click(within(dialog).getByRole('button', { name: 'Đóng' }));
    expect(screen.queryByRole('dialog', { name: 'Xem ảnh giao nhận' })).toBeNull();
  });

  it('không hiển thị thao tác xóa ảnh khi hệ thống chưa hỗ trợ', async () => {
    await renderProgress(
      RECEIVER_ID,
      request({
        handoverMedia: [
          {
            mediaId: 8,
            url: '/api/v1/media/8',
            stage: 'HANDOVER',
            uploadedBy: RECEIVER_ID,
            createdAt: '2026-10-31T11:40:00Z',
          },
        ],
      }),
    );

    expect(screen.queryByRole('button', { name: 'Xóa ảnh' })).toBeNull();
    expect(screen.queryByText(/Backend|API|endpoint/i)).toBeNull();
    expect(screen.getByText('1 ảnh')).toBeTruthy();
    expect(requestApi.attachMedia).not.toHaveBeenCalled();
  });

  it('chưa có ảnh thì hiện trạng thái rỗng và Gửi ảnh bị khoá', async () => {
    // when
    await renderProgress(RECEIVER_ID, request());

    // then
    expect(screen.getByText('Chưa có ảnh giao nhận')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Gửi ảnh' }) as HTMLButtonElement).disabled).toBe(
      true,
    );
  });

  it('khóa thao tác và hiện trạng thái đang tải trong lúc gửi ảnh', async () => {
    // given
    const userEvents = userEvent.setup();
    let resolveUpload!: (value: { mediaId: number; url: string }) => void;
    vi.mocked(mediaApi.upload).mockReturnValue(
      new Promise((resolve) => {
        resolveUpload = resolve;
      }),
    );
    vi.mocked(requestApi.attachMedia).mockResolvedValue([]);
    await renderProgress(RECEIVER_ID, request());
    await userEvents.upload(
      screen.getByLabelText('Chọn ảnh giao nhận'),
      new File(['image'], 'handover.png', { type: 'image/png' }),
    );

    // when
    await userEvents.click(screen.getByRole('button', { name: 'Gửi 1 ảnh' }));
    await userEvents.click(screen.getByRole('button', { name: 'Gửi ảnh' }));

    // then
    expect(screen.getAllByRole('button', { name: 'Đang tải...' }).length).toBeGreaterThan(0);
    expect((screen.getByLabelText('Chọn ảnh giao nhận') as HTMLInputElement).disabled).toBe(true);
    resolveUpload({ mediaId: 9, url: '/api/v1/media/9' });
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Gửi ảnh' })).toBeTruthy();
    });
    expect(mediaApi.upload).toHaveBeenCalledTimes(1);
    expect(requestApi.attachMedia).toHaveBeenCalledWith(101, {
      mediaIds: [9],
      stage: 'HANDOVER',
    });
  });

  it('ảnh chờ gửi hiện để xem lại và bỏ được trước khi gửi', async () => {
    // given
    const userEvents = userEvent.setup();
    await renderProgress(RECEIVER_ID, request());
    await userEvents.upload(screen.getByLabelText('Chọn ảnh giao nhận'), [
      new File(['a'], 'mat-truoc.png', { type: 'image/png' }),
      new File(['b'], 'mat-sau.png', { type: 'image/png' }),
    ]);
    expect(screen.getByRole('img', { name: 'Ảnh chờ gửi mat-truoc.png' })).toBeTruthy();

    // when
    await userEvents.click(screen.getByRole('button', { name: 'Bỏ ảnh mat-truoc.png' }));

    // then
    expect(screen.queryByRole('img', { name: 'Ảnh chờ gửi mat-truoc.png' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Gửi 1 ảnh' })).toBeTruthy();
    expect(mediaApi.upload).not.toHaveBeenCalled();
  });

  it('bấm Gửi hiện cảnh báo không thu hồi được; Xem lại thì không gọi API', async () => {
    // given
    const userEvents = userEvent.setup();
    await renderProgress(RECEIVER_ID, request());
    await userEvents.upload(
      screen.getByLabelText('Chọn ảnh giao nhận'),
      new File(['a'], 'handover.png', { type: 'image/png' }),
    );

    // when
    await userEvents.click(screen.getByRole('button', { name: 'Gửi 1 ảnh' }));
    const dialog = screen.getByRole('dialog', { name: 'Gửi 1 ảnh giao nhận?' });
    expect(within(dialog).getByText(/không thể xoá hoặc thu hồi/)).toBeTruthy();
    await userEvents.click(within(dialog).getByRole('button', { name: 'Xem lại' }));

    // then
    expect(screen.queryByRole('dialog', { name: 'Gửi 1 ảnh giao nhận?' })).toBeNull();
    expect(screen.getByRole('img', { name: 'Ảnh chờ gửi handover.png' })).toBeTruthy();
    expect(mediaApi.upload).not.toHaveBeenCalled();
  });

  it('chưa chốt lịch thì không có khối ảnh', async () => {
    // when
    await renderProgress(
      RECEIVER_ID,
      request({ status: 'AWAITING_LOGISTICS', contactRevealedAt: undefined }),
    );

    // then
    expect(screen.queryByRole('heading', { name: 'Ảnh giao nhận' })).toBeNull();
  });
});

describe('Ảnh khiếu nại', () => {
  const disputePhoto = {
    mediaId: 12,
    url: '/api/v1/media/12',
    stage: 'DISPUTE' as const,
    uploadedBy: RECEIVER_ID,
    createdAt: '2026-10-31T11:40:00Z',
  };

  it('chưa hoàn tất thì chưa có mục ảnh khiếu nại', async () => {
    // when
    await renderProgress(RECEIVER_ID, request());

    // then
    expect(screen.queryByRole('heading', { name: 'Ảnh khiếu nại' })).toBeNull();
  });

  it('hoàn tất còn hạn: mục riêng ghi hạn chót và cho gửi ảnh khiếu nại', async () => {
    // when
    await renderProgress(
      RECEIVER_ID,
      request({
        status: 'COMPLETED',
        completedAt: '2026-10-30T03:36:00Z',
        complaintDeadline: '2026-10-31T03:36:00Z',
        disputeExpired: false,
      }),
    );

    // then
    expect(screen.getByRole('heading', { name: 'Ảnh khiếu nại' })).toBeTruthy();
    expect(screen.getByText(/Có thể bổ sung ảnh vào giao dịch trước 10:36 31\/10\/2026/)).toBeTruthy();
    expect(screen.getByLabelText('Chọn ảnh khiếu nại')).toBeTruthy();
  });

  it('hết hạn mà chưa gửi ảnh khiếu nại thì ẩn hẳn mục', async () => {
    // when
    await renderProgress(
      RECEIVER_ID,
      request({ status: 'COMPLETED', completedAt: '2026-10-30T03:36:00Z', disputeExpired: true }),
    );

    // then
    expect(screen.queryByRole('heading', { name: 'Ảnh khiếu nại' })).toBeNull();
  });

  it('hết hạn nhưng đã có ảnh khiếu nại thì vẫn xem được, không thêm được nữa', async () => {
    // when
    await renderProgress(
      RECEIVER_ID,
      request({
        status: 'COMPLETED',
        completedAt: '2026-10-30T03:36:00Z',
        disputeExpired: true,
        handoverMedia: [disputePhoto],
      }),
    );

    // then
    expect(screen.getByRole('heading', { name: 'Ảnh khiếu nại' })).toBeTruthy();
    expect(screen.getByRole('img', { name: 'Ảnh khiếu nại' })).toBeTruthy();
    expect(screen.getByText('Đã hết hạn khiếu nại, không thêm ảnh khiếu nại được nữa.')).toBeTruthy();
    expect(screen.queryByLabelText('Chọn ảnh khiếu nại')).toBeNull();
  });
});

// Chuyển từ Messages.transaction.test.tsx: các chức năng này nay nằm ở trang Tiến trình.
describe('Hủy giao dịch', () => {
  it.each(['RESERVED', 'AWAITING_LOGISTICS'] as const)(
    'hiện khối Hủy giao dịch và chú thích khi trạng thái %s',
    async (status) => {
      await renderProgress(RECEIVER_ID, request({ status }));

      expect(screen.getByRole('button', { name: 'Hủy giao dịch' })).toBeTruthy();
      expect(
        screen.getByText(
          'Cần nêu lý do. Bài đăng mở lại cho người khác, không mất phí đăng lần nữa.',
        ),
      ).toBeTruthy();
    },
  );

  it.each(['LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER', 'COMPLETED', 'DISPUTED', 'CANCELLED'] as const)(
    'không hiện khi trạng thái %s',
    async (status) => {
      await renderProgress(RECEIVER_ID, request({ status }));

      expect(screen.queryByRole('button', { name: 'Hủy giao dịch' })).toBeNull();
    },
  );

  it('bắt buộc lý do rồi gọi API và cập nhật trạng thái trả về', async () => {
    const userEvents = userEvent.setup();
    vi.mocked(requestApi.cancelTransaction).mockResolvedValue(
      request({ status: 'CANCELLED', cancelReason: '[Người nhận] Tôi bận việc đột xuất' }),
    );
    await renderProgress(RECEIVER_ID, request({ status: 'AWAITING_LOGISTICS' }));

    await userEvents.click(screen.getByRole('button', { name: 'Hủy giao dịch' }));
    const dialog = await screen.findByRole('dialog');
    const submit = within(dialog).getByRole('button', {
      name: 'Hủy giao dịch',
    }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);

    const reasonBox = within(dialog).getByLabelText(/Lý do hủy/);
    expect(reasonBox.getAttribute('maxlength')).toBe('500');
    await userEvents.type(reasonBox, 'Tôi bận việc đột xuất');
    expect(within(dialog).getByText('21/500')).toBeTruthy();
    await userEvents.click(submit);

    await waitFor(() =>
      expect(requestApi.cancelTransaction).toHaveBeenCalledWith(
        101,
        RECEIVER_ID,
        'Tôi bận việc đột xuất',
      ),
    );
    expect(await screen.findByText(/Lý do: \[Người nhận\] Tôi bận việc đột xuất/)).toBeTruthy();
  });
});

describe('Hạn giải trình khi giao dịch đang khiếu nại', () => {
  it('hiện hạn theo giờ Việt Nam khi backend trả disputeResponseDueAt', async () => {
    await renderProgress(
      RECEIVER_ID,
      request({ status: 'DISPUTED', disputeResponseDueAt: '2026-10-31T07:30:00Z' }),
    );

    expect(screen.getByText(/Hạn giải trình ghi nhận: 14:30 31\/10/)).toBeTruthy();
  });

  it('không hiện câu chờ giải trình khi deadline là null', async () => {
    await renderProgress(RECEIVER_ID, request({ status: 'DISPUTED', disputeResponseDueAt: null }));

    expect(screen.getByText(/Sự cố giao nhận, chờ Admin xác minh/)).toBeTruthy();
    expect(screen.queryByText(/Hạn giải trình ghi nhận/)).toBeNull();
  });
});

describe('Ba nút kết quả giao nhận', () => {
  const swapRequest = (overrides: Partial<RequestResponse> = {}) =>
    request({ type: 'SWAP', offeredItemId: 77, offeredItemTitle: 'Đàn guitar', ...overrides });

  it('người cho ở Cho tặng thấy hai nút, không có Từ chối giao nhận', async () => {
    // when
    await renderProgress(DONOR_ID, request());

    // then
    expect(screen.getByRole('button', { name: 'Đã giao nhận xong' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Giao nhận không thành công' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Từ chối giao nhận' })).toBeNull();
  });

  it('người nhận ở Cho tặng thấy đủ ba nút', async () => {
    // when
    await renderProgress(RECEIVER_ID, request());

    // then
    expect(screen.getByRole('button', { name: 'Đã giao nhận xong' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Giao nhận không thành công' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Từ chối giao nhận' })).toBeTruthy();
  });

  it.each([DONOR_ID, RECEIVER_ID])('Trao đổi: bên %i thấy đủ ba nút', async (viewerId) => {
    // given
    vi.mocked(itemApi.detail).mockRejectedValue(new Error('not needed'));

    // when
    await renderProgress(viewerId, swapRequest());

    // then
    expect(screen.getByRole('button', { name: 'Đã giao nhận xong' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Giao nhận không thành công' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Từ chối giao nhận' })).toBeTruthy();
  });

  it('không còn nút Hủy giao dịch sau khi chốt lịch', async () => {
    // when
    await renderProgress(RECEIVER_ID, request({ status: 'AWAITING_HANDOVER' }));

    // then
    expect(screen.queryByRole('button', { name: 'Hủy giao dịch' })).toBeNull();
  });

  it('ẩn cả ba nút khi mình đã bấm hoàn tất', async () => {
    // when
    await renderProgress(
      RECEIVER_ID,
      request({ status: 'AWAITING_HANDOVER', receiverHandoverAt: '2026-10-25T10:00:00Z' }),
    );

    // then
    expect(screen.getByRole('button', { name: 'Bạn đã xác nhận hoàn tất' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Giao nhận không thành công' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Từ chối giao nhận' })).toBeNull();
  });

  it('Đã giao nhận xong gọi API không kèm câu hỏi đúng mô tả', async () => {
    // given
    const userEvents = userEvent.setup();
    vi.mocked(requestApi.confirmHandover).mockResolvedValue(
      request({ status: 'AWAITING_HANDOVER', receiverHandoverAt: '2026-10-25T10:00:00Z' }),
    );
    await renderProgress(RECEIVER_ID, request());

    // when
    await userEvents.click(screen.getByRole('button', { name: 'Đã giao nhận xong' }));

    // then
    await waitFor(() => expect(requestApi.confirmHandover).toHaveBeenCalledWith(101, RECEIVER_ID, {}));
    expect(screen.queryByText(/đúng mô tả không/)).toBeNull();
  });

  it('Giao hàng: người gửi thấy nút Tôi đã gửi hàng, người nhận thấy dòng chờ bên kia gửi', async () => {
    // when: luật mới (GN-12) Giao hàng có thẻ riêng theo từng chiều
    await renderProgress(DONOR_ID, request({ deliveryMethod: 'SHIPPING' }));

    // then
    expect(screen.getByRole('button', { name: 'Tôi đã gửi hàng' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Đã giao nhận xong' })).toBeNull();
    cleanup();
    await renderProgress(RECEIVER_ID, request({ deliveryMethod: 'SHIPPING' }));
    expect(screen.queryByRole('button', { name: 'Tôi đã gửi hàng' })).toBeNull();
    expect(screen.getByText('Món bạn nhận')).toBeTruthy();
  });

  it('không thành công khi bên kia chưa bấm: hộp thoại báo đề nghị huỷ chờ bên kia và gọi handover-failed', async () => {
    // given
    const userEvents = userEvent.setup();
    vi.mocked(requestApi.handoverFailed).mockResolvedValue(request());
    await renderProgress(RECEIVER_ID, request());

    // when
    await userEvents.click(screen.getByRole('button', { name: 'Giao nhận không thành công' }));
    const dialog = within(await screen.findByRole('dialog'));
    expect(dialog.getByText(/Giao dịch chưa bị huỷ ngay/)).toBeTruthy();
    const submit = dialog.getByRole('button', { name: 'Gửi đề nghị huỷ' }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
    await userEvents.selectOptions(
      dialog.getByLabelText('Lý do giao nhận không thành công'),
      'COUNTERPART_NO_SHOW',
    );
    await userEvents.click(submit);

    // then
    await waitFor(() =>
      expect(requestApi.handoverFailed).toHaveBeenCalledWith(101, RECEIVER_ID, {
        code: 'COUNTERPART_NO_SHOW',
        reason: undefined,
        evidenceMediaIds: undefined,
      }),
    );
    expect(requestApi.cancelTransaction).not.toHaveBeenCalled();
    expect(await screen.findByText(/Đã gửi đề nghị huỷ. Đang chờ .+ phản hồi./)).toBeTruthy();
  });

  it('không thành công khi bên kia đã bấm: hộp thoại báo tranh chấp và nút Gửi khiếu nại', async () => {
    // given
    const userEvents = userEvent.setup();
    vi.mocked(requestApi.handoverFailed).mockResolvedValue(
      request({ status: 'DISPUTED', donorHandoverAt: '2026-10-25T10:00:00Z' }),
    );
    await renderProgress(
      RECEIVER_ID,
      request({ status: 'AWAITING_HANDOVER', donorHandoverAt: '2026-10-25T10:00:00Z' }),
    );

    // when
    await userEvents.click(screen.getByRole('button', { name: 'Giao nhận không thành công' }));
    const dialog = within(await screen.findByRole('dialog'));
    expect(dialog.getByText(/chuyển sang sự cố giao nhận, chờ Admin xác minh/)).toBeTruthy();
    await userEvents.selectOptions(dialog.getByLabelText('Lý do giao nhận không thành công'), 'OTHER');
    await userEvents.type(dialog.getByLabelText(/Mô tả sự cố/), 'Chờ mãi không thấy ai giao');
    await userEvents.click(dialog.getByRole('button', { name: 'Gửi khiếu nại' }));

    // then
    await waitFor(() =>
      expect(requestApi.handoverFailed).toHaveBeenCalledWith(101, RECEIVER_ID, {
        code: 'OTHER',
        reason: 'Chờ mãi không thấy ai giao',
        evidenceMediaIds: undefined,
      }),
    );
  });

  it('Giao hàng: chiều đã gửi mở khiếu nại với lý do chưa nhận được hàng', async () => {
    // given: chiều 1 đã gửi nên người nhận báo được
    const userEvents = userEvent.setup();
    await renderProgress(
      RECEIVER_ID,
      request({
        deliveryMethod: 'SHIPPING',
        status: 'AWAITING_HANDOVER',
        donorShippedAt: '2026-10-25T08:00:00Z',
      }),
    );

    // when
    await userEvents.click(screen.getByRole('button', { name: 'Giao nhận không thành công' }));
    const dialog = within(await screen.findByRole('dialog'));

    // then
    expect(dialog.getByRole('option', { name: 'Chưa nhận được hàng' })).toBeTruthy();
    expect(dialog.getByRole('button', { name: 'Gửi khiếu nại' })).toBeTruthy();
    expect(dialog.queryByRole('option', { name: 'Bên kia chưa gửi hàng' })).toBeNull();
    expect(dialog.queryByRole('option', { name: 'Bên kia không đến điểm hẹn' })).toBeNull();
  });

  it('Từ chối giao nhận mở hộp thoại Báo cáo vi phạm, bắt buộc mô tả rồi gọi refuse-handover', async () => {
    // given
    const userEvents = userEvent.setup();
    vi.mocked(requestApi.refuseHandover).mockResolvedValue(request({ status: 'CANCELLED' }));
    await renderProgress(RECEIVER_ID, request());

    // when
    await userEvents.click(screen.getByRole('button', { name: 'Từ chối giao nhận' }));
    const dialog = within(await screen.findByRole('dialog'));
    expect(dialog.getByRole('heading', { name: 'Báo cáo vi phạm' })).toBeTruthy();
    expect(dialog.getByText('Không đúng mô tả')).toBeTruthy();
    expect(
      dialog.getByText(
        'Giao dịch sẽ bị huỷ. Bài của bạn được mở lại. Món bị từ chối sẽ tạm ẩn cho đến khi Admin xem xét.',
      ),
    ).toBeTruthy();
    const submit = dialog.getByRole('button', { name: 'Gửi báo cáo' }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
    await userEvents.type(dialog.getByLabelText(/Mô tả/), 'Máy không bật được');
    await userEvents.click(submit);

    // then
    await waitFor(() =>
      expect(requestApi.refuseHandover).toHaveBeenCalledWith(101, RECEIVER_ID, {
        reason: 'Máy không bật được',
        evidenceMediaIds: undefined,
      }),
    );
    expect(
      await screen.findByText('Bạn đã từ chối nhận và gửi báo cáo. Admin đang xem xét.'),
    ).toBeTruthy();
  });
});


describe('Món mang đổi', () => {
  it('không cần itemApi khi không phải giao dịch Trao đổi', async () => {
    // when
    await renderProgress(RECEIVER_ID, request());

    // then
    expect(itemApi.detail).not.toHaveBeenCalled();
  });
});

describe('Khiếu nại theo vai', () => {
  const dispute = (overrides: Partial<RequestDispute> = {}): RequestDispute => ({
    id: 7,
    reason: 'NO_SHOW',
    openedByMe: false,
    status: 'AWAITING_RESPONSE',
    responseDueAt: '2026-10-31T07:30:00Z',
    respondedAt: null,
    canRespond: true,
    ...overrides,
  });

  it('bên khiếu nại thấy thông báo đã gửi, hạn của đối phương và không có nút giải trình', async () => {
    // when
    await renderProgress(
      RECEIVER_ID,
      request({ status: 'DISPUTED', disputes: [dispute({ openedByMe: true, canRespond: false })] }),
    );

    // then
    expect(
      screen.getByText(
        /Bạn đã báo sự cố giao nhận, chờ Admin xác minh. An Chủ Bài có đến 14:30 31\/10 để giải trình, sau đó Admin kết luận\./,
      ),
    ).toBeTruthy();
    expect(screen.getByText('Trạng thái: Chờ Admin')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Giải trình ngay' })).toBeNull();
    expect(screen.queryByText(/Sự cố giao nhận, chờ Admin xác minh/)).toBeNull();
  });

  it('bên bị khiếu nại thấy cảnh báo và giải trình ngay tại chỗ', async () => {
    // given
    const userEvents = userEvent.setup();
    vi.mocked(respondDispute).mockResolvedValue(undefined);
    await renderProgress(DONOR_ID, request({ status: 'DISPUTED', disputes: [dispute()] }));
    expect(
      screen.getByText(/Bình Người Nhận đã báo sự cố giao nhận, chờ Admin xác minh./),
    ).toBeTruthy();

    // when
    await userEvents.click(screen.getByRole('button', { name: 'Giải trình ngay' }));
    const dialog = within(await screen.findByRole('dialog'));
    await userEvents.type(dialog.getByLabelText(/Nội dung giải trình/), 'Tôi đã đến đúng giờ hẹn');
    await userEvents.click(dialog.getByRole('button', { name: 'Gửi giải trình' }));

    // then
    await waitFor(() =>
      expect(respondDispute).toHaveBeenCalledWith(7, {
        note: 'Tôi đã đến đúng giờ hẹn',
        evidenceMediaIds: undefined,
      }),
    );
    expect(await screen.findByText('Đã gửi giải trình. Admin sẽ xem xét.')).toBeTruthy();
    expect(requestApi.get).toHaveBeenCalledTimes(2);
  });

  it('đã giải trình thì hiện giờ giải trình thay cho nút', async () => {
    // when
    await renderProgress(
      DONOR_ID,
      request({
        status: 'DISPUTED',
        disputes: [dispute({ canRespond: false, respondedAt: '2026-10-30T02:00:00Z', status: 'OPEN' })],
      }),
    );

    // then
    expect(screen.getByText(/Bạn đã giải trình lúc 09:00 30\/10\/2026/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Giải trình ngay' })).toBeNull();
  });

  it('quá hạn giải trình thì báo Admin kết luận theo thông tin hiện có', async () => {
    // when
    await renderProgress(DONOR_ID, request({ status: 'DISPUTED', disputes: [dispute({ canRespond: false })] }));

    // then
    expect(
      screen.getByText('Đã quá hạn giải trình, Admin sẽ kết luận dựa trên thông tin hiện có.'),
    ).toBeTruthy();
  });

  it('đã khiếu nại sau hoàn tất thì ẩn nút Khiếu nại giao dịch', async () => {
    // when
    await renderProgress(
      RECEIVER_ID,
      request({
        status: 'COMPLETED',
        completedAt: '2026-10-29T10:00:00Z',
        disputes: [dispute({ openedByMe: true, canRespond: false, reason: 'NOT_AS_DESCRIBED' })],
      }),
    );

    // then
    expect(screen.queryByRole('button', { name: 'Khiếu nại giao dịch' })).toBeNull();
  });

  it('người từ chối nhận thấy đã gửi báo cáo, không có khối khiếu nại', async () => {
    // when
    await renderProgress(
      RECEIVER_ID,
      request({
        status: 'CANCELLED',
        refusalReport: dispute({
          id: 9,
          reason: 'NOT_AS_DESCRIBED',
          openedByMe: true,
          status: 'OPEN',
          responseDueAt: null,
          canRespond: false,
        }),
      }),
    );

    // then
    expect(screen.getByText(/Bạn đã từ chối nhận và gửi báo cáo\. Admin đang xem xét\./)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Gửi giải trình' })).toBeNull();
    expect(screen.queryByText(/khiếu nại giao dịch này/)).toBeNull();
  });

  it('bên bị báo cáo thấy món đang tạm ẩn và gửi giải trình không bắt buộc', async () => {
    // given
    const userEvents = userEvent.setup();
    vi.mocked(respondDispute).mockResolvedValue(undefined);
    await renderProgress(
      DONOR_ID,
      request({
        status: 'CANCELLED',
        refusalReport: dispute({ id: 9, reason: 'NOT_AS_DESCRIBED', status: 'OPEN', responseDueAt: null }),
      }),
    );
    expect(
      screen.getByText('Bình Người Nhận đã từ chối nhận vì món không đúng mô tả.'),
    ).toBeTruthy();
    expect(screen.getByText(/Món của bạn đang tạm ẩn chờ Admin xem xét\./)).toBeTruthy();

    // when
    await userEvents.click(screen.getByRole('button', { name: 'Gửi giải trình' }));
    const dialog = within(await screen.findByRole('dialog'));
    expect(dialog.getByRole('heading', { name: 'Giải trình báo cáo' })).toBeTruthy();
    await userEvents.type(dialog.getByLabelText(/Nội dung giải trình/), 'Máy vẫn bật được lúc giao');
    await userEvents.click(dialog.getByRole('button', { name: 'Gửi giải trình' }));

    // then
    await waitFor(() =>
      expect(respondDispute).toHaveBeenCalledWith(9, {
        note: 'Máy vẫn bật được lúc giao',
        evidenceMediaIds: undefined,
      }),
    );
  });
});
