// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { RequestResponse } from '../../services/requestApi';
import { openDispute } from '../../services/reportApi';
import { DisputeDialog } from './TransactionDialogs';

vi.mock('../../services/reportApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../services/reportApi')>();
  return { ...actual, openDispute: vi.fn() };
});

const request: RequestResponse = {
  id: 101,
  itemId: 45,
  receiverId: 12,
  donorId: 9,
  type: 'SWAP',
  status: 'COMPLETED',
  createdAt: '2026-10-20T08:00:00Z',
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderDialog() {
  const onSubmitted = vi.fn();
  render(
    <DisputeDialog
      request={request}
      itemTitle="Máy pha cà phê"
      onClose={vi.fn()}
      onSubmitted={onSubmitted}
    />,
  );
  return onSubmitted;
}

describe('DisputeDialog', () => {
  it('bắt buộc nhập giải thích khi chọn lý do Khác và tự xóa lỗi khi nội dung hợp lệ', async () => {
    const user = userEvent.setup();
    const onSubmitted = renderDialog();
    await user.selectOptions(screen.getByLabelText('Lý do'), 'OTHER');
    const noteInput = screen.getByLabelText(/Nội dung \(bắt buộc\)/);
    fireEvent.change(noteInput, {
      target: { value: '   ' },
    });

    await user.click(screen.getByRole('button', { name: 'Gửi khiếu nại' }));

    expect(screen.getByText('Vui lòng nhập lý do khiếu nại.')).toBeTruthy();
    expect(openDispute).not.toHaveBeenCalled();
    expect(onSubmitted).not.toHaveBeenCalled();

    fireEvent.change(noteInput, {
      target: { value: 'Món đồ không thuộc các lý do có sẵn' },
    });
    expect(screen.queryByText('Vui lòng nhập lý do khiếu nại.')).toBeNull();

    vi.mocked(openDispute).mockResolvedValue({
      id: 88,
      responseDueAt: '2026-11-05T08:00:00Z',
    });
    await user.click(screen.getByRole('button', { name: 'Gửi khiếu nại' }));

    await waitFor(() =>
      expect(openDispute).toHaveBeenCalledWith(101, {
        reason: 'OTHER',
        note: 'Món đồ không thuộc các lý do có sẵn',
        evidenceMediaIds: undefined,
      }),
    );
    expect(onSubmitted).toHaveBeenCalledOnce();
  });

  it('các lý do khác vẫn cho gửi mà không cần nội dung', async () => {
    const user = userEvent.setup();
    vi.mocked(openDispute).mockResolvedValue({
      id: 89,
      responseDueAt: '2026-11-05T08:00:00Z',
    });
    renderDialog();
    await user.selectOptions(screen.getByLabelText('Lý do'), 'NOT_AS_DESCRIBED');

    await user.click(screen.getByRole('button', { name: 'Gửi khiếu nại' }));

    await waitFor(() =>
      expect(openDispute).toHaveBeenCalledWith(101, {
        reason: 'NOT_AS_DESCRIBED',
        note: undefined,
        evidenceMediaIds: undefined,
      }),
    );
  });
});

