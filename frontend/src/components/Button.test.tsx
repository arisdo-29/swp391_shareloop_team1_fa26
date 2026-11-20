// @vitest-environment jsdom

import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Button } from './ui';

describe('Button component', () => {
  it('renders button with warning variant correctly', () => {
    render(<Button variant="warning">Báo cáo</Button>);
    const button = screen.getByRole('button', { name: 'Báo cáo' });
    expect(button).toBeTruthy();
    expect(button.className).toContain('bg-warning-soft');
    expect(button.className).toContain('text-warning');
  });

  it('renders button with default primary variant', () => {
    render(<Button>Xác nhận</Button>);
    const button = screen.getByRole('button', { name: 'Xác nhận' });
    expect(button.className).toContain('bg-primary');
    expect(button.className).toContain('text-white');
  });

  it('renders button with outline and danger variants', () => {
    const { rerender } = render(<Button variant="outline">Đóng</Button>);
    expect(screen.getByRole('button', { name: 'Đóng' }).className).toContain('bg-white');

    rerender(<Button variant="danger">Huỷ</Button>);
    expect(screen.getByRole('button', { name: 'Huỷ' }).className).toContain('bg-error-soft');
  });
});
