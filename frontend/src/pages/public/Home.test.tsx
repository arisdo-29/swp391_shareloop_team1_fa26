// @vitest-environment jsdom

import { StrictMode, type ReactNode } from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
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
    categoriesWithCounts: vi.fn(),
    areas: vi.fn(),
  },
}));

vi.mock('../../services/itemApi', () => ({
  itemApi: {
    list: vi.fn(),
    featured: vi.fn(),
  },
}));

// Nhóm banner do backend trả; mốc đổi nhóm 3 phút sau để timer không chạy trong các test này.
function featuredResponse(items: ItemResponse[]) {
  const now = Date.now();
  return {
    items,
    rotatedAt: new Date(now).toISOString(),
    nextRotateAt: new Date(now + 180_000).toISOString(),
  };
}

const categories: CategoryResponse[] = [
  { id: 7, name: 'Sách', parentId: null, restricted: false, boostable: true, sortOrder: 1, activeItemCount: 3 },
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

function LocationState() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

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
      <LocationState />
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
  vi.mocked(catalogApi.categoriesWithCounts).mockResolvedValue(categories);
  vi.mocked(catalogApi.areas).mockResolvedValue(areas);
  vi.mocked(itemApi.list).mockResolvedValue(itemPage);
  vi.mocked(itemApi.featured).mockResolvedValue(featuredResponse([]));
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
    expect(searchButton.getAttribute('type')).toBe('submit');
    expect(searchButton.querySelector('svg')).toBeNull();
    expect(areaSelect.classList.contains('appearance-none')).toBe(true);
    expect(areaSelect.querySelector('option')).toBeTruthy();
  });

  it('giữ trang đăng món trong next khi khách chọn Đăng món đồ', () => {
    renderHome();

    fireEvent.click(screen.getByRole('button', { name: 'Đăng món đồ' }));

    expect(screen.getByTestId('location').textContent).toBe('/login?next=%2Fpost');
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

  it('hiển thị totalElements từ cùng bộ lọc danh mục của trang Tìm đồ và giữ link quận không kèm số đếm', async () => {
    vi.mocked(itemApi.list).mockImplementation(async (params = {}) => {
      if (params.categoryId === 7) {
        return { items: [], page: 0, size: 24, totalElements: 37, totalPages: 2 };
      }
      return itemPage;
    });
    const { container } = renderHome();

    await waitFor(() => expect(container.querySelector('.home-category-count')).toBeTruthy());
    expect(container.querySelector('.home-category-count')?.textContent).toBe('37 món đồ');
    expect(itemApi.list).toHaveBeenCalledWith({ categoryId: 7, page: 0, size: 24 });
    const districtLink = container.querySelector('.home-district');
    expect(districtLink).toBeTruthy();
    expect(districtLink?.getAttribute('href')).toBe('/browse?areaId=1');
    expect(districtLink?.textContent).not.toContain('món đang có');
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

  it('Tin nổi bật hiện đúng nhóm bài do /items/featured trả, giữ thứ tự backend và không có nhãn Được đẩy', async () => {
    // given: backend trả nhóm 2 bài theo thứ tự xoay
    const firstInRotation: ItemResponse = { ...item, id: 507, title: 'Bài đầu vòng', boostedUntil: '2099-11-20T08:00:00Z' };
    const secondInRotation: ItemResponse = { ...item, id: 506, title: 'Bài thứ hai', boostedUntil: '2099-10-20T08:00:00Z' };
    vi.mocked(itemApi.featured).mockResolvedValue(featuredResponse([firstInRotation, secondInRotation]));

    // when
    renderHome();

    // then: gọi banner 5 bài kèm viewerKey của khách; không sắp lại theo boostedUntil
    const heading = await screen.findByRole('heading', { name: 'Tin nổi bật' });
    const section = heading.closest('section');
    expect(section).toBeTruthy();
    await waitFor(() =>
      expect(within(section!).getAllByRole('heading', { level: 3 }).map((entry) => entry.textContent)).toEqual([
        firstInRotation.title,
        secondInRotation.title,
      ]),
    );
    expect(itemApi.featured).toHaveBeenCalledWith({ limit: 5, viewerKey: expect.any(Number) });
    expect(within(section!).getByRole('link', { name: 'Xem tất cả' }).getAttribute('href')).toBe(
      '/browse?boosted=true',
    );
    const newItemsSection = screen.getByRole('heading', { name: 'Món đồ mới' }).closest('section');
    expect(section!.querySelector('.home-product-grid')?.className).toContain(
      newItemsSection!.querySelector('.home-product-grid')?.className,
    );
    expect(within(section!).queryByText('Được đẩy')).toBeNull();
  });

  it('không tải nhiều trang 100 bài để lọc bài đẩy ở trình duyệt', async () => {
    // when
    renderHome();
    await screen.findByRole('heading', { name: 'Tin nổi bật' });

    // then: danh sách thường chỉ tải một trang; bài đẩy lấy từ featured
    await waitFor(() => expect(itemApi.featured).toHaveBeenCalled());
    expect(itemApi.list).not.toHaveBeenCalledWith({ page: 1, size: 100 });
  });

  it('banner dùng nhóm bài featured theo thứ tự backend và đổi đúng sản phẩm khi chọn thumbnail', async () => {
    // given: nhóm 5 bài có ảnh
    const carouselItems = Array.from({ length: 5 }, (_, index): ItemResponse => ({
      ...item,
      id: 540 + index,
      title: `Bài banner ${index + 1}`,
      images: [`/api/v1/media/${540 + index}`],
      boostedUntil: new Date(Date.UTC(2099, 0, 5 - index, 8)).toISOString(),
    }));
    vi.mocked(itemApi.featured).mockResolvedValue(featuredResponse(carouselItems));

    // when
    renderHome();

    // then
    const thumbnails = await screen.findAllByRole('button', { name: /Hiển thị ảnh Bài banner/ });
    expect(thumbnails.map((entry) => entry.getAttribute('aria-label'))).toEqual(
      carouselItems.map((entry) => `Hiển thị ảnh ${entry.title}`),
    );
    expect(screen.getByRole('link', { name: `Xem chi tiết ${carouselItems[0].title}` })).toBeTruthy();

    const selectedItem = carouselItems[3];
    fireEvent.click(screen.getByRole('button', { name: `Hiển thị ảnh ${selectedItem.title}` }));

    expect(screen.getByRole('link', { name: `Xem chi tiết ${selectedItem.title}` })).toBeTruthy();
    expect(screen.getByRole('heading', { level: 2, name: selectedItem.title })).toBeTruthy();
  });

  it('LoopAI dùng 3 bài đầu của nhóm featured, 3 gợi ý tương tác và ảnh minh họa riêng', async () => {
    const boostedItems = Array.from({ length: 4 }, (_, index): ItemResponse => ({
      ...item,
      id: 580 + index,
      title: `Bài LoopAI ${index + 1}`,
      images: [`https://example.com/loop-ai-${index + 1}.jpg`],
      boostedUntil: new Date(Date.UTC(2099, 0, index + 1, 8)).toISOString(),
    }));
    vi.mocked(itemApi.featured).mockResolvedValue(featuredResponse(boostedItems));

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
    expect(section!.querySelectorAll('.home-ai-suggestions svg')).toHaveLength(0);
    expect(screen.getByTestId('home-lifecycle-items').textContent).toBe(
      [boostedItems[0].title, boostedItems[1].title, boostedItems[2].title].join('|'),
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
    vi.mocked(catalogApi.categoriesWithCounts).mockReturnValue(categoriesRequest.promise);
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
