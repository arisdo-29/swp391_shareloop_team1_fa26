// @vitest-environment jsdom

import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { actions, store } from '../../app/store';
import { catalogApi } from '../../services/catalogApi';
import type { ItemResponse, PageResponse } from '../../services/itemApi';
import { userApi, type PublicProfileResponse, type UserItemTab } from '../../services/userApi';
import type { User } from '../../types/domain';
import { UserProfile } from './UserProfile';

vi.mock('../../services/userApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/userApi')>()),
  userApi: { getPublicProfile: vi.fn(), getUserItems: vi.fn() },
}));
vi.mock('../../services/catalogApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/catalogApi')>()),
  catalogApi: { categories: vi.fn(), areas: vi.fn() },
}));

const VIEWER_ID = 12;
const PROFILE_ID = 9;

const viewer: User = {
  id: `api_${VIEWER_ID}`,
  username: 'viewer',
  password: '',
  name: 'Người xem',
  email: 'viewer@example.com',
  phone: '0900000000',
  district: 'Quận 1',
  avatarInitials: 'NX',
  totalCredit: 460,
  availableCredit: 460,
  holdCredit: 0,
  rewardPoints: 77,
  reputationStars: 5,
  rank: 'BRONZE',
  status: 'active',
  role: 'user',
  joinedAt: '2026-01-01T00:00:00Z',
  totalTx: 0,
};

const profile = (overrides: Partial<PublicProfileResponse> = {}): PublicProfileResponse => ({
  id: PROFILE_ID,
  fullName: 'Nguyễn Văn An',
  avatarUrl: null,
  avatarInitials: 'NA',
  district: 'Bình Thạnh',
  rank: 'GOLD',
  trustStars: 4,
  asDescribedRate: 0.96,
  completedTransactions: 12,
  joinedAt: '2026-03-01T08:00:00Z',
  ...overrides,
});

const itemResponse = (id: number, overrides: Partial<ItemResponse> = {}): ItemResponse => ({
  id,
  donorId: PROFILE_ID,
  categoryId: 1,
  areaId: 2,
  offerType: 'GIVE',
  title: `Món số ${id}`,
  description: 'Mô tả',
  desiredItem: null,
  condition: 'GOOD',
  defectNote: null,
  brand: null,
  images: [],
  defectImages: [],
  operatingStatus: null,
  status: 'APPROVED',
  createdAt: '2026-10-20T08:00:00Z',
  updatedAt: '2026-10-20T08:00:00Z',
  expireAt: null,
  boostedUntil: null,
  ...overrides,
});

const page = (items: ItemResponse[], totalElements = items.length): PageResponse<ItemResponse> => ({
  items,
  page: 0,
  size: 8,
  totalElements,
});

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location">{location.pathname}</span>;
}

function mockItems(byTab: Record<UserItemTab, PageResponse<ItemResponse>>) {
  vi.mocked(userApi.getUserItems).mockImplementation(async (_id, tab) => byTab[tab]);
}

function renderProfile(path = `/users/${PROFILE_ID}`, viewerUser: User = viewer) {
  store.dispatch(actions.loginFromApi(viewerUser));
  vi.mocked(catalogApi.categories).mockResolvedValue([]);
  vi.mocked(catalogApi.areas).mockResolvedValue([]);
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/messages', path]} initialIndex={1}>
        <Routes>
          <Route path="/users/:userId" element={<UserProfile />} />
          <Route path="*" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
}

afterEach(() => {
  cleanup();
  store.dispatch(actions.logout());
  vi.clearAllMocks();
});

describe('Trang Hồ sơ công khai', () => {
  it('hiện trạng thái đang tải khi chưa có dữ liệu', () => {
    // given
    vi.mocked(userApi.getPublicProfile).mockReturnValue(new Promise(() => {}));

    // when
    renderProfile();

    // then
    expect(screen.getByRole('status', { name: 'Đang tải hồ sơ' })).toBeTruthy();
  });

  it('404 hiện Không tìm thấy người dùng, nút quay lại về trang trước', async () => {
    // given
    const user = userEvent.setup();
    vi.mocked(userApi.getPublicProfile).mockRejectedValue({ status: 404, code: 'USER_NOT_FOUND' });

    // when
    renderProfile();
    await user.click(await screen.findByRole('button', { name: 'Quay lại' }));

    // then
    expect(screen.getByTestId('location').textContent).toBe('/messages');
  });

  it('lỗi khác hiện Alert và thử tải lại được', async () => {
    // given
    const user = userEvent.setup();
    vi.mocked(userApi.getPublicProfile)
      .mockRejectedValueOnce({ status: 500, code: 'INTERNAL_ERROR' })
      .mockResolvedValueOnce(profile());
    mockItems({ ACTIVE: page([]), DONE: page([]) });

    // when
    renderProfile();
    await user.click(await screen.findByRole('button', { name: 'Thử tải lại' }));

    // then
    expect(await screen.findByRole('heading', { level: 1, name: 'Nguyễn Văn An' })).toBeTruthy();
  });

  it('hiện số liệu công khai, không hiện Credit hay điểm thưởng', async () => {
    // given
    vi.mocked(userApi.getPublicProfile).mockResolvedValue(profile());
    mockItems({ ACTIVE: page([]), DONE: page([]) });

    // when
    renderProfile();
    await screen.findByRole('heading', { level: 1, name: 'Nguyễn Văn An' });

    // then
    expect(screen.getByText('NA')).toBeTruthy();
    expect(screen.getByText('Hạng Vàng')).toBeTruthy();
    expect(screen.getByText('Khu vực: Bình Thạnh')).toBeTruthy();
    expect(screen.getByText('4 / 5 sao')).toBeTruthy();
    expect(screen.getByText('12')).toBeTruthy();
    expect(screen.getByText('96%')).toBeTruthy();
    expect(screen.queryByText(/Credit/)).toBeNull();
    expect(screen.queryByText(/Điểm thưởng/)).toBeNull();
    expect(screen.queryByText('460')).toBeNull();
  });

  it('ẩn ô Đúng mô tả khi asDescribedRate là null và ẩn khu vực khi rỗng', async () => {
    // given
    vi.mocked(userApi.getPublicProfile).mockResolvedValue(
      profile({ asDescribedRate: null, district: null }),
    );
    mockItems({ ACTIVE: page([]), DONE: page([]) });

    // when
    renderProfile();
    await screen.findByRole('heading', { level: 1, name: 'Nguyễn Văn An' });

    // then
    expect(screen.queryByText('Đúng mô tả')).toBeNull();
    expect(screen.queryByText(/Khu vực:/)).toBeNull();
  });

  it('hồ sơ người khác có Báo cáo, không có Nhắn tin', async () => {
    // given
    vi.mocked(userApi.getPublicProfile).mockResolvedValue(profile());
    mockItems({ ACTIVE: page([]), DONE: page([]) });

    // when
    renderProfile();
    await screen.findByRole('heading', { level: 1, name: 'Nguyễn Văn An' });

    // then
    expect(screen.getByRole('button', { name: 'Báo cáo' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Nhắn tin' })).toBeNull();
  });

  it('xem hồ sơ của chính mình thì ẩn Báo cáo', async () => {
    // given
    vi.mocked(userApi.getPublicProfile).mockResolvedValue(profile({ id: VIEWER_ID }));
    mockItems({ ACTIVE: page([]), DONE: page([]) });

    // when
    renderProfile(`/users/${VIEWER_ID}`);
    await screen.findByRole('heading', { level: 1, name: 'Nguyễn Văn An' });

    // then
    expect(screen.queryByRole('button', { name: 'Báo cáo' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Nhắn tin' })).toBeNull();
  });
});

describe('Hai mục món đồ ở hồ sơ', () => {
  it('rỗng thì mỗi mục có EmptyState riêng và số món là 0', async () => {
    // given
    vi.mocked(userApi.getPublicProfile).mockResolvedValue(profile());
    mockItems({ ACTIVE: page([]), DONE: page([]) });

    // when
    renderProfile();

    // then
    expect(await screen.findByText('Chưa có món đang hoạt động')).toBeTruthy();
    expect(await screen.findByText('Chưa có món đã giao dịch')).toBeTruthy();
    expect(screen.getAllByText('(0 món)')).toHaveLength(2);
  });

  it('có dữ liệu thì số món lấy từ totalElements và thẻ trỏ tới chi tiết bài', async () => {
    // given
    vi.mocked(userApi.getPublicProfile).mockResolvedValue(profile());
    mockItems({ ACTIVE: page([itemResponse(1), itemResponse(2)], 2), DONE: page([]) });

    // when
    renderProfile();
    const active = await screen.findByRole('region', { name: /Đang hoạt động/ });

    // then
    expect(await within(active).findByText('(2 món)')).toBeTruthy();
    expect(within(active).getByRole('link', { name: /Món số 1/ }).getAttribute('href')).toBe('/items/1');
  });

  it('thẻ đã giao dịch có nhãn Tạm ẩn, Đã giao dịch và ngày hoàn tất', async () => {
    // given
    vi.mocked(userApi.getPublicProfile).mockResolvedValue(profile());
    mockItems({
      ACTIVE: page([]),
      DONE: page([
        itemResponse(5, {
          status: 'TRADED',
          completedAt: '2026-10-27T03:00:00Z',
          requestable: false,
        }),
      ]),
    });

    // when
    renderProfile();
    const done = await screen.findByRole('region', { name: /Đã giao dịch thành công/ });
    await within(done).findByText('Món số 5');

    // then
    expect(within(done).getByText('Tạm ẩn')).toBeTruthy();
    expect(within(done).getByText('Đã giao dịch')).toBeTruthy();
    expect(within(done).getByText('27/10/2026')).toBeTruthy();
    expect(within(done).getByRole('link', { name: /Món số 5/ }).getAttribute('href')).toBe('/items/5');
    expect(
      within(done).getByText(
        'Các món này đã trao đổi xong nên đang tạm ẩn khỏi trang tìm đồ. Bạn vẫn xem được chi tiết bài đăng.',
      ),
    ).toBeTruthy();
  });

  it('nhiều hơn một trang thì có phân trang và tải đúng trang của đúng mục', async () => {
    // given
    const user = userEvent.setup();
    vi.mocked(userApi.getPublicProfile).mockResolvedValue(profile());
    mockItems({ ACTIVE: page([itemResponse(1)], 9), DONE: page([]) });

    // when
    renderProfile();
    const active = await screen.findByRole('region', { name: /Đang hoạt động/ });
    await user.click(await within(active).findByRole('button', { name: 'Trang sau' }));

    // then
    await waitFor(() => expect(userApi.getUserItems).toHaveBeenCalledWith(PROFILE_ID, 'ACTIVE', 1, 8));
    expect(userApi.getUserItems).not.toHaveBeenCalledWith(PROFILE_ID, 'DONE', 1, 8);
  });

  it('một trang thì không có phân trang', async () => {
    // given
    vi.mocked(userApi.getPublicProfile).mockResolvedValue(profile());
    mockItems({ ACTIVE: page([itemResponse(1)]), DONE: page([]) });

    // when
    renderProfile();
    await screen.findByText('Món số 1');

    // then
    expect(screen.queryByRole('button', { name: 'Trang sau' })).toBeNull();
  });
});
