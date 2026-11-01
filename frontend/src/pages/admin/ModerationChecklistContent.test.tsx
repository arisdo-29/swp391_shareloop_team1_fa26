// @vitest-environment jsdom

import { useState } from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ReviewChecklistItem } from '../../services/adminModerationApi';
import { ModerationChecklistContent } from './ModerationParts';

const checklist: ReviewChecklistItem[] = [
  {
    id: 11,
    key: 'content',
    statement: 'Nội dung phù hợp quy định',
    description: 'Đạt khi nội dung mô tả đúng món đồ. Từ chối nếu nội dung sai lệch.',
  },
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
    expect(screen.getByText(checklist[0].description!)).toBeTruthy();
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

describe('ModerationChecklistContent – Q01 điều kiện đạt', () => {
  it('hiện mô tả "Đạt khi..." ngay dưới tên mục, mục chưa có mô tả thì chỉ hiện tên', () => {
    render(
      <ModerationChecklistContent
        checklist={[
          {
            id: 1,
            key: 'real_photos',
            statement: 'Ảnh chụp thực tế rõ nét của đúng món đồ, từ 3 ảnh trở lên, không dùng ảnh mạng.',
            description: 'Đạt khi có từ 3 ảnh chụp thật. Từ chối nếu dùng ảnh tải từ mạng.',
          },
          { id: 2, key: 'category_match', statement: 'Danh mục và thẻ phân loại phản ánh đúng món đồ.', description: null },
        ]}
        answers={[false, false]}
        disabled={false}
        onToggle={() => undefined}
        onCancel={() => undefined}
        onConfirm={() => undefined}
      />,
    );

    const photos = screen.getByRole('checkbox', { name: /Ảnh chụp thực tế rõ nét/ });
    expect(photos.closest('label')?.textContent).toContain('Đạt khi có từ 3 ảnh chụp thật. Từ chối nếu dùng ảnh tải từ mạng.');
    const category = screen.getByRole('checkbox', { name: /Danh mục và thẻ phân loại/ });
    expect(category.closest('label')?.textContent).toBe('Danh mục và thẻ phân loại phản ánh đúng món đồ.');
  });
});
