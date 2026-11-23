// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { catalogApi } from '../../services/catalogApi';
import { itemApi, type ItemResponse } from '../../services/itemApi';
import { Activities } from './Activities';

vi.mock('../../app/hooks', () => ({
  useAppSelector: () => ({ id: 'api_10', name: 'Chủ bài', role: 'user' }),
}));
vi.mock('../../app/store', () => ({ selectCurrentUser: vi.fn() }));
vi.mock('../../app/useWallet', () => ({
  useWallet: () => ({
    wallet: { available: 100, held: 0, total: 100 },
    loading: false,
    error: '',
    refresh: vi.fn(),
    replaceWallet: vi.fn(),
  }),
}));
vi.mock('../../services/catalogApi', () => ({
  catalogApi: { categories: vi.fn(), areas: vi.fn() },
}));
vi.mock('../../services/itemApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/itemApi')>()),
  itemApi: { mine: vi.fn(), getListingFees: vi.fn() },
}));
vi.mock('../../services/requestApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/requestApi')>()),
  requestApi: { mine: vi.fn().mockResolvedValue([]), forItem: vi.fn().mockResolvedValue([]) },
}));

function myItem(overrides: Partial<ItemResponse>): ItemResponse {
  return {
    id: 31,
    donorId: 10,
    categoryId: 7,
    areaId: 1,
    offerType: 'GIVE',
    title: 'Áo khoác gió',
    description: 'Còn tốt.',
    desiredItem: null,
    condition: 'GOOD',
    defectNote: null,
    brand: null,
    images: [],
    defectImages: [],
    operatingStatus: null,
    status: 'APPROVED',
    createdAt: '2026-11-01T08:00:00Z',
    updatedAt: '2026-11-01T08:00:00Z',
    expireAt: new Date(Date.now() + 5 * 24 * 3600 * 1000).toISOString(),
    boostedUntil: null,
    ...overrides,
  };
}

function renderWith(item: ItemResponse) {
  vi.mocked(itemApi.mine).mockResolvedValue([item]);
  vi.mocked(itemApi.getListingFees).mockResolvedValue({ boostDays: 3 } as never);
  render(
    <MemoryRouter initialEntries={['/activities']}>
      <Activities />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.mocked(catalogApi.categories).mockResolvedValue([]);
  vi.mocked(catalogApi.areas).mockResolvedValue([]);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('lý do ẩn và lý do từ chối ở Bài của tôi', () => {
  it('bài bị ẩn vì vi phạm hiện đúng câu backend trả và việc cần làm', async () => {
    // given, when
    renderWith(myItem({ hidden: true, hiddenReason: 'Bài vi phạm quy định. Sửa bài để gửi duyệt lại.' }));

    // then
    expect(await screen.findByText('Bài vi phạm quy định. Sửa bài để gửi duyệt lại.')).toBeTruthy();
    expect(screen.queryByText(/Admin sẽ xem xét và báo kết quả/)).toBeNull();
  });

  it('bài bị ẩn không có lý do thì hiện câu ngắn, không hứa Admin báo kết quả', async () => {
    // given, when
    renderWith(myItem({ hidden: true, hiddenReason: null }));

    // then
    expect(await screen.findByText('Bài đang tạm ẩn khỏi Tìm đồ.')).toBeTruthy();
    expect(screen.queryByText(/Admin sẽ xem xét và báo kết quả/)).toBeNull();
  });

  it('bài bị từ chối hiện lý do Admin đã ghi', async () => {
    // given, when
    renderWith(myItem({ status: 'REJECTED', expireAt: null, rejectionReason: 'Ảnh bị mờ' }));

    // then
    expect(await screen.findByText('Ảnh bị mờ')).toBeTruthy();
  });
});
