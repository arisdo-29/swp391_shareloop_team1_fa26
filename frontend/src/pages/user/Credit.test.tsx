// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ComponentProps } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { HeldFeeItem } from '../../services/walletApi';
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
