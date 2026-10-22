// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { AdminCategoryResponse } from '../../services/adminCategoryApi';
import { ApiCategoryModal } from './AdminPages';

const existingCategory: AdminCategoryResponse = {
  id: 7,
  name: 'Đồ điện tử',
  parentId: null,
  restricted: false,
  boostable: true,
  sortOrder: 3,
  active: true,
};

afterEach(cleanup);

function renderModal(category: AdminCategoryResponse | null = null) {
  const onSave = vi.fn();
  render(
    <ApiCategoryModal
      category={category}
      categories={category ? [category] : []}
      saving={false}
      onClose={() => undefined}
      onSave={onSave}
    />,
  );
  return { onSave, orderInput: screen.getByRole('spinbutton', { name: 'Thứ tự' }) as HTMLInputElement };
}

describe('ApiCategoryModal', () => {
  it('tăng giảm thứ tự như số nguyên thông thường', () => {
    const { orderInput } = renderModal();

    expect(orderInput.value).toBe('0');
    orderInput.stepUp();
    fireEvent.input(orderInput);
    expect(orderInput.value).toBe('1');
    orderInput.stepUp();
    fireEvent.input(orderInput);
    expect(orderInput.value).toBe('2');
    orderInput.stepDown();
    fireEvent.input(orderInput);
    expect(orderInput.value).toBe('1');
    orderInput.stepDown();
    fireEvent.input(orderInput);
    expect(orderInput.value).toBe('0');
  });

  it('cho phép xóa để nhập lại và chuẩn hóa số 0 thừa khi rời ô', async () => {
    const user = userEvent.setup();
    const { orderInput } = renderModal();

    await user.clear(orderInput);
    expect(orderInput.value).toBe('');
    expect((screen.getByRole('button', { name: 'Lưu' }) as HTMLButtonElement).disabled).toBe(true);

    await user.type(orderInput, '001');
    expect(orderInput.value).toBe('1');
    await user.tab();
    expect(orderInput.value).toBe('1');

    for (const value of ['0', '1', '10']) {
      await user.clear(orderInput);
      await user.type(orderInput, value);
      expect(orderInput.value).toBe(value);
    }
  });

  it('không nhận số âm, chữ hoặc số thập phân', () => {
    const { orderInput } = renderModal();

    fireEvent.change(orderInput, { target: { value: '-1' } });
    expect(orderInput.value).toBe('0');
    fireEvent.change(orderInput, { target: { value: 'abc' } });
    expect(orderInput.value).not.toBe('abc');
    fireEvent.change(orderInput, { target: { value: '1.5' } });
    expect(orderInput.value).not.toBe('1.5');
  });

  it('thêm danh mục với sortOrder kiểu number và nhãn đẩy tin mới', async () => {
    const user = userEvent.setup();
    const { onSave, orderInput } = renderModal();

    await user.type(screen.getByRole('textbox', { name: 'Tên danh mục' }), 'Nội thất');
    await user.clear(orderInput);
    await user.type(orderInput, '001');
    await user.click(screen.getByRole('checkbox', { name: 'Cho phép đẩy tin' }));
    await user.click(screen.getByRole('button', { name: 'Lưu' }));

    expect(screen.queryByText('Cho phép boost')).toBeNull();
    expect(onSave).toHaveBeenCalledWith({
      name: 'Nội thất',
      parentId: null,
      restricted: false,
      boostable: true,
      sortOrder: 1,
    });
  });

  it('sửa danh mục và gửi giá trị thứ tự mới', async () => {
    const user = userEvent.setup();
    const { onSave, orderInput } = renderModal(existingCategory);

    expect(screen.getByRole('dialog', { name: 'Sửa danh mục' })).toBeTruthy();
    expect(orderInput.value).toBe('3');
    await user.clear(orderInput);
    await user.type(orderInput, '10');
    await user.click(screen.getByRole('button', { name: 'Lưu' }));

    expect(onSave).toHaveBeenCalledWith(expect.objectContaining({
      name: 'Đồ điện tử',
      boostable: true,
      sortOrder: 10,
    }));
  });
});
