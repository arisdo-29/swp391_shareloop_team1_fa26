// @vitest-environment jsdom

import { useState } from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ReviewChecklistItem } from '../../services/adminModerationApi';
import { ModerationChecklistContent } from './ModerationParts';

const checklist: ReviewChecklistItem[] = [
  { id: 11, key: 'content', statement: 'Nội dung phù hợp quy định' },
  { id: 22, key: 'images', statement: 'Hình ảnh thể hiện đúng món đồ' },
  { id: 33, key: 'contact', statement: 'Không chứa thông tin liên hệ trái phép' },
];

function ChecklistHarness({ onConfirm }: { onConfirm: () => void }) {
  const [answers, setAnswers] = useState(checklist.map(() => false));

  return (
    <ModerationChecklistContent
      checklist={checklist}
      answers={answers}
      disabled={false}
      onToggle={(index, checked) => {
        setAnswers((current) => current.map((answer, answerIndex) => (
          answerIndex === index ? checked : answer
        )));
      }}
      onCancel={() => undefined}
      onConfirm={onConfirm}
    />
  );
}

afterEach(cleanup);

describe('ModerationChecklistContent', () => {
  it('hiển thị checklist Backend, cập nhật tiến độ và chỉ xác nhận khi hoàn tất', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(<ChecklistHarness onConfirm={onConfirm} />);

    const confirmButton = screen.getByRole('button', { name: 'Xác nhận duyệt bài' });
    expect(screen.getByText('0/3')).toBeTruthy();
    expect(screen.getAllByRole('checkbox')).toHaveLength(3);
    expect((confirmButton as HTMLButtonElement).disabled).toBe(true);
    expect(onConfirm).not.toHaveBeenCalled();

    await user.click(screen.getByRole('checkbox', { name: checklist[0].statement }));
    expect(screen.getByText('1/3')).toBeTruthy();
    expect((confirmButton as HTMLButtonElement).disabled).toBe(true);
    expect(onConfirm).not.toHaveBeenCalled();

    await user.click(screen.getByRole('checkbox', { name: checklist[1].statement }));
    await user.click(screen.getByRole('checkbox', { name: checklist[2].statement }));
    expect(screen.getByText('3/3')).toBeTruthy();
    expect((confirmButton as HTMLButtonElement).disabled).toBe(false);
    expect(onConfirm).not.toHaveBeenCalled();

    await user.click(confirmButton);
    expect(onConfirm).toHaveBeenCalledTimes(1);
  });

  it('không cho duyệt khi Backend không cung cấp checklist', () => {
    const onConfirm = vi.fn();
    render(
      <ModerationChecklistContent
        checklist={[]}
        answers={[]}
        disabled={false}
        onToggle={() => undefined}
        onCancel={() => undefined}
        onConfirm={onConfirm}
      />,
    );

    expect(screen.getByText(/Backend chưa cung cấp checklist/)).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Xác nhận duyệt bài' }) as HTMLButtonElement).disabled).toBe(true);
    expect(onConfirm).not.toHaveBeenCalled();
  });
});
