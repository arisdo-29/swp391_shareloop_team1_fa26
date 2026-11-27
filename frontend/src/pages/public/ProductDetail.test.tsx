// @vitest-environment jsdom

import { cleanup, render, screen, within } from '@testing-library/react';
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

describe('Nội dung chi tiết sản phẩm', () => {
  it('hiển thị giá có khung vàng và giá trống bằng chữ xám', async () => {
    await renderDetail(detail({ referencePrice: 120000 }));

    const referencePrice = screen.getByText(/Giá tham khảo 120\.000/);
    expect(referencePrice.className).toContain('border-warning');

    cleanup();
    await renderDetail(detail({ referencePrice: null }));

    const missingPrice = screen.getByText('Chưa có giá tham khảo');
    expect(missingPrice.className).toContain('text-text-muted');
    expect(missingPrice.className).not.toContain('border-warning');
  });

  it('định dạng Markdown an toàn và hiển thị danh sách trong mô tả', async () => {
    await renderDetail(
      detail({
        description: '**Nội dung quan trọng**\n\n- Dòng thứ nhất\n- Dòng thứ hai',
      }),
    );

    const description = screen.getByRole('heading', { level: 2, name: 'Mô tả' }).closest('section')!;
    expect(within(description).getByText('Nội dung quan trọng').tagName).toBe('STRONG');
    expect(within(description).getByRole('list').children).toHaveLength(2);
    expect(within(description).queryByText(/\*\*/)).toBeNull();
  });
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

  it('bài RESERVED ở chế độ chỉ xem có nhãn Đã có người nhận và không có nút gửi yêu cầu', async () => {
    // when: người từng gửi yêu cầu không được chọn mở lại bài
    await renderDetail(detail({ status: 'RESERVED', requestable: false }));

    // then
    expect(screen.getAllByText('Đã có người nhận').length).toBeGreaterThan(0);
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

  it('nút Báo cáo bài đăng hiển thị dạng text-button màu đỏ nhẹ', async () => {
    // when
    await renderDetail(detail());

    // then
    const reportButton = screen.getByRole('button', { name: 'Báo cáo bài đăng' });
    expect(reportButton).toBeTruthy();
    expect(reportButton.className).toContain('text-error/80');
    expect(reportButton.className).not.toContain('bg-warning-soft');
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
    expect(screen.queryByRole('navigation', { name: 'Phân trang món mang đổi' })).toBeNull();

    // when
    await user.click(screen.getByRole('button', { name: 'Gửi đề nghị, miễn phí' }));

    // then
    expect(requestApi.create).not.toHaveBeenCalled();
    expect(
      screen.getByText('Vui lòng chọn một món để trao đổi hoặc chọn "Chỉ xin nhận, không kèm món".'),
    ).toBeTruthy();
  });

  it('hiển thị toàn bộ món trong vùng cuộn và chỉ đánh dấu sau khi người dùng chọn', async () => {
    const user = userEvent.setup();
    const otherItems = Array.from({ length: 6 }, (_, index) =>
      detail({ id: 60 + index, donorId: VIEWER_ID, title: `Món số ${index + 1}`, referencePrice: 900_000 }),
    );
    const similarItem = detail({ id: 70, donorId: VIEWER_ID, title: 'Bàn gỗ', referencePrice: 300_000 });
    vi.mocked(requestApi.forItem).mockResolvedValue([]);
    vi.mocked(requestApi.create).mockResolvedValue(createdRequest(65));
    await renderDetail(detail({ offerType: 'SWAP', referencePrice: 300_000 }), [...otherItems, similarItem]);

    const options = within(screen.getByRole('group', { name: /Chọn món bạn mang đổi/ }));
    const cards = options.getAllByRole('button', { name: /Bàn gỗ|Món số/ });
    expect(cards).toHaveLength(7);
    expect(cards[0]).toBe(options.getByRole('button', { name: /Bàn gỗ.*Giá tương đương/ }));
    expect(cards.every((card) => card.getAttribute('aria-pressed') === 'false')).toBe(true);
    expect(screen.getByText('7 món')).toBeTruthy();
    expect(screen.queryByRole('navigation', { name: 'Phân trang món mang đổi' })).toBeNull();
    expect(screen.getByTestId('offered-item-scroll').className).toContain('max-h-[300px]');

    const selectedCard = options.getByRole('button', { name: /Món số 6/ });
    await user.click(selectedCard);
    expect(selectedCard.getAttribute('aria-pressed')).toBe('true');
    expect(selectedCard.className).toContain('border-exchange');
    expect(selectedCard.className).toContain('bg-detail-selected');

    await user.click(screen.getByRole('button', { name: 'Gửi đề nghị, miễn phí' }));
    expect(requestApi.create).toHaveBeenCalledWith(45, VIEWER_ID, 65);
  });
});
