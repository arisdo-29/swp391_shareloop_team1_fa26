// @vitest-environment jsdom

import { StrictMode, type ReactNode } from 'react';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { catalogApi, type CategoryResponse } from '../../services/catalogApi';
import { itemApi, type ItemResponse } from '../../services/itemApi';
import { categoryBrowsePath } from '../../utils/browseFilters';
import { Browse } from './Browse';
import { Home } from './Home';

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
  HomeLifecycle: () => null,
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

const longCategoryName = 'Sách, truyện, giáo trình, văn phòng phẩm';
const categories: CategoryResponse[] = [
  { id: 7, name: 'Đồ điện tử', parentId: null, restricted: false, boostable: true, sortOrder: 1 },
  { id: 42, name: longCategoryName, parentId: null, restricted: false, boostable: true, sortOrder: 2 },
];

const book: ItemResponse = {
  id: 501,
  donorId: 9,
  categoryId: 42,
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

const expiringBook: ItemResponse = {
  ...book,
  id: 502,
  title: 'Sách sắp hết hạn hiển thị',
  createdAt: '2026-10-10T08:00:00Z',
  expireAt: '2026-10-25T08:00:00Z',
};

function LocationState() {
  const location = useLocation();
  const navigate = useNavigate();
  return (
    <>
      <output data-testid="location">{location.pathname}{location.search}</output>
      <button type="button" onClick={() => navigate(-1)}>Quay lại lịch sử</button>
      <button type="button" onClick={() => navigate(1)}>Đi tới lịch sử</button>
    </>
  );
}

function renderBrowse(initialEntries = ['/browse'], initialIndex?: number) {
  return render(
    <MemoryRouter initialEntries={initialEntries} initialIndex={initialIndex}>
      <LocationState />
      <Browse />
    </MemoryRouter>,
  );
}

function renderBrowseInStrictMode() {
  return render(
    <StrictMode>
      <MemoryRouter initialEntries={['/browse']}>
        <Browse />
      </MemoryRouter>
    </StrictMode>,
  );
}

beforeEach(() => {
  vi.mocked(catalogApi.categories).mockResolvedValue(categories);
  vi.mocked(catalogApi.areas).mockResolvedValue([]);
  vi.mocked(itemApi.list).mockResolvedValue({ items: [book], page: 0, size: 20, totalElements: 1 });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('lọc danh mục trên trang Tìm đồ', () => {
  it('dùng chung request items đang chạy khi StrictMode mount lại Browse', async () => {
    let resolveItems!: (value: { items: ItemResponse[]; page: number; size: number; totalElements: number }) => void;
    const itemsRequest = new Promise<{ items: ItemResponse[]; page: number; size: number; totalElements: number }>((resolve) => {
      resolveItems = resolve;
    });
    vi.mocked(itemApi.list).mockReturnValue(itemsRequest);

    renderBrowseInStrictMode();

    expect(itemApi.list).toHaveBeenCalledTimes(1);
    resolveItems({ items: [book], page: 0, size: 20, totalElements: 1 });
    expect(await screen.findByText(book.title)).toBeTruthy();
  });

  it('đổi cách sắp xếp mà không gọi lại API items', async () => {
    const user = userEvent.setup();
    renderBrowse();

    expect(await screen.findByText(book.title)).toBeTruthy();
    await waitFor(() => expect(itemApi.list).toHaveBeenCalledTimes(1));

    await user.selectOptions(screen.getByRole('combobox', { name: 'Sắp xếp' }), 'expiring');

    expect(itemApi.list).toHaveBeenCalledTimes(1);
    expect(screen.getByText(book.description)).toBeTruthy();
  });

  it('hiển thị đầy đủ bài đang được đẩy từ mọi trang API và phân trang kết quả', async () => {
    const user = userEvent.setup();
    const boostedItems = Array.from({ length: 21 }, (_, index): ItemResponse => ({
      ...book,
      id: 600 + index,
      title: `Bài được đẩy ${index + 1}`,
      boostedUntil: new Date(Date.UTC(2099, 0, index + 1, 8)).toISOString(),
    }));
    const expiredBoostItem: ItemResponse = {
      ...book,
      id: 700,
      title: 'Bài hết hiệu lực đẩy',
      boostedUntil: '2020-01-01T08:00:00Z',
    };
    const unboostedItem: ItemResponse = {
      ...book,
      id: 701,
      title: 'Bài chưa được đẩy',
    };
    const sourceItems = [...boostedItems, expiredBoostItem, unboostedItem];
    vi.mocked(itemApi.list).mockImplementation(async ({ page = 0, size = 20 } = {}) => ({
      items: sourceItems.slice(page * size, (page + 1) * size),
      page,
      size,
      totalElements: sourceItems.length,
      totalPages: Math.ceil(sourceItems.length / size),
    }));

    const { container } = renderBrowse(['/browse?boosted=true']);

    expect(await screen.findByText(boostedItems[20].title)).toBeTruthy();
    await waitFor(() => {
      expect(itemApi.list).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 20 }));
      expect(itemApi.list).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 20 }));
    });
    expect(container.querySelectorAll('[data-browse-results] > a')).toHaveLength(20);
    expect(screen.queryByText(expiredBoostItem.title)).toBeNull();
    expect(screen.queryByText(unboostedItem.title)).toBeNull();
    expect(screen.queryByText('Được đẩy')).toBeNull();
    expect(screen.getByText('Trang 1 / 2')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Trang sau' }));

    expect(await screen.findByText(boostedItems[0].title)).toBeTruthy();
    await waitFor(() => expect(container.querySelectorAll('[data-browse-results] > a')).toHaveLength(1));
    expect(screen.getByText('Trang 2 / 2')).toBeTruthy();
  });

  it('repaginates uneven API pages into complete pages of 20 without gaps or duplicates', async () => {
    const user = userEvent.setup();
    const sourceItems = Array.from({ length: 45 }, (_, index): ItemResponse => ({
      ...book,
      id: 800 + index,
      title: `Pagination item ${index + 1}`,
      createdAt: new Date(Date.UTC(2026, 9, 25, 8, 0, 45 - index)).toISOString(),
    }));
    const apiPages = [
      sourceItems.slice(0, 20),
      [sourceItems[19], ...sourceItems.slice(20, 24)],
      sourceItems.slice(24, 44),
      sourceItems.slice(44),
    ];
    vi.mocked(itemApi.list).mockImplementation(async ({ page = 0, size = 20 } = {}) => ({
      items: apiPages[page] ?? [],
      page,
      size,
      totalElements: sourceItems.length,
      totalPages: apiPages.length,
    }));

    const { container } = renderBrowse();
    const visibleTitles = () => Array.from(container.querySelectorAll('[data-browse-results] > a h3'))
      .map((node) => node.textContent ?? '');
    const titlesAcrossPages: string[] = [];

    expect(await screen.findByText(sourceItems[0].title)).toBeTruthy();
    await waitFor(() => expect(container.querySelectorAll('[data-browse-results] > a')).toHaveLength(20));
    expect(screen.getByText('Trang 1 / 3')).toBeTruthy();
    titlesAcrossPages.push(...visibleTitles());

    await user.click(screen.getByRole('button', { name: 'Trang sau' }));
    await waitFor(() => expect(container.querySelectorAll('[data-browse-results] > a')).toHaveLength(20));
    expect(screen.getByText('Trang 2 / 3')).toBeTruthy();
    titlesAcrossPages.push(...visibleTitles());

    await user.click(screen.getByRole('button', { name: 'Trang sau' }));
    await waitFor(() => expect(container.querySelectorAll('[data-browse-results] > a')).toHaveLength(5));
    expect(screen.getByText('Trang 3 / 3')).toBeTruthy();
    titlesAcrossPages.push(...visibleTitles());

    expect(new Set(titlesAcrossPages)).toEqual(new Set(sourceItems.map((item) => item.title)));
    expect(titlesAcrossPages).toHaveLength(sourceItems.length);
    for (let page = 0; page < apiPages.length; page += 1) {
      expect(itemApi.list).toHaveBeenCalledWith(expect.objectContaining({ page, size: 20 }));
    }
  });

  it('ẩn nhãn Được đẩy trên trang Browse thông thường', async () => {
    const boostedBook: ItemResponse = {
      ...book,
      boostedUntil: '2099-01-01T08:00:00Z',
    };
    vi.mocked(itemApi.list).mockResolvedValue({
      items: [boostedBook],
      page: 0,
      size: 20,
      totalElements: 1,
    });

    renderBrowse();

    expect(await screen.findByText(boostedBook.title)).toBeTruthy();
    expect(screen.queryByText('Được đẩy')).toBeNull();
  });

  it('sắp xếp danh sách hiện tại mà không thay dữ liệu hoặc gọi API mới', async () => {
    vi.mocked(itemApi.list).mockResolvedValue({
      items: [expiringBook, book],
      page: 0,
      size: 20,
      totalElements: 2,
    });
    const user = userEvent.setup();
    const { container } = renderBrowse();

    expect(await screen.findByText(expiringBook.title)).toBeTruthy();
    const titles = () => Array.from(container.querySelectorAll('[data-browse-results] > a h3')).map((node) => node.textContent);
    expect(titles()).toEqual([book.title, expiringBook.title]);

    await user.selectOptions(screen.getByRole('combobox', { name: 'Sắp xếp' }), 'expiring');
    expect(titles()).toEqual([expiringBook.title, book.title]);
    expect(itemApi.list).toHaveBeenCalledTimes(1);
  });

  it('nút áp dụng giữ bộ lọc tức thời và đóng bảng lọc trên mobile', async () => {
    const user = userEvent.setup();
    renderBrowse();

    const toggle = screen.getByRole('button', { name: 'Bộ lọc' });
    await user.click(toggle);
    expect(toggle.getAttribute('aria-expanded')).toBe('true');

    await user.click(screen.getByRole('button', { name: 'Áp dụng bộ lọc' }));
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
  });

  it('tạo URL danh mục bằng category ID', () => {
    expect(categoryBrowsePath(42)).toBe('/browse?categoryId=42');
  });

  it('click danh mục trên Home chuyển sang URL dùng đúng category ID', async () => {
    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <LocationState />
        <Home />
      </MemoryRouter>,
    );

    const categoryLinks = await screen.findAllByRole('link', { name: new RegExp(longCategoryName) });
    const discoveryLink = categoryLinks.find((link) => link.getAttribute('href') === '/browse?categoryId=42');
    expect(discoveryLink).toBeTruthy();
    await user.click(discoveryLink!);
    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/browse?categoryId=42'));
  });

  it('đọc categoryId từ URL, tự chọn tên đầy đủ và gửi đúng ID tới API', async () => {
    renderBrowse(['/browse?categoryId=42']);

    const categorySelect = await screen.findByRole('combobox', { name: 'Danh mục' });
    await waitFor(() => expect((categorySelect as HTMLSelectElement).value).toBe('42'));
    expect(screen.getAllByText(longCategoryName).length).toBeGreaterThanOrEqual(2);
    expect(categorySelect.getAttribute('title')).toBe(longCategoryName);
    await waitFor(() => expect(itemApi.list).toHaveBeenCalledWith({
      categoryId: 42,
      areaId: undefined,
      q: undefined,
      page: 0,
      size: 20,
    }));
    expect(await screen.findByText('Bộ giáo trình đại học')).toBeTruthy();
  });

  it('gửi hình thức và tình trạng từ URL lên API, không lọc lại phía trình duyệt', async () => {
    // book có tình trạng GOOD nhưng URL lọc NEW: server đã lọc nên trang vẫn hiện đúng những gì API trả về
    renderBrowse(['/browse?type=gift&condition=NEW']);

    await waitFor(() => expect(itemApi.list).toHaveBeenCalledWith({
      categoryId: undefined,
      areaId: undefined,
      q: undefined,
      condition: 'NEW',
      offerType: 'GIVE',
      page: 0,
      size: 20,
    }));
    expect(await screen.findByText(book.title)).toBeTruthy();
  });

  it('đổi hình thức và tình trạng thì gọi lại API với offerType và condition', async () => {
    const user = userEvent.setup();
    renderBrowse();
    await screen.findByText(book.title);

    await user.click(screen.getByRole('button', { name: 'Trao đổi' }));
    await waitFor(() => expect(itemApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ offerType: 'SWAP' })));

    await user.click(screen.getByRole('button', { name: 'Như mới' }));
    await waitFor(() => expect(itemApi.list).toHaveBeenLastCalledWith(
      expect.objectContaining({ offerType: 'SWAP', condition: 'LIKE_NEW', page: 0 }),
    ));
  });

  it('không hiện id kỹ thuật khi không tìm thấy tên danh mục', async () => {
    renderBrowse(['/browse?categoryId=9999']);

    await waitFor(() => expect(itemApi.list).toHaveBeenCalledWith(expect.objectContaining({ categoryId: 9999 })));
    await screen.findByText(book.title);
    expect(screen.queryByText(/Danh mục #/)).toBeNull();
  });

  it('đổi danh mục và đặt lại đồng bộ URL lẫn request API', async () => {
    const user = userEvent.setup();
    renderBrowse();
    const categorySelect = await screen.findByRole('combobox', { name: 'Danh mục' });

    await user.selectOptions(categorySelect, '42');
    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/browse?categoryId=42'));
    await waitFor(() => expect(itemApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ categoryId: 42 })));

    await user.click(screen.getByRole('button', { name: 'Đặt lại' }));
    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/browse'));
    expect((categorySelect as HTMLSelectElement).value).toBe('');
    await waitFor(() => expect(itemApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ categoryId: undefined })));
  });

  it('khôi phục đúng danh mục khi dùng Back và Forward', async () => {
    const user = userEvent.setup();
    renderBrowse(['/browse?categoryId=42', '/browse?categoryId=7'], 1);
    const categorySelect = await screen.findByRole('combobox', { name: 'Danh mục' });
    await waitFor(() => expect((categorySelect as HTMLSelectElement).value).toBe('7'));

    await user.click(screen.getByRole('button', { name: 'Quay lại lịch sử' }));
    await waitFor(() => expect((categorySelect as HTMLSelectElement).value).toBe('42'));
    await waitFor(() => expect(itemApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ categoryId: 42 })));

    await user.click(screen.getByRole('button', { name: 'Đi tới lịch sử' }));
    await waitFor(() => expect((categorySelect as HTMLSelectElement).value).toBe('7'));
    await waitFor(() => expect(itemApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ categoryId: 7 })));
  });
});
