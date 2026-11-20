// @vitest-environment jsdom

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { respondDispute } from '../services/reportApi';
import { DisputeResponseDialog } from './DisputeResponseDialog';

vi.mock('../services/reportApi', () => ({
  reportApi: { respond: vi.fn() },
  respondDispute: vi.fn(),
}));
vi.mock('../services/mediaApi', () => ({ mediaApi: { upload: vi.fn() } }));

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

describe('DisputeResponseDialog', () => {
  it('khoá nút gửi khi chưa nhập, gửi giải trình rồi báo đã gửi', async () => {
    // given
    const user = userEvent.setup();
    const onSubmitted = vi.fn();
    vi.mocked(respondDispute).mockResolvedValue(undefined);
    render(
      <DisputeResponseDialog
        disputeId={7}
        reasonLabel="Sự cố giao nhận"
        deadlineText="Hạn giải trình: 14:30 31/10."
        onClose={vi.fn()}
        onSubmitted={onSubmitted}
      />,
    );
    const submit = screen.getByRole('button', { name: 'Gửi giải trình' }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
    expect(screen.getByText('Lý do khiếu nại: Sự cố giao nhận')).toBeTruthy();

    // when
    await user.type(screen.getByLabelText(/Nội dung giải trình/), 'Tôi có đến, đối phương không nghe máy');
    await user.click(submit);

    // then
    await waitFor(() =>
      expect(respondDispute).toHaveBeenCalledWith(7, {
        note: 'Tôi có đến, đối phương không nghe máy',
        evidenceMediaIds: undefined,
      }),
    );
    expect(onSubmitted).toHaveBeenCalledOnce();
  });

  it('báo lỗi của backend và không đóng hộp thoại', async () => {
    // given
    const user = userEvent.setup();
    const onSubmitted = vi.fn();
    vi.mocked(respondDispute).mockRejectedValue({ code: 'DISPUTE_RESPONSE_WINDOW_EXPIRED' });
    render(<DisputeResponseDialog disputeId={7} reasonLabel="Không đúng mô tả" onClose={vi.fn()} onSubmitted={onSubmitted} />);

    // when
    await user.type(screen.getByLabelText(/Nội dung giải trình/), 'Máy vẫn bật khi tôi giao');
    await user.click(screen.getByRole('button', { name: 'Gửi giải trình' }));

    // then
    expect(await screen.findByText('Đã hết hạn gửi giải trình.')).toBeTruthy();
    expect(onSubmitted).not.toHaveBeenCalled();
  });
});
