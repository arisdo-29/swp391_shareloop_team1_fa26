// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { actions, store } from '../../app/store';
import { catalogApi } from '../../services/catalogApi';
import { itemApi, type ItemResponse } from '../../services/itemApi';
import { requestApi } from '../../services/requestApi';
import type { User } from '../../types/domain';
import { ProductDetail } from './ProductDetail';

vi.mock('../../services/itemApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/itemApi')>()),
  itemApi: { detail: vi.fn(), mine: vi.fn() },
}));
vi.mock('../../services/catalogApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/catalogApi')>()),
  catalogApi: { category: vi.fn(), categories: vi.fn(), areas: vi.fn() },
}));
vi.mock('../../services/requestApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/requestApi')>()),
  requestApi: { mine: vi.fn(), create: vi.fn(), cancel: vi.fn(), forItem: vi.fn() },
}));

const DONOR_ID = 9;
const VIEWER_ID = 12;

const viewer: User = {
  id: `api_${VIEWER_ID}`,
  username: 'viewer',
  password: '',
  name: 'Người xem',
  email: 'viewer@example.com',
  phone: '0900000000',
  district: 'Quận 1',
  avatarInitials: 'NX',
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
};

const detail = (overrides: Partial<ItemResponse> = {}): ItemResponse => ({
  id: 45,
  donorId: DONOR_ID,
  categoryId: 1,
  areaId: 2,
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
  status: 'APPROVED',
  createdAt: '2026-10-20T08:00:00Z',
  updatedAt: '2026-10-20T08:00:00Z',
  expireAt: null,
  boostedUntil: null,
  donorName: 'An Chủ Bài',
  requestable: true,
  ...overrides,
});

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location">{location.pathname}</span>;
}

async function renderDetail(item: ItemResponse, ownedItems: ItemResponse[] = []) {
  store.dispatch(actions.loginFromApi(viewer));
  vi.mocked(itemApi.detail).mockResolvedValue(item);
  vi.mocked(itemApi.mine).mockResolvedValue(ownedItems);
  vi.mocked(requestApi.mine).mockResolvedValue([]);
  vi.mocked(catalogApi.category).mockResolvedValue({ id: 1, name: 'Xe cộ' } as never);
  vi.mocked(catalogApi.categories).mockResolvedValue([]);
  vi.mocked(catalogApi.areas).mockResolvedValue([]);
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/items/45']}>
        <Routes>
          <Route path="/items/:id" element={<ProductDetail />} />
          <Route path="*" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
  await screen.findByRole('heading', { level: 1, name: 'Xe đạp cũ' });
}

afterEach(() => {
  cleanup();
  store.dispatch(actions.logout());
  vi.clearAllMocks();
});

describe('Chi tiết bài đã giao dịch', () => {
  it('bài TRADED với requestable false chỉ cho xem: có nhãn Đã giao dịch, không có nút gửi yêu cầu', async () => {
    // when
    await renderDetail(
      detail({ status: 'TRADED', requestable: false, completedAt: '2026-10-27T03:00:00Z' }),
    );

    // then
    expect(screen.getAllByText('Đã giao dịch').length).toBeGreaterThan(0);
    expect(
      screen.getByText(
        'Món này đã trao đổi xong nên không nhận yêu cầu mới. Bạn chỉ xem được chi tiết bài đăng.',
      ),
    ).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Gửi yêu cầu/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /Gửi đề nghị/ })).toBeNull();
  });

  it('bài đang mở nhận yêu cầu vẫn có nút gửi yêu cầu như cũ', async () => {
    // when
    await renderDetail(detail());

    // then
    expect(screen.getByRole('button', { name: 'Gửi yêu cầu, miễn phí' })).toBeTruthy();
    expect(screen.queryByText('Đã giao dịch')).toBeNull();
  });
});

describe('Liên kết hồ sơ ở khối chủ bài', () => {
  it('Xem hồ sơ mở hồ sơ công khai của chủ bài', async () => {
    // given
    const user = userEvent.setup();
    await renderDetail(detail());

    // when
    await user.click(screen.getByRole('button', { name: 'Xem hồ sơ' }));

    // then
    expect(screen.getByTestId('location').textContent).toBe(`/users/${DONOR_ID}`);
  });
});

describe('Gửi yêu cầu có hoặc không kèm món', () => {
  // Món của người xem, đủ điều kiện mang đi đổi.
  const myChair = detail({ id: 60, donorId: VIEWER_ID, title: 'Ghế xoay', offerType: 'GIVE' });

  const createdRequest = (offeredItemId?: number) => ({
    id: 101,
    itemId: 45,
    receiverId: VIEWER_ID,
    offeredItemId,
    type: 'GIVE' as const,
    status: 'PENDING' as const,
    createdAt: '2026-11-08T08:00:00Z',
  });

  it('bài Cho gửi được yêu cầu không kèm món và báo xin nhận', async () => {
    // given
    const user = userEvent.setup();
    vi.mocked(requestApi.create).mockResolvedValue(createdRequest());
    await renderDetail(detail(), [myChair]);

    // when
    await user.click(screen.getByRole('button', { name: 'Gửi yêu cầu, miễn phí' }));

    // then
    expect(requestApi.create).toHaveBeenCalledWith(45, VIEWER_ID, undefined);
    expect(requestApi.forItem).not.toHaveBeenCalled();
    expect(await screen.findByText('Đã gửi yêu cầu xin nhận.')).toBeTruthy();
  });

  it('bài Cho gửi được yêu cầu kèm món và báo đề nghị trao đổi', async () => {
    // given
    const user = userEvent.setup();
    vi.mocked(requestApi.forItem).mockResolvedValue([]);
    vi.mocked(requestApi.create).mockResolvedValue(createdRequest(60));
    await renderDetail(detail(), [myChair]);

    // when
    await user.click(await screen.findByRole('button', { name: /Ghế xoay/ }));
    await user.click(screen.getByRole('button', { name: 'Gửi yêu cầu, miễn phí' }));

    // then
    expect(requestApi.forItem).toHaveBeenCalledWith(60, VIEWER_ID);
    expect(requestApi.create).toHaveBeenCalledWith(45, VIEWER_ID, 60);
    expect(await screen.findByText('Đã gửi đề nghị trao đổi.')).toBeTruthy();
  });

  it('bài Trao đổi gửi được "Chỉ xin nhận" dù không có món nào để đổi', async () => {
    // given
    const user = userEvent.setup();
    vi.mocked(requestApi.create).mockResolvedValue(createdRequest());
    await renderDetail(detail({ offerType: 'SWAP' }));

    // when
    await user.click(screen.getByRole('button', { name: 'Chỉ xin nhận, không kèm món' }));
    await user.click(screen.getByRole('button', { name: 'Gửi yêu cầu, miễn phí' }));

    // then
    expect(requestApi.create).toHaveBeenCalledWith(45, VIEWER_ID, undefined);
    expect(requestApi.forItem).not.toHaveBeenCalled();
    expect(await screen.findByText('Đã gửi yêu cầu xin nhận.')).toBeTruthy();
  });

  it('bài Trao đổi chưa chọn món hay "Chỉ xin nhận" thì không gửi', async () => {
    // given
    const user = userEvent.setup();
    await renderDetail(detail({ offerType: 'SWAP' }), [myChair]);

    // when
    await user.click(screen.getByRole('button', { name: 'Gửi đề nghị, miễn phí' }));

    // then
    expect(requestApi.create).not.toHaveBeenCalled();
    expect(
      screen.getByText('Vui lòng chọn một món để trao đổi hoặc chọn "Chỉ xin nhận, không kèm món".'),
    ).toBeTruthy();
  });
});
