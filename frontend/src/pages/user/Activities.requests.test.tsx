// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { catalogApi } from '../../services/catalogApi';
import { itemApi, type ItemResponse } from '../../services/itemApi';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import { Activities } from './Activities';

vi.mock('../../app/hooks', () => ({
  useAppSelector: () => ({ id: 'api_10', name: 'Chủ bài', role: 'user' }),
}));

vi.mock('../../app/store', () => ({
  selectCurrentUser: vi.fn(),
}));

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
  catalogApi: {
    categories: vi.fn(),
    areas: vi.fn(),
  },
}));

vi.mock('../../services/itemApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/itemApi')>()),
  itemApi: {
    mine: vi.fn(),
    getListingFees: vi.fn(),
  },
}));

vi.mock('../../services/requestApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/requestApi')>()),
  requestApi: {
    mine: vi.fn(),
    forItem: vi.fn(),
  },
}));

const myItem: ItemResponse = {
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
  expireAt: '2099-01-01T08:00:00Z',
  boostedUntil: null,
};

const pendingRequest: RequestResponse = {
  id: 501,
  itemId: 31,
  receiverId: 42,
  type: 'GIVE',
  status: 'PENDING',
  createdAt: '2026-11-02T08:00:00Z',
  receiverName: 'Tester 2',
};

beforeEach(() => {
  vi.mocked(catalogApi.categories).mockResolvedValue([
    { id: 7, name: 'Quần áo', parentId: null, restricted: false, boostable: true, sortOrder: 1, activeItemCount: 1 },
  ]);
  vi.mocked(catalogApi.areas).mockResolvedValue([]);
  vi.mocked(itemApi.mine).mockResolvedValue([myItem]);
  vi.mocked(requestApi.mine).mockResolvedValue([]);
  vi.mocked(requestApi.forItem).mockResolvedValue([pendingRequest]);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('danh sách yêu cầu ở Bài của tôi', () => {
  it('ảnh và tên người yêu cầu dẫn tới trang hồ sơ của họ', async () => {
    // given
    render(
      <MemoryRouter>
        <Activities />
      </MemoryRouter>,
    );

    // when: mở danh sách yêu cầu của bài
    fireEvent.click(await screen.findByRole('button', { name: 'Xem 1 yêu cầu' }));

    // then
    expect(screen.getByRole('link', { name: 'Xem hồ sơ của Tester 2' }).getAttribute('href')).toBe('/users/42');
    expect(screen.getByRole('link', { name: 'Tester 2' }).getAttribute('href')).toBe('/users/42');
  });
});
