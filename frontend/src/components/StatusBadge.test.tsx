// @vitest-environment jsdom

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { StatusBadge } from './ui';
import { requestStatusLabel } from '../utils/request';

afterEach(() => cleanup());

describe('StatusBadge', () => {
  it('không truyền label thì giữ nhãn cũ', () => {
    // when
    render(<StatusBadge status="RESERVED" />);

    // then: nhãn bài đăng như trước, không bị đổi bởi prop mới
    expect(screen.getByText('Đã giữ chỗ')).toBeTruthy();
  });

  it.each([
    ['COMPLETED', 'text-success'],
    ['PENDING', 'text-warning'],
    ['RESERVED', 'text-warning'],
    ['AWAITING_LOGISTICS', 'text-warning'],
    ['LOGISTICS_CONFIRMED', 'text-warning'],
    ['AWAITING_HANDOVER', 'text-warning'],
    ['CANCELLED', 'text-error'],
    ['REJECTED', 'text-error'],
    ['DISPUTED', 'text-error'],
  ] as const)('có label thì hiện nhãn Request của %s với màu %s', (status, toneClass) => {
    // when
    render(<StatusBadge status={status} label={requestStatusLabel[status]} />);

    // then
    const badge = screen.getByText(requestStatusLabel[status]);
    expect(badge.className).toContain(toneClass);
  });
});
