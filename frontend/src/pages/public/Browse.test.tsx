// @vitest-environment jsdom

import { StrictMode, type ReactNode } from 'react';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import browseBanner from '../../assets/browse-banner-shareloop-dark.png';
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

  it('đổi Grid/List và sắp xếp mà không gọi lại API items', async () => {
    const user = userEvent.setup();
    renderBrowse();

    expect(await screen.findByText(book.title)).toBeTruthy();
    await waitFor(() => expect(itemApi.list).toHaveBeenCalledTimes(1));

    await user.click(screen.getByRole('button', { name: 'Xem dạng danh sách' }));
    expect(screen.getByRole('button', { name: 'Xem dạng danh sách' }).getAttribute('aria-pressed')).toBe('true');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Sắp xếp' }), 'expiring');

    expect(itemApi.list).toHaveBeenCalledTimes(1);
    expect(screen.getByText(book.description)).toBeTruthy();
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
    const titles = () => Array.from(container.querySelectorAll('.browse-product-card h2')).map((node) => node.textContent);
    expect(titles()).toEqual([book.title, expiringBook.title]);

    await user.selectOptions(screen.getByRole('combobox', { name: 'Sắp xếp' }), 'expiring');
    expect(titles()).toEqual([expiringBook.title, book.title]);
    expect(itemApi.list).toHaveBeenCalledTimes(1);
  });

  it('dùng đúng banner Browse đã duyệt', () => {
    const { container } = renderBrowse();
    const banner = container.querySelector<HTMLImageElement>('.browse-banner img');

    expect(banner?.getAttribute('src')).toBe(browseBanner);
    expect(banner?.getAttribute('width')).toBe('2158');
    expect(banner?.getAttribute('height')).toBe('729');
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
