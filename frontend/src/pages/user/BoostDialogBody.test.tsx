// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { BoostQuote } from '../../services/itemApi';
import { formatDate, formatDateTime, formatTime } from '../../utils/boostMessages';
import { BoostDialogBody, type BoostFailure } from './BoostDialogBody';

const allowedQuote: BoostQuote = {
  fee: 5,
  newBoostedUntil: '2026-11-09T08:00:00Z',
  allowed: true,
  reason: null,
  reasonCode: null,
  boostStartedAt: '2026-11-06T08:00:00Z',
  chainEndsAt: '2026-11-12T08:00:00Z',
  cooldownUntil: '2026-11-12T08:00:00Z',
  extending: false,
  slotsLeft: 12,
  earliestSlotAt: null,
  userActiveBoosts: 1,
  userEarliestBoostEndAt: null,
  expireAt: '2026-12-06T08:00:00Z',
};

function rejectedQuote(reasonCode: string, overrides: Partial<BoostQuote> = {}): BoostQuote {
  return { ...allowedQuote, allowed: false, reasonCode, reason: 'Lý do từ backend.', ...overrides };
}

function renderBody(quote: BoostQuote | null, options: { failure?: BoostFailure | null; boostDays?: number | null } = {}) {
  const handlers = { onConfirm: vi.fn(), onCancel: vi.fn(), onRenew: vi.fn() };
  render(
    <MemoryRouter>
      <BoostDialogBody
        titleId="boost-title"
        itemTitle="Áo khoác gió"
        loading={false}
        quote={quote}
        boostDays={options.boostDays === undefined ? 3 : options.boostDays}
        failure={options.failure ?? null}
        busy={false}
        {...handlers}
      />
    </MemoryRouter>,
  );
  return handlers;
}

afterEach(() => {
  cleanup();
});

describe('hộp xác nhận đẩy bài', () => {
  it('đợt mới hiển thị phí, các mốc từ quote, số suất và đoạn quyền lợi; không có số dư ví', () => {
    // given: mốc nghỉ khác mốc kéo dài tối đa để kiểm từng mốc hiện đúng chỗ
    const quote = { ...allowedQuote, cooldownUntil: '2026-11-13T08:00:00Z' };

    // when
    const { onConfirm } = renderBody(quote);

    // then
    expect(screen.getByRole('heading', { name: 'Xác nhận đẩy bài' })).toBeTruthy();
    expect(screen.getByText('Áo khoác gió')).toBeTruthy();
    expect(screen.getByText('3 ngày')).toBeTruthy();
    // ô Chi phí đẩy và nhãn trên nút xác nhận
    expect(screen.getAllByText('5 Credit')).toHaveLength(2);
    expect(screen.getByText(formatDate(quote.newBoostedUntil))).toBeTruthy();
    expect(screen.getByText(`Lúc ${formatTime(quote.newBoostedUntil)}`)).toBeTruthy();
    expect(screen.getByText(formatDateTime(quote.chainEndsAt))).toBeTruthy();
    expect(screen.getByText(formatDateTime(quote.cooldownUntil))).toBeTruthy();
    expect(screen.getByText('Còn 12 suất đẩy trống')).toBeTruthy();
    expect(screen.getByText(/Người mua sau không được xếp trên bạn/)).toBeTruthy();
    expect(screen.getByText(/không hoàn phí/)).toBeTruthy();
    expect(screen.queryByText(/Số dư/)).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Xác nhận đẩy 5 Credit' }));
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('không có cooldownUntil thì chỉ hiện trường hợp mua thêm gói', () => {
    // when
    renderBody({ ...allowedQuote, cooldownUntil: null });

    // then
    expect(screen.getByText('Nếu mua thêm gói:')).toBeTruthy();
    expect(screen.queryByText('Nếu không mua thêm:')).toBeNull();
  });

  it('nút Hủy và nút đóng X đều gọi onCancel', () => {
    // when
    const { onCancel } = renderBody(allowedQuote);
    fireEvent.click(screen.getByRole('button', { name: 'Hủy' }));
    fireEvent.click(screen.getByRole('button', { name: 'Đóng' }));

    // then
    expect(onCancel).toHaveBeenCalledTimes(2);
  });

  it('gia hạn khi đang đẩy dùng câu gia hạn và không nói số suất', () => {
    // given: bài đang đẩy, phí và số ngày khác mặc định để chắc không viết cứng
    const extendingQuote = { ...allowedQuote, extending: true, fee: 7, slotsLeft: 0 };

    // when
    renderBody(extendingQuote, { boostDays: 4 });

    // then
    expect(screen.getByText('Gia hạn thêm')).toBeTruthy();
    expect(screen.getByText('4 ngày')).toBeTruthy();
    expect(screen.getAllByText('7 Credit')).toHaveLength(2);
    expect(screen.getByText('Kết thúc lúc')).toBeTruthy();
    expect(screen.queryByText(/suất đẩy trống/)).toBeNull();
  });
});

describe('popup không đẩy được theo reasonCode', () => {
  it.each([
    [
      'LISTINGFEE_BOOST_SOLD_OUT',
      { earliestSlotAt: '2026-11-07T10:00:00Z' },
      `Hết suất đẩy bài. Suất sớm nhất trống lúc ${formatDateTime('2026-11-07T10:00:00Z')}. Bạn chưa bị trừ Credit.`,
    ],
    [
      'LISTINGFEE_BOOST_USER_LIMIT',
      { userActiveBoosts: 3, userEarliestBoostEndAt: '2026-11-08T09:00:00Z' },
      `Bạn đang đẩy 3 bài, là mức tối đa. Bài sớm nhất kết thúc lúc ${formatDateTime('2026-11-08T09:00:00Z')}.`,
    ],
    [
      'LISTINGFEE_BOOST_COOLDOWN',
      { cooldownUntil: '2026-11-10T08:00:00Z' },
      `Bài vừa kết thúc đợt đẩy. Bạn có thể đẩy lại từ ${formatDateTime('2026-11-10T08:00:00Z')}.`,
    ],
    [
      'LISTINGFEE_BOOST_CHAIN_LIMIT',
      { chainEndsAt: '2026-11-12T08:00:00Z', cooldownUntil: '2026-11-15T08:00:00Z' },
      `Đợt đẩy này đã đủ số ngày tối đa, kết thúc lúc ${formatDateTime('2026-11-12T08:00:00Z')}. Sau đó bài nghỉ và được đẩy lại từ ${formatDateTime('2026-11-15T08:00:00Z')}.`,
    ],
  ])('%s hiện lý do kèm mốc làm lại, không có nút hành động', (reasonCode, overrides, expected) => {
    // when
    renderBody(rejectedQuote(reasonCode, overrides));

    // then
    expect(screen.getByRole('heading', { name: 'Chưa đẩy được bài' })).toBeTruthy();
    expect(screen.getByText(expected)).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Xác nhận đẩy/ })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Gia hạn bài' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Nạp Credit' })).toBeNull();
  });

  it('LISTINGFEE_BOOST_EXCEEDS_EXPIRY ghi hạn hiển thị và nút Gia hạn bài mở luồng gia hạn', () => {
    // when
    const { onRenew } = renderBody(rejectedQuote('LISTINGFEE_BOOST_EXCEEDS_EXPIRY'));

    // then
    expect(
      screen.getByText(
        `Bài hết hạn hiển thị lúc ${formatDateTime(allowedQuote.expireAt!)}. Hãy gia hạn bài trước khi đẩy.`,
      ),
    ).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Gia hạn bài' }));
    expect(onRenew).toHaveBeenCalledTimes(1);
  });

  it('WALLET_INSUFFICIENT_BALANCE khi xác nhận ghi phí cần có và nút Nạp Credit dẫn tới trang nạp', () => {
    // when: báo giá cho phép nhưng POST trả thiếu Credit
    renderBody(allowedQuote, { failure: { code: 'WALLET_INSUFFICIENT_BALANCE', message: 'Không đủ.' } });

    // then
    expect(screen.getByText('Cần 5 Credit để đẩy bài.')).toBeTruthy();
    expect(screen.getByRole('link').getAttribute('href')).toBe('/credit');
    expect(screen.getByRole('button', { name: 'Nạp Credit' })).toBeTruthy();
  });

  it('mã khác hiện nguyên reason backend trả', () => {
    // when
    renderBody(rejectedQuote('LISTINGFEE_CATEGORY_NOT_BOOSTABLE', { reason: 'Danh mục của bài đăng không cho phép đẩy bài.' }));

    // then
    expect(screen.getByText('Danh mục của bài đăng không cho phép đẩy bài.')).toBeTruthy();
  });
});
