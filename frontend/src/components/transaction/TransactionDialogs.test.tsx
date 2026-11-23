// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { requestApi, type RequestResponse } from '../../services/requestApi';
import { openDispute } from '../../services/reportApi';
import {
  CancelRequestDialog,
  DisputeDialog,
  HandoverFailedDialog,
  RejectCancelRequestDialog,
} from './TransactionDialogs';
import { afterMeetingReasons, BEFORE_MEETING_REASONS, isBeforeMeetingTime } from '../../utils/handoverOutcome';

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


describe('lý do huỷ và giờ hẹn', () => {
  it('trước giờ hẹn có ba lý do và từ giờ hẹn có lý do theo hình thức giao nhận', () => {
    expect(BEFORE_MEETING_REASONS.map((option) => option.label)).toEqual([
      'Tôi bận đột xuất',
      'Tôi không còn nhu cầu',
      'Lý do khác',
    ]);
    expect(afterMeetingReasons(false).map((option) => option.code)).toEqual([
      'COUNTERPART_NO_SHOW',
      'CANNOT_CONTACT',
      'OTHER',
    ]);
    expect(afterMeetingReasons(true)[0].label).toBe('Bên kia chưa gửi hàng');
  });

  it('isBeforeMeetingTime so giờ hẹn với bây giờ', () => {
    const now = new Date('2026-12-01T10:00:00Z');
    expect(isBeforeMeetingTime('2026-12-01T12:00:00Z', now)).toBe(true);
    expect(isBeforeMeetingTime('2026-12-01T09:00:00Z', now)).toBe(false);
    expect(isBeforeMeetingTime(undefined, now)).toBe(false);
  });
});

const confirmedRequest: RequestResponse = {
  id: 101,
  itemId: 45,
  receiverId: 12,
  donorId: 9,
  type: 'GIVE',
  status: 'LOGISTICS_CONFIRMED',
  deliveryMethod: 'IN_PERSON',
  contactRevealedAt: '2026-11-30T08:00:00Z',
  createdAt: '2026-11-20T08:00:00Z',
};

describe('CancelRequestDialog', () => {
  it('lý do khác bắt buộc nhập chữ rồi gửi đề nghị huỷ với mã lý do', async () => {
    const user = userEvent.setup();
    vi.spyOn(requestApi, 'requestCancel').mockResolvedValue({ ...confirmedRequest });
    const onDone = vi.fn();
    render(
      <CancelRequestDialog
        request={confirmedRequest}
        currentUserId={9}
        itemTitle="Máy pha cà phê"
        partnerName="Bình"
        viewerIsDonor
        onClose={vi.fn()}
        onDone={onDone}
        onStale={vi.fn()}
      />,
    );
    const submit = screen.getByRole('button', { name: 'Gửi đề nghị huỷ' }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);

    await user.selectOptions(screen.getByLabelText('Lý do đề nghị huỷ'), 'OTHER');
    expect(submit.disabled).toBe(true);
    fireEvent.change(screen.getByLabelText(/Mô tả lý do/), { target: { value: 'Tôi phải đi công tác' } });
    await user.click(submit);

    await waitFor(() =>
      expect(requestApi.requestCancel).toHaveBeenCalledWith(101, 9, {
        code: 'OTHER',
        reason: 'Tôi phải đi công tác',
      }),
    );
    expect(onDone).toHaveBeenCalledOnce();
  });

  it('lý do có sẵn gửi được ngay mà không cần nhập chữ', async () => {
    const user = userEvent.setup();
    vi.spyOn(requestApi, 'requestCancel').mockResolvedValue({ ...confirmedRequest });
    render(
      <CancelRequestDialog
        request={confirmedRequest}
        currentUserId={9}
        itemTitle="Máy pha cà phê"
        partnerName="Bình"
        viewerIsDonor
        onClose={vi.fn()}
        onDone={vi.fn()}
        onStale={vi.fn()}
      />,
    );
    await user.selectOptions(screen.getByLabelText('Lý do đề nghị huỷ'), 'BUSY');
    await user.click(screen.getByRole('button', { name: 'Gửi đề nghị huỷ' }));

    await waitFor(() =>
      expect(requestApi.requestCancel).toHaveBeenCalledWith(101, 9, { code: 'BUSY', reason: undefined }),
    );
  });
});

describe('Lý do khác ngắn hơn 5 ký tự', () => {
  it('đề nghị huỷ: báo lỗi tiếng Việt, khoá nút và không gọi API', async () => {
    const user = userEvent.setup();
    const spy = vi.spyOn(requestApi, 'requestCancel').mockResolvedValue({ ...confirmedRequest });
    render(
      <CancelRequestDialog
        request={confirmedRequest}
        currentUserId={9}
        itemTitle="Máy pha cà phê"
        partnerName="Bình"
        viewerIsDonor
        onClose={vi.fn()}
        onDone={vi.fn()}
        onStale={vi.fn()}
      />,
    );
    await user.selectOptions(screen.getByLabelText('Lý do đề nghị huỷ'), 'OTHER');
    fireEvent.change(screen.getByLabelText(/Mô tả lý do/), { target: { value: 'abc' } });

    expect(screen.getByText('Mô tả cần ít nhất 5 ký tự.')).toBeTruthy();
    const submit = screen.getByRole('button', { name: 'Gửi đề nghị huỷ' }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
    await user.click(submit);
    expect(spy).not.toHaveBeenCalled();
  });

  it('giao nhận không thành công: báo lỗi tiếng Việt, khoá nút và không gọi API', async () => {
    const user = userEvent.setup();
    const pastRequest = { ...confirmedRequest, meetingTime: '2026-11-30T10:00:00Z' };
    const spy = vi.spyOn(requestApi, 'handoverFailed').mockResolvedValue({ ...pastRequest });
    render(
      <HandoverFailedDialog
        request={pastRequest}
        currentUserId={12}
        itemTitle="Máy pha cà phê"
        partnerName="An"
        viewerIsDonor={false}
        onClose={vi.fn()}
        onDone={vi.fn()}
        onStale={vi.fn()}
      />,
    );
    await user.selectOptions(screen.getByLabelText('Lý do giao nhận không thành công'), 'OTHER');
    fireEvent.change(screen.getByLabelText(/Mô tả sự cố/), { target: { value: 'ab' } });

    expect(screen.getByText('Mô tả cần ít nhất 5 ký tự.')).toBeTruthy();
    const submit = screen.getByRole('button', { name: /Gửi đề nghị huỷ|Gửi khiếu nại/ }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
    await user.click(submit);
    expect(spy).not.toHaveBeenCalled();
  });
});

describe('HandoverFailedDialog', () => {
  const pastRequest = { ...confirmedRequest, meetingTime: '2026-11-30T10:00:00Z' };

  it('gửi mã lý do không đến điểm hẹn và nút ghi rõ là đề nghị huỷ khi bên kia chưa bấm hoàn tất', async () => {
    const user = userEvent.setup();
    vi.spyOn(requestApi, 'handoverFailed').mockResolvedValue({ ...pastRequest });
    render(
      <HandoverFailedDialog
        request={pastRequest}
        currentUserId={12}
        itemTitle="Máy pha cà phê"
        partnerName="An"
        viewerIsDonor={false}
        onClose={vi.fn()}
        onDone={vi.fn()}
        onStale={vi.fn()}
      />,
    );
    await user.selectOptions(
      screen.getByLabelText('Lý do giao nhận không thành công'),
      'COUNTERPART_NO_SHOW',
    );
    await user.click(screen.getByRole('button', { name: 'Gửi đề nghị huỷ' }));

    await waitFor(() =>
      expect(requestApi.handoverFailed).toHaveBeenCalledWith(101, 12, {
        code: 'COUNTERPART_NO_SHOW',
        reason: undefined,
        evidenceMediaIds: undefined,
      }),
    );
  });

  it('khi bên kia đã bấm hoàn tất thì nút là gửi khiếu nại', () => {
    render(
      <HandoverFailedDialog
        request={{ ...pastRequest, status: 'AWAITING_HANDOVER', donorHandoverAt: '2026-11-30T11:00:00Z' }}
        currentUserId={12}
        itemTitle="Máy pha cà phê"
        partnerName="An"
        viewerIsDonor={false}
        onClose={vi.fn()}
        onDone={vi.fn()}
        onStale={vi.fn()}
      />,
    );
    expect(screen.getByRole('button', { name: 'Gửi khiếu nại' })).toBeTruthy();
  });
});

describe('RejectCancelRequestDialog', () => {
  it('bắt buộc giải trình từ 5 ký tự rồi gửi không đồng ý', async () => {
    const user = userEvent.setup();
    vi.spyOn(requestApi, 'rejectCancelRequest').mockResolvedValue({ ...confirmedRequest, status: 'DISPUTED' });
    const onDone = vi.fn();
    render(
      <RejectCancelRequestDialog
        request={confirmedRequest}
        currentUserId={12}
        itemTitle="Máy pha cà phê"
        partnerName="An"
        viewerIsDonor={false}
        onClose={vi.fn()}
        onDone={onDone}
        onStale={vi.fn()}
      />,
    );
    const submit = screen.getByRole('button', { name: 'Gửi giải trình' }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);

    fireEvent.change(screen.getByLabelText(/Giải trình/), { target: { value: 'Tôi vẫn muốn nhận' } });
    await user.click(submit);

    await waitFor(() =>
      expect(requestApi.rejectCancelRequest).toHaveBeenCalledWith(101, 12, {
        explanation: 'Tôi vẫn muốn nhận',
        evidenceMediaIds: undefined,
      }),
    );
    expect(onDone).toHaveBeenCalledOnce();
  });
});
