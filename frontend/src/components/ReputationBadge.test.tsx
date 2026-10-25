// @vitest-environment jsdom

import { describe, expect, it } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import {
  formatAsDescribedRate,
  formatTrustStars,
  getRankLabel,
} from '../utils/reputation';
import { ReputationBadge } from './ReputationBadge';

describe('ReputationBadge helpers (FR-65)', () => {
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

  it('định dạng tỉ lệ đúng mô tả từ 0–1 thành phần trăm Math.round(x * 100)%', () => {
    expect(formatAsDescribedRate(1)).toBe('100%');
    expect(formatAsDescribedRate(0.95)).toBe('95%');
    expect(formatAsDescribedRate(0.954)).toBe('95%');
    expect(formatAsDescribedRate(0.956)).toBe('96%');
    expect(formatAsDescribedRate(0)).toBe('0%');
    expect(formatAsDescribedRate(null)).toBe('—');
    expect(formatAsDescribedRate(undefined)).toBe('—');
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
  it('hiển thị đầy đủ hạng, sao uy tín và tỉ lệ đúng mô tả', () => {
    render(<ReputationBadge trustStars={4.8} rank="GOLD" asDescribedRate={0.92} />);
    expect(screen.getByText('Hạng Vàng')).toBeTruthy();
    expect(screen.getByText('4.8/5 sao')).toBeTruthy();
    expect(screen.getByText('92% đúng mô tả')).toBeTruthy();
    cleanup();
  });

  it('hiển thị dạng compact gọn gàng khi compact=true', () => {
    render(<ReputationBadge trustStars={5} rank="DIAMOND" asDescribedRate={1} compact />);
    expect(screen.getByText('Kim cương')).toBeTruthy();
    expect(screen.getByText('5/5')).toBeTruthy();
    expect(screen.getByText('(100% đúng mô tả)')).toBeTruthy();
    cleanup();
  });

  it('không render gì nếu không có thông tin uy tín', () => {
    const { container } = render(<ReputationBadge trustStars={null} rank={null} asDescribedRate={null} />);
    expect(container.firstChild).toBeNull();
    cleanup();
  });
});
