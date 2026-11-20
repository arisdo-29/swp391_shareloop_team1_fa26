// @vitest-environment jsdom

import { describe, expect, it } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { formatTrustStars, getRankLabel } from '../utils/reputation';
import { ReputationBadge } from './ReputationBadge';

describe('ReputationBadge helpers (YC-06)', () => {
  it('chuyển mã hạng thành nhãn tiếng Việt tương ứng', () => {
    expect(getRankLabel('BRONZE')).toBe('Đồng');
    expect(getRankLabel('SILVER')).toBe('Bạc');
    expect(getRankLabel('GOLD')).toBe('Vàng');
    expect(getRankLabel('DIAMOND')).toBe('Kim cương');
    expect(getRankLabel('bronze')).toBe('Đồng');
    expect(getRankLabel('silver')).toBe('Bạc');
    expect(getRankLabel('gold')).toBe('Vàng');
    expect(getRankLabel('diamond')).toBe('Kim cương');
    expect(getRankLabel('')).toBe('');
    expect(getRankLabel(null)).toBe('');
    expect(getRankLabel(undefined)).toBe('');
  });

  it('định dạng số sao uy tín', () => {
    expect(formatTrustStars(5)).toBe('5');
    expect(formatTrustStars(4.5)).toBe('4.5');
    expect(formatTrustStars(0)).toBe('0');
    expect(formatTrustStars(null)).toBe('—');
    expect(formatTrustStars(undefined)).toBe('—');
  });
});

describe('ReputationBadge component', () => {
  it('hiển thị hạng, sao uy tín và không còn tỉ lệ đúng mô tả', () => {
    render(<ReputationBadge trustStars={4.8} rank="GOLD" />);
    expect(screen.getByText('Hạng Vàng')).toBeTruthy();
    const stars = screen.getByRole('img', { name: '4,8 trên 5 sao' });
    expect(stars.children).toHaveLength(5);
    expect(stars.lastElementChild?.querySelector<HTMLElement>('span')?.style.width).toBe('80%');
    expect(screen.queryByText(/đúng mô tả/)).toBeNull();
    cleanup();
  });

  it('hiển thị dạng compact gọn gàng khi compact=true', () => {
    render(<ReputationBadge trustStars={5} rank="DIAMOND" compact />);
    expect(screen.getByText('Kim cương')).toBeTruthy();
    expect(screen.getByRole('img', { name: '5 trên 5 sao' })).toBeTruthy();
    expect(screen.queryByText(/đúng mô tả/)).toBeNull();
    cleanup();
  });

  it('không render gì nếu không có thông tin uy tín', () => {
    const { container } = render(<ReputationBadge trustStars={null} rank={null} />);
    expect(container.firstChild).toBeNull();
    cleanup();
  });
});
