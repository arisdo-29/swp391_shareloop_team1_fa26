// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { RejectReasonsContent } from './AdminPages';

const reasons = [
  'Ảnh không đúng món đồ.',
  'Bài đăng chứa thông tin liên hệ.',
];

afterEach(cleanup);

describe('RejectReasonsContent', () => {
  it('không cho xác nhận khi chưa chọn lý do và gửi đúng một lý do', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(
      <RejectReasonsContent
        reasons={reasons}
        disabled={false}
        onCancel={() => undefined}
        onConfirm={onConfirm}
      />,
    );

    const confirmButton = screen.getByRole('button', { name: 'Xác nhận từ chối' });
    expect((confirmButton as HTMLButtonElement).disabled).toBe(true);
    expect(screen.getByText('0/3')).toBeTruthy();

    await user.click(screen.getByRole('checkbox', { name: reasons[0] }));
    expect(screen.getByText('1/3')).toBeTruthy();
    expect((confirmButton as HTMLButtonElement).disabled).toBe(false);
    await user.click(confirmButton);

    expect(onConfirm).toHaveBeenCalledTimes(1);
    expect(onConfirm).toHaveBeenCalledWith(reasons[0]);
  });

  it('ghép nhiều lý do đã chọn bằng ký tự xuống dòng', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(
      <RejectReasonsContent
        reasons={reasons}
        disabled={false}
        onCancel={() => undefined}
        onConfirm={onConfirm}
      />,
    );

    await user.click(screen.getByRole('checkbox', { name: reasons[0] }));
    await user.click(screen.getByRole('checkbox', { name: reasons[1] }));
    await user.click(screen.getByRole('button', { name: 'Xác nhận từ chối' }));

    expect(screen.getByText('2/3')).toBeTruthy();
    expect(onConfirm).toHaveBeenCalledWith(`${reasons[0]}\n${reasons[1]}`);
  });

  it('bắt buộc nhập lý do khác và cho phép kết hợp với lý do mẫu', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(
      <RejectReasonsContent
        reasons={reasons}
        disabled={false}
        onCancel={() => undefined}
        onConfirm={onConfirm}
      />,
    );

    await user.click(screen.getByRole('checkbox', { name: reasons[1] }));
    await user.click(screen.getByRole('checkbox', { name: 'Lý do khác' }));
    const confirmButton = screen.getByRole('button', { name: 'Xác nhận từ chối' });
    expect((confirmButton as HTMLButtonElement).disabled).toBe(true);

    await user.type(screen.getByRole('textbox', { name: /Nội dung lý do khác/ }), '  Thiếu ảnh chụp lỗi.  ');
    expect((confirmButton as HTMLButtonElement).disabled).toBe(false);
    await user.click(confirmButton);

    expect(onConfirm).toHaveBeenCalledWith(`${reasons[1]}\nThiếu ảnh chụp lỗi.`);
  });

  it('không gửi nội dung tùy chỉnh sau khi bỏ chọn lý do khác', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(
      <RejectReasonsContent
        reasons={reasons}
        disabled={false}
        onCancel={() => undefined}
        onConfirm={onConfirm}
      />,
    );

    await user.click(screen.getByRole('checkbox', { name: reasons[0] }));
    await user.click(screen.getByRole('checkbox', { name: 'Lý do khác' }));
    await user.type(screen.getByRole('textbox', { name: /Nội dung lý do khác/ }), 'Không dùng nội dung này');
    await user.click(screen.getByRole('checkbox', { name: 'Lý do khác' }));
    await user.click(screen.getByRole('button', { name: 'Xác nhận từ chối' }));

    expect(onConfirm).toHaveBeenCalledWith(reasons[0]);
  });

  it('hủy không xác nhận, trạng thái loading khóa thao tác và chống double-submit', async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();
    const onConfirm = vi.fn();
    const { rerender } = render(
      <RejectReasonsContent
        reasons={reasons}
        disabled={false}
        onCancel={onCancel}
        onConfirm={onConfirm}
      />,
    );

    await user.click(screen.getByRole('button', { name: 'Hủy' }));
    expect(onCancel).toHaveBeenCalledTimes(1);
    expect(onConfirm).not.toHaveBeenCalled();

    await user.click(screen.getByRole('checkbox', { name: reasons[0] }));
    rerender(
      <RejectReasonsContent
        reasons={reasons}
        disabled
        onCancel={onCancel}
        onConfirm={onConfirm}
      />,
    );
    const loadingButton = screen.getByRole('button', { name: 'Đang xử lý...' });
    expect((loadingButton as HTMLButtonElement).disabled).toBe(true);
    await user.click(loadingButton);
    await user.click(loadingButton);
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('giữ nguyên lựa chọn khi API trả lỗi', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    const { rerender } = render(
      <RejectReasonsContent
        reasons={reasons}
        disabled={false}
        onCancel={() => undefined}
        onConfirm={onConfirm}
      />,
    );

    const selectedCheckbox = screen.getByRole('checkbox', { name: reasons[0] });
    await user.click(selectedCheckbox);
    rerender(
      <RejectReasonsContent
        reasons={reasons}
        disabled={false}
        error="Không từ chối được bài đăng."
        onCancel={() => undefined}
        onConfirm={onConfirm}
      />,
    );

    expect((screen.getByRole('checkbox', { name: reasons[0] }) as HTMLInputElement).checked).toBe(true);
    expect(screen.getByText('Không từ chối được bài đăng.')).toBeTruthy();
  });
});
