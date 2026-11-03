// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { actions, store } from '../../app/store';
import { itemApi } from '../../services/itemApi';
import { mediaApi } from '../../services/mediaApi';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import type { ItemRequestStatus, User } from '../../types/domain';
import { TransactionProgress } from './TransactionProgress';

vi.mock('../../services/requestApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/requestApi')>()),
  requestApi: {
    get: vi.fn(),
    forItem: vi.fn(),
    cancel: vi.fn(),
    select: vi.fn(),
    cancelTransaction: vi.fn(),
    reportHandoverIssue: vi.fn(),
    getContact: vi.fn(),
    confirmHandover: vi.fn(),
    attachMedia: vi.fn(),
  },
}));
vi.mock('../../services/itemApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/itemApi')>()),
  itemApi: { detail: vi.fn() },
}));
vi.mock('../../services/mediaApi', () => ({ mediaApi: { upload: vi.fn() } }));

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
async function renderProgress(viewerId: number, current: RequestResponse) {
  store.dispatch(actions.loginFromApi(user(viewerId)));
  vi.mocked(requestApi.get).mockResolvedValue(current);
  vi.mocked(requestApi.forItem).mockResolvedValue([current]);
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/messages/101/progress']}>
        <Routes>
          <Route path="/messages/:conversationId/progress" element={<TransactionProgress />} />
          <Route path="*" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
  await screen.findByRole('heading', { level: 1, name: 'Tiến trình giao dịch' });
}

beforeEach(() => {
  vi.stubGlobal('scrollTo', vi.fn());
});

afterEach(() => {
  cleanup();
  store.dispatch(actions.logout());
  vi.clearAllMocks();
  vi.unstubAllGlobals();
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
    expect(screen.queryByRole('button', { name: 'Huỷ giao dịch' })).toBeNull();
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

  it('không được chọn và đã huỷ hiện Alert kèm lý do', async () => {
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

  it('chờ chốt lịch: hiện form lịch với Lưu lịch hẹn và Xác nhận lịch, chưa có số điện thoại', async () => {
    // when
    await renderProgress(
      RECEIVER_ID,
      request({ status: 'AWAITING_LOGISTICS', contactRevealedAt: undefined }),
    );

    // then
    expect(screen.getByRole('button', { name: 'Lưu lịch hẹn' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Xác nhận lịch' })).toBeTruthy();
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

  it('người nhận thấy Tôi đã nhận được đồ, chủ bài thấy Tôi đã giao đồ', async () => {
    // when
    await renderProgress(RECEIVER_ID, request());

    // then
    expect(screen.getByRole('button', { name: 'Tôi đã nhận được đồ' })).toBeTruthy();
    cleanup();
    await renderProgress(DONOR_ID, request());
    expect(screen.getByRole('button', { name: 'Tôi đã giao đồ' })).toBeTruthy();
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

  it('chưa có ảnh thì hiện trạng thái rỗng và Thêm ảnh bị khoá', async () => {
    // when
    await renderProgress(RECEIVER_ID, request());

    // then
    expect(screen.getByText('Chưa có ảnh giao nhận')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Thêm ảnh' }) as HTMLButtonElement).disabled).toBe(
      true,
    );
  });

  it('khóa thao tác và hiện trạng thái đang tải trong lúc thêm ảnh', async () => {
    const userEvents = userEvent.setup();
    let resolveUpload!: (value: { mediaId: number; url: string }) => void;
    vi.mocked(mediaApi.upload).mockReturnValue(
      new Promise((resolve) => {
        resolveUpload = resolve;
      }),
    );
    vi.mocked(requestApi.attachMedia).mockResolvedValue([]);
    await renderProgress(RECEIVER_ID, request());
    const input = screen.getByLabelText('Chọn ảnh') as HTMLInputElement;

    await userEvents.upload(input, new File(['image'], 'handover.png', { type: 'image/png' }));
    await userEvents.click(screen.getByRole('button', { name: 'Thêm 1 ảnh' }));

    expect(screen.getByRole('button', { name: 'Đang tải...' })).toBeTruthy();
    expect(input.disabled).toBe(true);
    resolveUpload({ mediaId: 9, url: '/api/v1/media/9' });
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Thêm ảnh' })).toBeTruthy();
    });
    expect(mediaApi.upload).toHaveBeenCalledTimes(1);
    expect(requestApi.attachMedia).toHaveBeenCalledWith(101, {
      mediaIds: [9],
      stage: 'HANDOVER',
    });
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

// Chuyển từ Messages.transaction.test.tsx: các chức năng này nay nằm ở trang Tiến trình.
describe('Huỷ giao dịch', () => {
  it.each(['RESERVED', 'AWAITING_LOGISTICS', 'LOGISTICS_CONFIRMED', 'AWAITING_HANDOVER'] as const)(
    'hiện khối Huỷ giao dịch và chú thích khi trạng thái %s',
    async (status) => {
      await renderProgress(RECEIVER_ID, request({ status }));

      expect(screen.getByRole('button', { name: 'Huỷ giao dịch' })).toBeTruthy();
      expect(
        screen.getByText(
          'Cần nêu lý do. Bài đăng mở lại cho người khác, không mất phí đăng lần nữa.',
        ),
      ).toBeTruthy();
    },
  );

  it.each(['COMPLETED', 'DISPUTED', 'CANCELLED'] as const)(
    'không hiện khi trạng thái %s',
    async (status) => {
      await renderProgress(RECEIVER_ID, request({ status }));

      expect(screen.queryByRole('button', { name: 'Huỷ giao dịch' })).toBeNull();
    },
  );

  it('bắt buộc lý do rồi gọi API và cập nhật trạng thái trả về', async () => {
    const userEvents = userEvent.setup();
    vi.mocked(requestApi.cancelTransaction).mockResolvedValue(
      request({ status: 'CANCELLED', cancelReason: '[Người nhận] Tôi bận việc đột xuất' }),
    );
    await renderProgress(RECEIVER_ID, request());

    await userEvents.click(screen.getByRole('button', { name: 'Huỷ giao dịch' }));
    const dialog = await screen.findByRole('dialog');
    const submit = within(dialog).getByRole('button', {
      name: 'Huỷ giao dịch',
    }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);

    const reasonBox = within(dialog).getByLabelText(/Lý do huỷ/);
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

    expect(screen.getByText(/chờ chủ bài giải trình trước 14:30 31\/10/)).toBeTruthy();
  });

  it('không hiện câu chờ giải trình khi deadline là null', async () => {
    await renderProgress(RECEIVER_ID, request({ status: 'DISPUTED', disputeResponseDueAt: null }));

    expect(screen.getByText(/Giao dịch đang có khiếu nại/)).toBeTruthy();
    expect(screen.queryByText(/chờ chủ bài giải trình trước/)).toBeNull();
  });
});

describe('Báo chưa nhận được / chưa giao được', () => {
  it('người nhận thấy nút Tôi chưa nhận được khi chủ bài đã xác nhận còn mình chưa', async () => {
    await renderProgress(
      RECEIVER_ID,
      request({ status: 'AWAITING_HANDOVER', donorHandoverAt: '2026-10-25T10:00:00Z' }),
    );

    expect(screen.getByRole('button', { name: 'Tôi chưa nhận được' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Tôi đã nhận được đồ' })).toBeTruthy();
  });

  it('chủ bài thấy nút Tôi chưa giao được khi người nhận đã xác nhận còn mình chưa', async () => {
    await renderProgress(
      DONOR_ID,
      request({ status: 'AWAITING_HANDOVER', receiverHandoverAt: '2026-10-25T10:00:00Z' }),
    );

    expect(screen.getByRole('button', { name: 'Tôi chưa giao được' })).toBeTruthy();
  });

  it('không hiện nút khi bên kia chưa xác nhận hoặc mình đã xác nhận', async () => {
    await renderProgress(RECEIVER_ID, request({ status: 'AWAITING_HANDOVER' }));

    expect(screen.getByRole('button', { name: 'Tôi đã nhận được đồ' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Tôi chưa nhận được' })).toBeNull();
  });

  it('gửi ghi chú và cập nhật giao dịch sang khiếu nại', async () => {
    const userEvents = userEvent.setup();
    vi.mocked(requestApi.reportHandoverIssue).mockResolvedValue(
      request({ status: 'DISPUTED', donorHandoverAt: '2026-10-25T10:00:00Z' }),
    );
    await renderProgress(
      RECEIVER_ID,
      request({ status: 'AWAITING_HANDOVER', donorHandoverAt: '2026-10-25T10:00:00Z' }),
    );

    await userEvents.click(screen.getByRole('button', { name: 'Tôi chưa nhận được' }));
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

describe('Món mang đổi', () => {
  it('không cần itemApi khi không phải giao dịch Trao đổi', async () => {
    // when
    await renderProgress(RECEIVER_ID, request());

    // then
    expect(itemApi.detail).not.toHaveBeenCalled();
  });
});
