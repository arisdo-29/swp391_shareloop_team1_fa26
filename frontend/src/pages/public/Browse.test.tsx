// @vitest-environment jsdom

import { StrictMode, type ReactNode } from 'react';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
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
    categoriesWithCounts: vi.fn(),
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
  { id: 7, name: 'Đồ điện tử', parentId: null, restricted: false, boostable: true, sortOrder: 1, activeItemCount: 0 },
  { id: 42, name: longCategoryName, parentId: null, restricted: false, boostable: true, sortOrder: 2, activeItemCount: 0 },
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
  vi.mocked(catalogApi.categoriesWithCounts).mockResolvedValue(categories);
  vi.mocked(catalogApi.areas).mockResolvedValue([]);
  vi.mocked(itemApi.list).mockResolvedValue({ items: [book], page: 0, size: 24, totalElements: 1 });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('lọc danh mục trên trang Tìm đồ', () => {
  it('chỉ hiển thị món có tiêu đề hoặc mô tả khớp từ khóa và đồng bộ tổng số', async () => {
    const lamp: ItemResponse = {
      ...book,
      id: 801,
      title: 'Kệ trang trí kết hợp đèn LED',
      description: 'Ánh sáng dịu nhẹ cho phòng ngủ.',
    };
    const unrelatedBag: ItemResponse = {
      ...book,
      id: 802,
      title: 'Túi đeo chéo CEMMERY',
      description: 'Mẫu túi thời trang nhỏ gọn.',
    };
    const blackShirt: ItemResponse = {
      ...book,
      id: 803,
      title: 'Áo dài nam cách tân',
      description: 'Màu đen phối vàng ánh kim.',
    };
    vi.mocked(itemApi.list).mockResolvedValue({
      items: [unrelatedBag, lamp, blackShirt],
      page: 0,
      size: 24,
      totalElements: 3,
      totalPages: 1,
    });

    renderBrowse(['/browse?q=%C4%91%C3%A8n']);

    expect(await screen.findByText(lamp.title)).toBeTruthy();
    expect(screen.queryByText(unrelatedBag.title)).toBeNull();
    expect(screen.queryByText(blackShirt.title)).toBeNull();
    expect(screen.getByText('1 món')).toBeTruthy();
  });

  it('tìm được tiêu đề khi từ khóa không có dấu tiếng Việt', async () => {
    const lamp: ItemResponse = {
      ...book,
      id: 804,
      title: 'Đèn trang trí để bàn',
      description: 'Ánh sáng vàng dịu.',
    };
    vi.mocked(itemApi.list).mockResolvedValue({
      items: [lamp],
      page: 0,
      size: 24,
      totalElements: 1,
      totalPages: 1,
    });

    renderBrowse(['/browse?q=den%20trang%20tri']);

    expect(await screen.findByText(lamp.title)).toBeTruthy();
    expect(screen.getByText('1 món')).toBeTruthy();
  });

  it('cho phép nhập từ khóa liên tục, giữ focus và chỉ lọc sau debounce', async () => {
    const user = userEvent.setup();
    renderBrowse();

    await waitFor(() => expect(itemApi.list).toHaveBeenCalledTimes(1));
    const searchInput = screen.getByRole('textbox', { name: 'Tìm tên món đồ' }) as HTMLInputElement;

    await user.click(searchInput);
    await user.type(searchInput, 'tai nghe Sony');

    expect(searchInput.value).toBe('tai nghe Sony');
    expect(document.activeElement).toBe(searchInput);
    expect(itemApi.list).toHaveBeenCalledTimes(1);

    await waitFor(() => expect(screen.getByTestId('location').textContent)
      .toBe('/browse?q=tai+nghe+Sony'));
    await waitFor(() => expect(itemApi.list).toHaveBeenLastCalledWith(expect.objectContaining({
      q: 'tai nghe Sony',
      page: 0,
      size: 24,
    })));
    expect(document.activeElement).toBe(searchInput);
  });

  it('dùng chung request items đang chạy khi StrictMode mount lại Browse', async () => {
    let resolveItems!: (value: { items: ItemResponse[]; page: number; size: number; totalElements: number }) => void;
    const itemsRequest = new Promise<{ items: ItemResponse[]; page: number; size: number; totalElements: number }>((resolve) => {
      resolveItems = resolve;
    });
    vi.mocked(itemApi.list).mockReturnValue(itemsRequest);

    renderBrowseInStrictMode();

    expect(itemApi.list).toHaveBeenCalledTimes(1);
    resolveItems({ items: [book], page: 0, size: 24, totalElements: 1 });
    expect(await screen.findByText(book.title)).toBeTruthy();
  });

  it('đổi cách sắp xếp thì gửi sort lên API và lưu vào URL', async () => {
    const user = userEvent.setup();
    renderBrowse();

    expect(await screen.findByText(book.title)).toBeTruthy();
    await waitFor(() => expect(itemApi.list).toHaveBeenCalledTimes(1));

    await user.selectOptions(screen.getByRole('combobox', { name: 'Sắp xếp' }), 'expiring');

    await waitFor(() => expect(itemApi.list).toHaveBeenCalledTimes(2));
    expect(itemApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'expiring', page: 0, size: 24 }));
    expect(screen.getByTestId('location').textContent).toBe('/browse?sort=expiring');
    expect(screen.getByText(book.description)).toBeTruthy();
  });

  it('chế độ tin nổi bật chỉ tải trang hiện tại từ API', async () => {
    const user = userEvent.setup();
    const boostedItems = Array.from({ length: 25 }, (_, index): ItemResponse => ({
      ...book,
      id: 600 + index,
      title: `Bài được đẩy ${index + 1}`,
      boostedUntil: new Date(Date.UTC(2099, 0, index + 1, 8)).toISOString(),
    }));
    vi.mocked(itemApi.list).mockImplementation(async ({ page = 0, size = 24 } = {}) => ({
      items: boostedItems.slice(page * size, (page + 1) * size),
      page,
      size,
      totalElements: boostedItems.length,
      totalPages: Math.ceil(boostedItems.length / size),
    }));

    const { container } = renderBrowse(['/browse?boosted=true']);

    expect(await screen.findByText(boostedItems[23].title)).toBeTruthy();
    expect(itemApi.list).toHaveBeenCalledTimes(1);
    expect(itemApi.list).toHaveBeenCalledWith(expect.objectContaining({ boosted: true, page: 0, size: 24 }));
    expect(container.querySelectorAll('[data-browse-results] > a')).toHaveLength(24);
    expect(screen.getByText('Trang 1 / 2')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Trang sau' }));

    expect(await screen.findByText(boostedItems[24].title)).toBeTruthy();
    await waitFor(() => expect(container.querySelectorAll('[data-browse-results] > a')).toHaveLength(1));
    expect(screen.getByText('Trang 2 / 2')).toBeTruthy();
    expect(itemApi.list).toHaveBeenCalledTimes(2);
    expect(itemApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ boosted: true, page: 1, size: 24 }));
  });

  it('hiển thị đủ 24 món trên mỗi trang trừ trang cuối và chỉ tải trang được xem', async () => {
    const user = userEvent.setup();
    const sourceItems = Array.from({ length: 49 }, (_, index): ItemResponse => ({
      ...book,
      id: 800 + index,
      title: `Pagination item ${index + 1}`,
      createdAt: new Date(Date.UTC(2026, 9, 25, 8, 0, 49 - index)).toISOString(),
    }));
    vi.mocked(itemApi.list).mockImplementation(async ({ page = 0, size = 24 } = {}) => ({
      items: sourceItems.slice(page * size, (page + 1) * size),
      page,
      size,
      totalElements: sourceItems.length,
      totalPages: Math.ceil(sourceItems.length / size),
    }));

    const { container } = renderBrowse();
    const visibleTitles = () => Array.from(container.querySelectorAll('[data-browse-results] > a h3'))
      .map((node) => node.textContent ?? '');
    const titlesAcrossPages: string[] = [];

    expect(await screen.findByText(sourceItems[0].title)).toBeTruthy();
    await waitFor(() => expect(container.querySelectorAll('[data-browse-results] > a')).toHaveLength(24));
    expect(itemApi.list).toHaveBeenCalledTimes(1);
    expect(screen.getByText('Trang 1 / 3')).toBeTruthy();
    titlesAcrossPages.push(...visibleTitles());

    await user.click(screen.getByRole('button', { name: 'Trang sau' }));
    await waitFor(() => expect(container.querySelectorAll('[data-browse-results] > a')).toHaveLength(24));
    expect(screen.getByText('Trang 2 / 3')).toBeTruthy();
    titlesAcrossPages.push(...visibleTitles());

    await user.click(screen.getByRole('button', { name: 'Trang sau' }));
    await waitFor(() => expect(container.querySelectorAll('[data-browse-results] > a')).toHaveLength(1));
    expect(screen.getByText('Trang 3 / 3')).toBeTruthy();
    titlesAcrossPages.push(...visibleTitles());

    expect(new Set(titlesAcrossPages)).toEqual(new Set(sourceItems.map((item) => item.title)));
    expect(titlesAcrossPages).toHaveLength(sourceItems.length);
    expect(itemApi.list).toHaveBeenCalledTimes(3);
    for (let page = 0; page < 3; page += 1) {
      expect(itemApi.list).toHaveBeenCalledWith(expect.objectContaining({ page, size: 24 }));
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
      size: 24,
      totalElements: 1,
    });

    renderBrowse();

    expect(await screen.findByText(boostedBook.title)).toBeTruthy();
    expect(screen.queryByText('Được đẩy')).toBeNull();
  });

  it('hiển thị đúng thứ tự toàn cục do API trả về và F5 giữ chế độ sắp xếp', async () => {
    vi.mocked(itemApi.list).mockImplementation(async ({ sort } = {}) => ({
      items: sort === 'expiring' ? [expiringBook, book] : [book, expiringBook],
      page: 0,
      size: 24,
      totalElements: 2,
    }));
    const user = userEvent.setup();
    const view = renderBrowse();
    const { container } = view;

    expect(await screen.findByText(expiringBook.title)).toBeTruthy();
    const titles = () => Array.from(container.querySelectorAll('[data-browse-results] > a h3')).map((node) => node.textContent);
    expect(titles()).toEqual([book.title, expiringBook.title]);

    await user.selectOptions(screen.getByRole('combobox', { name: 'Sắp xếp' }), 'expiring');
    await waitFor(() => expect(titles()).toEqual([expiringBook.title, book.title]));
    expect(screen.getByTestId('location').textContent).toBe('/browse?sort=expiring');

    view.unmount();
    renderBrowse(['/browse?sort=expiring']);
    expect((screen.getByRole('combobox', { name: 'Sắp xếp' }) as HTMLSelectElement).value).toBe('expiring');
    await waitFor(() => expect(itemApi.list).toHaveBeenCalledTimes(3));
    await waitFor(() => expect(itemApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'expiring' })));
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

  it('tìm từ Home giữ nguyên từ khóa và gửi đúng areaId của quận tới API', async () => {
    vi.mocked(catalogApi.areas).mockResolvedValue([
      {
        id: 1,
        name: 'TP. Hồ Chí Minh',
        parentId: null,
        level: 1,
        children: [
          { id: 2, name: 'Quận 1', parentId: 1, level: 2, children: [] },
          { id: 3, name: 'Quận 3', parentId: 1, level: 2, children: [] },
          { id: 4, name: 'Quận 5', parentId: 1, level: 2, children: [] },
        ],
      },
    ]);
    const districtBook: ItemResponse = {
      ...book,
      id: 701,
      areaId: 3,
      title: 'Tai nghe Sony tại Quận 3',
    };
    const otherDistrictBook: ItemResponse = {
      ...book,
      id: 702,
      areaId: 2,
      title: 'Tai nghe tại Quận 1',
    };
    vi.mocked(itemApi.list).mockImplementation(async (params = {}) => ({
      items: params.areaId === 3 && params.q === 'tai nghe Sony'
        ? [districtBook]
        : [otherDistrictBook],
      page: params.page ?? 0,
      size: params.size ?? 24,
      totalElements: 1,
      totalPages: 1,
    }));
    const user = userEvent.setup();
    const view = render(
      <MemoryRouter initialEntries={['/']}>
        <LocationState />
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/browse" element={<Browse />} />
        </Routes>
      </MemoryRouter>,
    );

    await user.type(screen.getByRole('textbox', { name: 'Từ khóa tìm kiếm' }), 'tai nghe Sony');
    await user.selectOptions(await screen.findByRole('combobox', { name: 'Chọn quận' }), '3');
    await user.click(screen.getByRole('button', { name: 'Tìm đồ' }));

    await waitFor(() => expect(screen.getByTestId('location').textContent)
      .toBe('/browse?q=tai%20nghe%20Sony&areaId=3'));
    await waitFor(() => expect(itemApi.list).toHaveBeenCalledWith(expect.objectContaining({
      areaId: 3,
      q: 'tai nghe Sony',
      page: 0,
      size: 24,
    })));
    const districtSelect = await screen.findByRole('combobox', { name: 'Quận' });
    await waitFor(() => expect((districtSelect as HTMLSelectElement).value).toBe('3'));
    expect((screen.getByRole('textbox', { name: 'Tìm tên món đồ' }) as HTMLInputElement).value)
      .toBe('tai nghe Sony');
    expect(await screen.findByText(districtBook.title)).toBeTruthy();
    expect(screen.queryByText(otherDistrictBook.title)).toBeNull();

    view.unmount();
    vi.clearAllMocks();
    renderBrowse(['/browse?q=tai%20nghe%20Sony&areaId=3']);

    const reloadedDistrictSelect = await screen.findByRole('combobox', { name: 'Quận' });
    await waitFor(() => expect((reloadedDistrictSelect as HTMLSelectElement).value).toBe('3'));
    expect((screen.getByRole('textbox', { name: 'Tìm tên món đồ' }) as HTMLInputElement).value)
      .toBe('tai nghe Sony');
    await waitFor(() => expect(itemApi.list).toHaveBeenCalledWith(expect.objectContaining({
      areaId: 3,
      q: 'tai nghe Sony',
      page: 0,
      size: 24,
    })));
    expect(await screen.findByText(districtBook.title)).toBeTruthy();
    expect(screen.queryByText(otherDistrictBook.title)).toBeNull();
  });

  it.each([
    [2, 'Quận 1'],
    [4, 'Quận 5'],
  ])('click Tìm đồ từ Home chuyển sang Browse với areaId=%i (%s)', async (areaId) => {
    vi.mocked(catalogApi.areas).mockResolvedValue([
      {
        id: 1,
        name: 'TP. Hồ Chí Minh',
        parentId: null,
        level: 1,
        children: [
          { id: 2, name: 'Quận 1', parentId: 1, level: 2, children: [] },
          { id: 3, name: 'Quận 3', parentId: 1, level: 2, children: [] },
          { id: 4, name: 'Quận 5', parentId: 1, level: 2, children: [] },
        ],
      },
    ]);
    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/']}>
        <LocationState />
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/browse" element={<Browse />} />
        </Routes>
      </MemoryRouter>,
    );

    await user.selectOptions(await screen.findByRole('combobox', { name: 'Chọn quận' }), String(areaId));
    await user.click(screen.getByRole('button', { name: 'Tìm đồ' }));

    await waitFor(() => expect(screen.getByTestId('location').textContent)
      .toBe(`/browse?q=&areaId=${areaId}`));
    await waitFor(() => expect(itemApi.list).toHaveBeenCalledWith(expect.objectContaining({
      areaId,
      q: undefined,
      page: 0,
      size: 24,
    })));
  });

  it('chuẩn hóa URL district cũ sang areaId và vẫn gửi đúng bộ lọc khi tải lại', async () => {
    vi.mocked(catalogApi.areas).mockResolvedValue([
      {
        id: 1,
        name: 'TP. Hồ Chí Minh',
        parentId: null,
        level: 1,
        children: [{ id: 3, name: 'Quận 3', parentId: 1, level: 2, children: [] }],
      },
    ]);

    renderBrowse(['/browse?q=tai%20nghe&district=3']);

    await waitFor(() => expect(screen.getByTestId('location').textContent)
      .toBe('/browse?q=tai+nghe&areaId=3'));
    const districtSelect = await screen.findByRole('combobox', { name: 'Quận' });
    await waitFor(() => expect((districtSelect as HTMLSelectElement).value).toBe('3'));
    await waitFor(() => expect(itemApi.list).toHaveBeenCalledWith(expect.objectContaining({
      areaId: 3,
      q: 'tai nghe',
      page: 0,
      size: 24,
    })));
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
      size: 24,
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
      size: 24,
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
