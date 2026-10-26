// @vitest-environment jsdom

import { StrictMode, type ReactNode } from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { catalogApi, type AreaResponse, type CategoryResponse } from '../../services/catalogApi';
import { itemApi, type ItemResponse, type PageResponse } from '../../services/itemApi';
import { Home } from './Home';
import browseSuggestionBox from '../../assets/browse-suggestion-box.png';

vi.mock('../../app/hooks', () => ({
  useAppSelector: () => null,
}));

vi.mock('../../app/store', () => ({
  selectCurrentUser: vi.fn(),
}));

vi.mock('./HomeMotion', () => ({
  HomeMotion: ({ children }: { children: ReactNode }) => <>{children}</>,
  HomeDiscoveryRail: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock('./HomeCommunity', () => ({
  HomeLifecycle: ({ items }: { items: Array<{ title: string }> }) => (
    <div data-testid="home-lifecycle-items">{items.map((entry) => entry.title).join('|')}</div>
  ),
  HomeProcess: () => null,
}));

vi.mock('../../services/catalogApi', () => ({
  catalogApi: {
    categories: vi.fn(),
    areas: vi.fn(),
  },
}));

vi.mock('../../services/itemApi', () => ({
  itemApi: {
    list: vi.fn(),
  },
}));

const categories: CategoryResponse[] = [
  { id: 7, name: 'Sách', parentId: null, restricted: false, boostable: true, sortOrder: 1 },
];

const areas: AreaResponse[] = [
  { id: 1, name: 'Bình Thạnh', parentId: null, level: 1, children: [] },
];

const item: ItemResponse = {
  id: 501,
  donorId: 9,
  categoryId: 7,
  areaId: 1,
  offerType: 'GIVE',
  title: 'Bộ giáo trình đại học',
  description: 'Giáo trình còn sử dụng tốt.',
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
};

const itemPage: PageResponse<ItemResponse> = {
  items: [item],
  page: 0,
  size: 100,
  totalElements: 1,
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

function renderHome() {
  return render(
    <MemoryRouter>
      <Home />
    </MemoryRouter>,
  );
}

function renderHomeInStrictMode() {
  return render(
    <StrictMode>
      <MemoryRouter>
        <Home />
      </MemoryRouter>
    </StrictMode>,
  );
}

beforeEach(() => {
  vi.mocked(catalogApi.categories).mockResolvedValue(categories);
  vi.mocked(catalogApi.areas).mockResolvedValue(areas);
  vi.mocked(itemApi.list).mockResolvedValue(itemPage);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('trạng thái tải bài đăng trên Home', () => {
  it('giữ chức năng tìm kiếm và chọn khu vực khi bỏ icon trang trí', () => {
    renderHome();

    const searchButton = screen.getByRole('button', { name: 'Tìm đồ' });
    const areaSelect = screen.getByRole('combobox', { name: 'Chọn quận' });
    expect(searchButton.querySelector('svg')).toBeNull();
    expect(areaSelect.classList.contains('appearance-none')).toBe(true);
    expect(areaSelect.querySelector('option')).toBeTruthy();
  });

  it('dùng chung request items đang chạy khi StrictMode mount lại Home', async () => {
    const itemsRequest = deferred<PageResponse<ItemResponse>>();
    vi.mocked(itemApi.list).mockReturnValue(itemsRequest.promise);

    renderHomeInStrictMode();

    expect(itemApi.list).toHaveBeenCalledTimes(1);
    await act(async () => itemsRequest.resolve(itemPage));
    expect((await screen.findAllByText(item.title)).length).toBeGreaterThan(0);
  });

  it('giữ link danh mục nhưng không hiển thị mũi tên trên thẻ', async () => {
    const { container } = renderHome();

    await waitFor(() => expect(container.querySelector('.home-category-label')).toBeTruthy());
    const categoryLabel = container.querySelector('.home-category-label');
    const categoryLink = categoryLabel?.closest('a');
    expect(categoryLink?.getAttribute('href')).toBe('/browse?categoryId=7');
    expect(container.querySelector('.home-category-arrow')).toBeNull();
  });

  it('kết thúc loading và hiển thị bài đăng khi API items thành công', async () => {
    const itemsRequest = deferred<PageResponse<ItemResponse>>();
    vi.mocked(itemApi.list).mockReturnValue(itemsRequest.promise);

    renderHome();

    expect(screen.getByText('Đang tải bài đăng mới nhất...')).toBeTruthy();
    await act(async () => itemsRequest.resolve(itemPage));

    expect((await screen.findAllByText(item.title)).length).toBeGreaterThan(0);
    await waitFor(() => expect(screen.queryByText('Đang tải bài đăng mới nhất...')).toBeNull());
  });

  it('xếp Món đồ mới theo createdAt và không ưu tiên bài được đẩy', async () => {
    const oldBoostedItem: ItemResponse = {
      ...item,
      id: 502,
      title: 'Bài cũ đang được đẩy',
      createdAt: '2026-10-20T08:00:00Z',
      boostedUntil: '2099-11-20T08:00:00Z',
    };
    const newestItem: ItemResponse = {
      ...item,
      id: 503,
      title: 'Bài mới nhất',
      createdAt: '2026-10-25T08:00:00Z',
    };
    const unavailableItem: ItemResponse = {
      ...item,
      id: 504,
      title: 'Bài chưa khả dụng',
      status: 'PENDING_REVIEW',
      createdAt: '2026-10-26T08:00:00Z',
    };
    const expiredItem: ItemResponse = {
      ...item,
      id: 505,
      title: 'Bài đã hết hạn',
      createdAt: '2026-10-27T08:00:00Z',
      expireAt: '2026-10-01T08:00:00Z',
    };
    vi.mocked(itemApi.list).mockResolvedValue({
      items: [oldBoostedItem, unavailableItem, expiredItem, newestItem],
      page: 0,
      size: 100,
      totalElements: 4,
    });

    renderHome();

    const heading = await screen.findByRole('heading', { name: 'Món đồ mới' });
    const section = heading.closest('section');
    expect(section).toBeTruthy();
    expect(within(section!).getAllByRole('heading', { level: 3 }).map((entry) => entry.textContent)).toEqual([
      newestItem.title,
      oldBoostedItem.title,
    ]);
    expect(within(section!).queryByText('Được đẩy')).toBeNull();
    expect(within(section!).queryByText(unavailableItem.title)).toBeNull();
    expect(within(section!).queryByText(expiredItem.title)).toBeNull();
  });

  it('chỉ hiển thị bài đang được đẩy trong Tin nổi bật và xếp thời gian đẩy mới nhất trước', async () => {
    const earlierBoostedItem: ItemResponse = {
      ...item,
      id: 506,
      title: 'Bài đẩy trước',
      boostedUntil: '2099-10-20T08:00:00Z',
    };
    const latestBoostedItem: ItemResponse = {
      ...item,
      id: 507,
      title: 'Bài đẩy mới nhất',
      boostedUntil: '2099-11-20T08:00:00Z',
    };
    const expiredBoostItem: ItemResponse = {
      ...item,
      id: 508,
      title: 'Bài hết hiệu lực đẩy',
      boostedUntil: '2020-10-20T08:00:00Z',
    };
    const unboostedItem: ItemResponse = {
      ...item,
      id: 509,
      title: 'Bài chưa đẩy',
    };
    vi.mocked(itemApi.list).mockResolvedValue({
      items: [earlierBoostedItem, expiredBoostItem, unboostedItem, latestBoostedItem],
      page: 0,
      size: 100,
      totalElements: 4,
    });

    renderHome();

    const heading = await screen.findByRole('heading', { name: 'Tin nổi bật' });
    const section = heading.closest('section');
    expect(section).toBeTruthy();
    expect(within(section!).getByRole('link', { name: 'Xem tất cả' }).getAttribute('href')).toBe(
      '/browse?boosted=true',
    );
    const newItemsSection = screen.getByRole('heading', { name: 'Món đồ mới' }).closest('section');
    expect(section!.querySelector('.home-product-grid')?.className).toBe(
      newItemsSection!.querySelector('.home-product-grid')?.className,
    );
    expect(within(section!).getAllByRole('heading', { level: 3 }).map((entry) => entry.textContent)).toEqual([
      latestBoostedItem.title,
      earlierBoostedItem.title,
    ]);
    expect(within(section!).queryByText(expiredBoostItem.title)).toBeNull();
    expect(within(section!).queryByText(unboostedItem.title)).toBeNull();
    expect(within(section!).queryByText('Được đẩy')).toBeNull();
    expect(within(section!).getAllByText('Cho tặng')).toHaveLength(2);
  });

  it('tải các trang API để Tin nổi bật hiển thị đủ 5 bài đẩy gần nhất', async () => {
    const boostedItems = Array.from({ length: 6 }, (_, index): ItemResponse => ({
      ...item,
      id: 520 + index,
      title: `Bài đẩy số ${index + 1}`,
      createdAt: new Date(Date.UTC(2026, 9, 26 - index, 8)).toISOString(),
      boostedUntil: new Date(Date.UTC(2099, 0, index + 1, 8)).toISOString(),
    }));
    vi.mocked(itemApi.list).mockImplementation(async ({ page = 0 } = {}) => ({
      items: page === 0 ? boostedItems.slice(0, 3) : boostedItems.slice(3),
      page,
      size: 100,
      totalElements: boostedItems.length,
      totalPages: 2,
    }));

    renderHome();

    const heading = await screen.findByRole('heading', { name: 'Tin nổi bật' });
    const section = heading.closest('section');
    expect(section).toBeTruthy();
    await waitFor(() => expect(itemApi.list).toHaveBeenCalledWith({ page: 1, size: 100 }));
    expect(within(section!).getAllByRole('heading', { level: 3 }).map((entry) => entry.textContent)).toEqual([
      boostedItems[5].title,
      boostedItems[4].title,
      boostedItems[3].title,
      boostedItems[2].title,
      boostedItems[1].title,
    ]);
    expect(within(section!).queryByText(boostedItems[0].title)).toBeNull();
  });

  it('banner dùng 8 bài đẩy gần nhất và đổi đúng sản phẩm khi chọn thumbnail', async () => {
    const carouselItems = Array.from({ length: 9 }, (_, index): ItemResponse => ({
      ...item,
      id: 540 + index,
      title: `Bài banner ${index + 1}`,
      images: [`/api/v1/media/${540 + index}`],
      createdAt: new Date(Date.UTC(2026, 9, 10 - index, 8)).toISOString(),
      boostedUntil: new Date(Date.UTC(2099, 0, index + 1, 8)).toISOString(),
    }));
    vi.mocked(itemApi.list).mockImplementation(async ({ page = 0 } = {}) => ({
      items: page === 0 ? carouselItems.slice(0, 3) : carouselItems.slice(3),
      page,
      size: 100,
      totalElements: carouselItems.length,
      totalPages: 2,
    }));

    renderHome();

    const expectedSlides = carouselItems.slice(1).reverse();
    const thumbnails = await screen.findAllByRole('button', { name: /Hiển thị ảnh Bài banner/ });
    expect(thumbnails).toHaveLength(8);
    expect(thumbnails.map((entry) => entry.getAttribute('aria-label'))).toEqual(
      expectedSlides.map((entry) => `Hiển thị ảnh ${entry.title}`),
    );
    expect(screen.getByRole('link', { name: `Xem chi tiết ${expectedSlides[0].title}` })).toBeTruthy();

    const selectedItem = expectedSlides[4];
    fireEvent.click(screen.getByRole('button', { name: `Hiển thị ảnh ${selectedItem.title}` }));

    expect(screen.getByRole('link', { name: `Xem chi tiết ${selectedItem.title}` })).toBeTruthy();
    expect(screen.getByRole('heading', { level: 2, name: selectedItem.title })).toBeTruthy();
    expect(screen.queryByRole('button', { name: `Hiển thị ảnh ${carouselItems[0].title}` })).toBeNull();
  });

  it('LoopAI giữ 3 bài boost gần nhất, 3 gợi ý tương tác và ảnh minh họa riêng', async () => {
    const boostedItems = Array.from({ length: 4 }, (_, index): ItemResponse => ({
      ...item,
      id: 580 + index,
      title: `Bài LoopAI ${index + 1}`,
      images: [`https://example.com/loop-ai-${index + 1}.jpg`],
      boostedUntil: new Date(Date.UTC(2099, 0, index + 1, 8)).toISOString(),
    }));
    vi.mocked(itemApi.list).mockResolvedValue({
      items: boostedItems,
      page: 0,
      size: 100,
      totalElements: boostedItems.length,
      totalPages: 1,
    });

    renderHome();

    const heading = await screen.findByRole('heading', { name: 'Bạn đang tìm món gì?' });
    const section = heading.closest('section');
    expect(section).toBeTruthy();
    await waitFor(() => expect(section!.querySelectorAll('.home-ai-suggestions a')).toHaveLength(3));
    const suggestionLinks = within(section!).getAllByRole('link');
    expect(suggestionLinks.map((link) => link.getAttribute('href'))).toEqual(['/ai', '/ai', '/ai']);
    expect(within(section!).getByText('Đèn bàn để đọc sách')).toBeTruthy();
    expect(within(section!).getByText('Sách cho cuối tuần')).toBeTruthy();
    expect(within(section!).getByText('Đồ dùng góc học tập')).toBeTruthy();
    expect(section!.querySelector<HTMLImageElement>('.home-suggestion-visual img')?.getAttribute('src'))
      .toBe(browseSuggestionBox);
    expect(section!.querySelectorAll('.home-ai-suggestions svg')).toHaveLength(3);
    expect(screen.getByTestId('home-lifecycle-items').textContent).toBe(
      [boostedItems[3].title, boostedItems[2].title, boostedItems[1].title].join('|'),
    );
  });

  it('hiển thị 5 bài trao đổi mới nhất, không ưu tiên boost và không hiện nhãn Được đẩy', async () => {
    const tradeItems = Array.from({ length: 6 }, (_, index): ItemResponse => ({
      ...item,
      id: 560 + index,
      title: `Bài trao đổi ${index + 1}`,
      offerType: 'SWAP',
      desiredItem: 'Sách tham khảo',
      createdAt: new Date(Date.UTC(2026, 9, 20 + index, 8)).toISOString(),
      boostedUntil: index === 2 ? '2099-01-01T08:00:00Z' : null,
    }));
    vi.mocked(itemApi.list).mockResolvedValue({
      items: [tradeItems[2], tradeItems[0], tradeItems[5], tradeItems[1], tradeItems[4], tradeItems[3]],
      page: 0,
      size: 100,
      totalElements: tradeItems.length,
    });

    renderHome();

    const heading = await screen.findByRole('heading', { name: 'Đang tìm người trao đổi' });
    const section = heading.closest('section');
    expect(section).toBeTruthy();
    expect(within(section!).getByRole('link', { name: 'Xem đồ trao đổi' }).getAttribute('href')).toBe(
      '/browse?type=trade',
    );
    expect(within(section!).getAllByRole('heading', { level: 3 }).map((entry) => entry.textContent)).toEqual([
      tradeItems[5].title,
      tradeItems[4].title,
      tradeItems[3].title,
      tradeItems[2].title,
      tradeItems[1].title,
    ]);
    expect(within(section!).queryByText(tradeItems[0].title)).toBeNull();
    expect(within(section!).queryByText('Được đẩy')).toBeNull();
  });

  it('kết thúc loading và hiển thị lỗi khi API items thất bại', async () => {
    const itemsRequest = deferred<PageResponse<ItemResponse>>();
    vi.mocked(itemApi.list).mockReturnValue(itemsRequest.promise);

    renderHome();

    await act(async () => itemsRequest.reject({ message: 'Không tải được danh sách bài đăng.' }));

    expect(await screen.findByText('Không tải được danh sách bài đăng.')).toBeTruthy();
    expect(screen.queryByText('Đang tải bài đăng mới nhất...')).toBeNull();
  });

  it('không để categories hoặc areas chậm chặn bài đăng đã tải xong', async () => {
    const categoriesRequest = deferred<CategoryResponse[]>();
    const areasRequest = deferred<AreaResponse[]>();
    vi.mocked(catalogApi.categories).mockReturnValue(categoriesRequest.promise);
    vi.mocked(catalogApi.areas).mockReturnValue(areasRequest.promise);

    renderHome();

    expect((await screen.findAllByText(item.title)).length).toBeGreaterThan(0);
    expect(screen.queryByText('Đang tải bài đăng mới nhất...')).toBeNull();

    await act(async () => {
      categoriesRequest.resolve(categories);
      areasRequest.resolve(areas);
    });
  });
});
