// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { toast } from '../../components/toast';
import { catalogApi } from '../../services/catalogApi';
import { itemApi, type BoostQuote, type ItemResponse } from '../../services/itemApi';
import { formatDateTime } from '../../utils/boostMessages';
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
    getBoostQuote: vi.fn(),
    getListingFees: vi.fn(),
    boostItem: vi.fn(),
    remove: vi.fn(),
    getRenewQuote: vi.fn(),
  },
}));

vi.mock('../../services/requestApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../services/requestApi')>()),
  requestApi: {
    mine: vi.fn().mockResolvedValue([]),
    forItem: vi.fn().mockResolvedValue([]),
  },
}));

const HOUR = 60 * 60 * 1000;

function hoursFromNow(hours: number) {
  return new Date(Date.now() + hours * HOUR).toISOString();
}

function myItem(overrides: Partial<ItemResponse> = {}): ItemResponse {
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
    expireAt: hoursFromNow(24 * 30),
    boostedUntil: null,
    ...overrides,
  };
}

function renderActivities(items: ItemResponse[]) {
  vi.mocked(itemApi.mine).mockResolvedValue(items);
  return render(
    <MemoryRouter>
      <Activities />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.mocked(catalogApi.categories).mockResolvedValue([
    { id: 7, name: 'Quần áo', parentId: null, restricted: false, boostable: true, sortOrder: 1, activeItemCount: 1 },
  ]);
  vi.mocked(catalogApi.areas).mockResolvedValue([]);
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.restoreAllMocks();
});

describe('trạng thái đẩy ở Bài của tôi', () => {
  it('bài đang đẩy hiện "Đang đẩy" kèm mốc kết thúc và mốc mua thêm', async () => {
    // given
    const boostedUntil = hoursFromNow(30);
    const chainEndsAt = hoursFromNow(100);
    renderActivities([myItem({ boostedUntil, boostChainEndsAt: chainEndsAt })]);

    // then
    expect(await screen.findByText(new RegExp(`Đang đẩy · còn .* · kết thúc ${escapeRegExp(formatDateTime(boostedUntil))}`))).toBeTruthy();
    expect(screen.getByText(`Mua thêm được tới ${formatDateTime(chainEndsAt)}`)).toBeTruthy();
  });

  it('bài đang đẩy không hiện gợi ý "Đẩy bài để nhiều người thấy hơn"', async () => {
    // given
    renderActivities([myItem({ boostedUntil: hoursFromNow(30) })]);

    // when
    await screen.findByText(/Đang đẩy ·/);

    // then
    expect(screen.getByText('Chưa có ai gửi yêu cầu.')).toBeTruthy();
    expect(screen.queryByText('Đẩy bài để nhiều người thấy hơn.')).toBeNull();
  });

  it('bài đã hết đẩy vẫn hiện gợi ý "Đẩy bài để nhiều người thấy hơn"', async () => {
    // given
    renderActivities([myItem({ boostedUntil: hoursFromNow(-2) })]);

    // then
    expect(await screen.findByText('Đẩy bài để nhiều người thấy hơn.')).toBeTruthy();
  });

  it('bài đang nghỉ hiện "Được đẩy lại từ"', async () => {
    // given
    const cooldownUntil = hoursFromNow(40);
    renderActivities([myItem({ boostedUntil: hoursFromNow(-2), boostCooldownUntil: cooldownUntil })]);

    // then
    expect(await screen.findByText(`Được đẩy lại từ ${formatDateTime(cooldownUntil)}`)).toBeTruthy();
    expect(screen.queryByText(/Đang đẩy ·/)).toBeNull();
  });
});

describe('gỡ bài đang được đẩy', () => {
  it('hiện cảnh báo mất thời gian đẩy còn lại và không hoàn phí', async () => {
    // given
    const boostedUntil = hoursFromNow(30);
    renderActivities([myItem({ boostedUntil })]);

    // when
    fireEvent.click(await screen.findByRole('button', { name: 'Gỡ bài' }));

    // then
    expect(
      screen.getByText(
        `Bài đang được đẩy tới ${formatDateTime(boostedUntil)}. Gỡ bài sẽ mất thời gian đẩy còn lại và không được hoàn phí.`,
      ),
    ).toBeTruthy();
  });

  it('bài không đang đẩy thì không hiện cảnh báo', async () => {
    // given
    renderActivities([myItem({ boostedUntil: hoursFromNow(-5) })]);

    // when
    fireEvent.click(await screen.findByRole('button', { name: 'Gỡ bài' }));

    // then
    expect(screen.getByRole('heading', { name: 'Gỡ bài đăng?' })).toBeTruthy();
    expect(screen.queryByText(/Gỡ bài sẽ mất thời gian đẩy còn lại/)).toBeNull();
  });
});

describe('đẩy bài từ Bài của tôi', () => {
  it('xác nhận xong gọi POST boost và báo hạn đẩy cùng số dư từ response', async () => {
    // given: báo giá cho phép, backend trả hạn đẩy và số dư mới
    const quote: BoostQuote = {
      fee: 5,
      newBoostedUntil: hoursFromNow(72),
      allowed: true,
      reason: null,
      reasonCode: null,
      boostStartedAt: hoursFromNow(0),
      chainEndsAt: hoursFromNow(144),
      cooldownUntil: hoursFromNow(144),
      extending: false,
      slotsLeft: 10,
      earliestSlotAt: null,
      userActiveBoosts: 0,
      userEarliestBoostEndAt: null,
      expireAt: hoursFromNow(24 * 30),
    };
    vi.mocked(itemApi.getBoostQuote).mockResolvedValue(quote);
    vi.mocked(itemApi.getListingFees).mockResolvedValue({
      postFeeSwap: 5,
      postFeeGive: 0,
      editFee: 5,
      renewFee: 5,
      boostFee: 5,
      boostDays: 3,
      boostMaxDays: 6,
      postDisplayDays: 30,
      postDisplayDaysGold: 45,
    });
    vi.mocked(itemApi.boostItem).mockResolvedValue({ itemId: 31, boostedUntil: quote.newBoostedUntil, creditBalance: 15 });
    const successToast = vi.spyOn(toast, 'success');
    renderActivities([myItem()]);

    // when
    fireEvent.click(await screen.findByRole('button', { name: 'Đẩy bài' }));
    const dialog = await screen.findByRole('dialog');
    await within(dialog).findByText('3 ngày');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Xác nhận đẩy 5 Credit' }));

    // then
    await vi.waitFor(() =>
      expect(successToast).toHaveBeenCalledWith(
        `Đã đẩy bài đến ${formatDateTime(quote.newBoostedUntil)}. Số dư: 15 Credit.`,
      ),
    );
    expect(itemApi.boostItem).toHaveBeenCalledWith(31);
  });
});

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
