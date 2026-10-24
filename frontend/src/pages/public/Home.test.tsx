// @vitest-environment jsdom

import { StrictMode, type ReactNode } from 'react';
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { catalogApi, type AreaResponse, type CategoryResponse } from '../../services/catalogApi';
import { itemApi, type ItemResponse, type PageResponse } from '../../services/itemApi';
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
  it('dùng chung request items đang chạy khi StrictMode mount lại Home', async () => {
    const itemsRequest = deferred<PageResponse<ItemResponse>>();
    vi.mocked(itemApi.list).mockReturnValue(itemsRequest.promise);

    renderHomeInStrictMode();

    expect(itemApi.list).toHaveBeenCalledTimes(1);
    await act(async () => itemsRequest.resolve(itemPage));
    expect((await screen.findAllByText(item.title)).length).toBeGreaterThan(0);
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
