// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
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
  catalogApi: { categories: vi.fn(), areas: vi.fn(), categoryAttributes: vi.fn().mockResolvedValue([]) },
}));
vi.mock('../../services/itemApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/itemApi')>()),
  itemApi: {
    mine: vi.fn(),
    detail: vi.fn(),
    getEditQuote: vi.fn(),
    update: vi.fn(),
    getListingFees: vi.fn(),
  },
}));
vi.mock('../../services/requestApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/requestApi')>()),
  requestApi: { mine: vi.fn().mockResolvedValue([]), forItem: vi.fn().mockResolvedValue([]) },
}));

function approvedItem(overrides: Partial<ItemResponse> = {}): ItemResponse {
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
    images: ['/api/v1/media/1', '/api/v1/media/2', '/api/v1/media/3', '/api/v1/media/4'],
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

async function openEditAndSave(item: ItemResponse) {
  vi.mocked(itemApi.mine).mockResolvedValue([item]);
  vi.mocked(itemApi.detail).mockResolvedValue(item);
  vi.mocked(itemApi.update).mockResolvedValue(item);
  vi.mocked(itemApi.getListingFees).mockResolvedValue({ boostDays: 3 } as never);
  vi.mocked(itemApi.getEditQuote).mockResolvedValue({ free: true, amount: 0, reason: 'FIRST_FREE_EDIT' } as never);
  render(
    <MemoryRouter initialEntries={['/activities']}>
      <Activities />
    </MemoryRouter>,
  );
  fireEvent.click(await screen.findByText('Sửa bài'));
  await screen.findByText(/Lượt sửa này miễn phí/);
  fireEvent.click(await screen.findByText('Lưu thay đổi'));
}

beforeEach(() => {
  vi.mocked(catalogApi.categories).mockResolvedValue([
    { id: 7, name: 'Quần áo', parentId: null, restricted: false, boostable: true, sortOrder: 1, activeItemCount: 1 },
  ]);
  vi.mocked(catalogApi.areas).mockResolvedValue([
    { id: 1, name: 'Quận 1', parentId: null, level: 2, children: [] },
  ]);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('hộp xác nhận gửi bản sửa của bài đã duyệt', () => {
  it('bài đã từng duyệt hiện cảnh báo và chỉ gửi sau khi chủ bài xác nhận', async () => {
    // given, when
    await openEditAndSave(approvedItem());

    // then
    expect(await screen.findByText(/Bài sẽ tạm ẩn khi chờ duyệt/)).toBeTruthy();
    expect(screen.getByText(/Hạn hiển thị còn/)).toBeTruthy();
    expect(itemApi.update).not.toHaveBeenCalled();
    fireEvent.click(screen.getByText('Gửi duyệt lại'));
    await waitFor(() => expect(itemApi.update).toHaveBeenCalledTimes(1));
  });

  it('bài chưa từng duyệt gửi thẳng không hỏi lại', async () => {
    // given, when
    await openEditAndSave(approvedItem({ status: 'PENDING_REVIEW', expireAt: null }));

    // then
    await waitFor(() => expect(itemApi.update).toHaveBeenCalledTimes(1));
    expect(screen.queryByText(/Bài sẽ tạm ẩn khi chờ duyệt/)).toBeNull();
  });
});
