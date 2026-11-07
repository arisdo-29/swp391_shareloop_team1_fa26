// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ReportEvidenceField } from './ReportEvidenceField';

const originalCreate = URL.createObjectURL;
const originalRevoke = URL.revokeObjectURL;

beforeEach(() => {
  // jsdom không có object URL nên giả lập để kiểm tra ảnh xem trước.
  URL.createObjectURL = vi.fn(() => 'blob:preview');
  URL.revokeObjectURL = vi.fn();
});

afterEach(() => {
  cleanup();
  URL.createObjectURL = originalCreate;
  URL.revokeObjectURL = originalRevoke;
});

describe('ReportEvidenceField', () => {
  it('ảnh vừa chọn hiện dạng ảnh thu nhỏ', () => {
    // given
    const file = new File(['photo'], 'mat-kinh.jpg', { type: 'image/jpeg' });

    // when
    render(<ReportEvidenceField files={[file]} onChange={vi.fn()} disabled={false} />);

    // then
    const image = screen.getByRole<HTMLImageElement>('img', { name: 'mat-kinh.jpg' });
    expect(image.getAttribute('src')).toBe('blob:preview');
  });

  it('bấm bỏ ảnh thì gọi onChange không còn ảnh đó', async () => {
    // given
    const user = userEvent.setup();
    const kept = new File(['a'], 'giu-lai.jpg', { type: 'image/jpeg' });
    const removed = new File(['b'], 'bo-di.jpg', { type: 'image/jpeg' });
    const onChange = vi.fn();
    render(<ReportEvidenceField files={[kept, removed]} onChange={onChange} disabled={false} />);

    // when
    await user.click(screen.getByRole('button', { name: 'Bỏ ảnh bo-di.jpg' }));

    // then
    expect(onChange).toHaveBeenCalledWith([kept]);
  });
});
