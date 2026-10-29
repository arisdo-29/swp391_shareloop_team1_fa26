// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ComponentProps } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { CreditHistorySection, CreditTransactionDetail } from '../../components/CreditHistoryView';
import type { HeldFeeItem, WalletTransactionResponse } from '../../services/walletApi';
import { toCreditHistory, userHistoryTabs } from '../../utils/creditHistory';
import { HeldFeesSection } from './Credit';

const heldFees: HeldFeeItem[] = [
  {
    itemId: 42,
    title: 'Xe đạp mini',
    offerType: 'GIVE',
    status: 'PENDING_REVIEW',
    pendingFee: 5,
    feeType: 'POST',
  },
  {
    itemId: 43,
    title: 'Bàn học gỗ',
    offerType: 'SWAP',
    status: 'APPROVED',
    pendingFee: 10,
    feeType: 'EDIT',
  },
];

function renderSection(
  props: Partial<ComponentProps<typeof HeldFeesSection>> = {},
) {
  return render(
    <MemoryRouter>
      <HeldFeesSection
        heldFees={[]}
        loading={false}
        error=""
        onRetry={() => undefined}
        {...props}
      />
    </MemoryRouter>,
  );
}

afterEach(cleanup);

describe('Credit đang giữ', () => {
  it('hiển thị loading riêng và chưa hiển thị trạng thái rỗng', () => {
    renderSection({ loading: true });

    expect(screen.getByRole('status').textContent).toContain(
      'Đang tải danh sách Credit đang giữ...',
    );
    expect(screen.queryByText('Không có bài nào đang giữ phí.')).toBeNull();
  });

  it('hiển thị lỗi API và cho phép thử lại', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    renderSection({ error: 'Không thể tải danh sách.', onRetry });

    expect(screen.getByText('Không thể tải danh sách.')).toBeTruthy();
    expect(screen.queryByText('Không có bài nào đang giữ phí.')).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('chỉ hiển thị trạng thái rỗng sau khi tải thành công', () => {
    renderSection();

    expect(screen.getByText('Không có bài nào đang giữ phí.')).toBeTruthy();
    expect(screen.queryByRole('status')).toBeNull();
  });

  it('hiển thị đủ dữ liệu, bản dịch và giữ link xem bài', () => {
    renderSection({ heldFees });

    expect(screen.getByRole('link', { name: 'Xe đạp mini' }).getAttribute('href')).toBe(
      '/items/42',
    );
    // Không hiển thị id kỹ thuật của bài cho người dùng.
    expect(screen.queryByText('Mã bài: #42')).toBeNull();
    expect(screen.getByText('Cho tặng')).toBeTruthy();
    expect(screen.getByText('Chờ duyệt')).toBeTruthy();
    expect(screen.getByText('5 Credit')).toBeTruthy();
    expect(screen.getByText('Phí đăng')).toBeTruthy();

    expect(screen.getByRole('link', { name: 'Bàn học gỗ' }).getAttribute('href')).toBe(
      '/items/43',
    );
    expect(screen.queryByText('Mã bài: #43')).toBeNull();
    expect(screen.getByText('Trao đổi')).toBeTruthy();
    expect(screen.getByText('Đã duyệt')).toBeTruthy();
    expect(screen.getByText('10 Credit')).toBeTruthy();
    expect(screen.getByText('Phí sửa')).toBeTruthy();
  });
});

function walletEntry(
  id: number,
  type: WalletTransactionResponse['type'],
  amount: number,
  extra: Partial<WalletTransactionResponse> = {},
): WalletTransactionResponse {
  return {
    id,
    type,
    amount,
    balanceAfter: 50,
    itemId: null,
    paymentOrderId: null,
    note: null,
    createdAt: '2026-10-05T03:00:00Z',
    ...extra,
  };
}

describe('Lịch sử Credit của người dùng', () => {
  const history = [
    walletEntry(1, 'POST_FEE', -5, { itemId: 42 }),
    walletEntry(2, 'EDIT_FEE', -2, { itemId: 42 }),
    walletEntry(3, 'RENEW_FEE', -3, { itemId: 99 }),
    walletEntry(4, 'BOOST_FEE', -4, { itemId: 42 }),
    walletEntry(5, 'AI_SEARCH_FEE', 2, { note: 'Hoàn phí AI #9' }),
  ].map((entry) => toCreditHistory(entry, '7'));

  function renderHistory(activeTab = 'all') {
    return render(
      <CreditHistorySection
        itemTitles={new Map([['42', 'Xe đạp mini']])}
        allHistory={history}
        tabs={userHistoryTabs}
        activeTab={activeTab}
        onTabChange={() => undefined}
        onSelect={() => undefined}
      />,
    );
  }

  it('hiện đúng nhãn theo loại phí và ghép tên món khi bài còn trong danh sách', () => {
    // given
    // when
    renderHistory();

    // then
    expect(screen.getByText('Phí đăng bài - Xe đạp mini')).toBeTruthy();
    expect(screen.getByText('Phí chỉnh sửa bài - Xe đạp mini')).toBeTruthy();
    expect(screen.getByText('Phí đẩy tin - Xe đạp mini')).toBeTruthy();
    expect(screen.queryByText('Chi tiêu')).toBeNull();
  });

  it('hiện số trên tab từ counts của BE thay vì đếm các dòng của trang hiện tại', () => {
    // given: trang hiện tại chỉ có vài dòng nhưng BE đếm cả khoảng lọc
    render(
      <CreditHistorySection
        itemTitles={new Map()}
        allHistory={history}
        tabs={userHistoryTabs}
        tabCounts={{ all: 42, topup: 6, spend: 35 }}
        activeTab="all"
        onTabChange={() => undefined}
        onSelect={() => undefined}
      />,
    );

    // when
    const tabLabels = screen.getAllByRole('tab').map((tab) => tab.textContent);

    // then
    expect(tabLabels).toEqual(['Tất cả (42)', 'Nạp Credit (6)', 'Chi tiêu (35)']);
  });

  it('không hiện tên món khi bài không còn trong danh sách', () => {
    // given
    // when
    renderHistory();

    // then
    expect(screen.getByText('Phí gia hạn bài')).toBeTruthy();
  });

  it('hiện hoàn phí AI bằng nhãn riêng, không có số và không nằm trong tab Chi tiêu', () => {
    // given
    // when
    const { container } = renderHistory();

    // then
    expect(screen.getByText('Hoàn phí tìm kiếm AI')).toBeTruthy();
    expect(container.textContent).not.toContain('#');
    cleanup();
    renderHistory('spend');
    expect(screen.queryByText('Hoàn phí tìm kiếm AI')).toBeNull();
  });

  it('hộp chi tiết hiện tên món và nhãn đúng loại phí', () => {
    // given
    const entry = history[1];

    // when
    render(
      <CreditTransactionDetail
        entry={entry}
        relatedItemTitle="Xe đạp mini"
        onClose={() => undefined}
      />,
    );

    // then
    expect(screen.getAllByText('Phí chỉnh sửa bài').length).toBeGreaterThan(0);
    expect(screen.getByText('Món đồ')).toBeTruthy();
    expect(screen.getByText('Xe đạp mini')).toBeTruthy();
  });

  it('hộp chi tiết hoàn phí AI không hiện note có số', () => {
    // given
    const refund = history[4];

    // when
    const { container } = render(<CreditTransactionDetail entry={refund} onClose={() => undefined} />);

    // then
    expect(container.textContent).not.toContain('#');
    expect(screen.getAllByText('Hoàn phí tìm kiếm AI').length).toBeGreaterThan(0);
  });
});
