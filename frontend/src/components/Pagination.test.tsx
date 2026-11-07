// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { pageSlots } from '../utils/pagination';
import { Pagination } from './Pagination';

afterEach(() => cleanup());

describe('pageSlots', () => {
  it('ít trang thì hiện đủ, không có dấu ...', () => {
    // when
    const slots = pageSlots(2, 5);

    // then
    expect(slots).toEqual([0, 1, 2, 3, 4]);
  });

  it('trang ở giữa thì có ... ở hai đầu', () => {
    // when
    const slots = pageSlots(5, 10);

    // then
    expect(slots).toEqual([0, 'ellipsis-start', 4, 5, 6, 'ellipsis-end', 9]);
  });
});

describe('Pagination', () => {
  it('không có kết quả thì không hiện gì', () => {
    // when
    const { container } = render(
      <Pagination page={0} size={20} totalElements={0} onPageChange={vi.fn()} />,
    );

    // then
    expect(container.innerHTML).toBe('');
  });

  it('size không hợp lệ thì không hiện gì thay vì chia cho 0', () => {
    // when
    const { container } = render(
      <Pagination page={0} size={0} totalElements={30} onPageChange={vi.fn()} />,
    );

    // then
    expect(container.innerHTML).toBe('');
  });

  it('hiện khoảng kết quả đang xem và đi tới trang cuối', async () => {
    // given
    const user = userEvent.setup();
    const onPageChange = vi.fn();
    render(<Pagination page={1} size={10} totalElements={95} onPageChange={onPageChange} />);

    // when
    await user.click(screen.getByRole('button', { name: 'Trang cuối' }));

    // then
    expect(screen.getByText('Hiển thị 11–20 trên 95 kết quả')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Trang 2' }).getAttribute('aria-current')).toBe(
      'page',
    );
    expect(onPageChange).toHaveBeenCalledWith(9);
  });

  it('có onSizeChange thì đổi được số dòng mỗi trang', async () => {
    // given
    const user = userEvent.setup();
    const onSizeChange = vi.fn();
    render(
      <Pagination
        page={0}
        size={10}
        totalElements={95}
        onPageChange={vi.fn()}
        onSizeChange={onSizeChange}
      />,
    );

    // when
    await user.selectOptions(screen.getByLabelText('Mỗi trang'), '50');

    // then
    expect(onSizeChange).toHaveBeenCalledWith(50);
  });
});
