// @vitest-environment jsdom

import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastHost } from './ToastHost';
import { toast } from './toast';

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe('ToastHost', () => {
  it('không hiển thị thông báo hết hạn phiên', () => {
    render(<ToastHost />);

    act(() => toast.error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'));

    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.queryByRole('status')).toBeNull();
  });

  it('hiển thị đúng tone và tự đóng sau 3,8 giây', () => {
    render(<ToastHost />);

    act(() => toast.success('Lưu thay đổi thành công.'));
    const message = screen.getByRole('status');
    expect(message.textContent).toContain('Lưu thay đổi thành công.');
    expect(message.querySelector('.text-success')).toBeTruthy();

    act(() => vi.advanceTimersByTime(3_800));
    expect(screen.queryByText('Lưu thay đổi thành công.')).toBeNull();
  });

  it('hiển thị lỗi, đóng thủ công và không lặp Toast giống nhau liên tiếp', () => {
    render(<ToastHost />);

    act(() => {
      toast.error('Không thể gửi yêu cầu thử nghiệm.');
      toast.error('Không thể gửi yêu cầu thử nghiệm.');
    });

    expect(screen.getAllByText('Không thể gửi yêu cầu thử nghiệm.')).toHaveLength(1);
    expect(screen.getByRole('alert').querySelector('.text-error')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Đóng thông báo' }));
    expect(screen.queryByText('Không thể gửi yêu cầu thử nghiệm.')).toBeNull();
  });
});
