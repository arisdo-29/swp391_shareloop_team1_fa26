// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ThemeSelect } from './ThemeSelect';
import { applyTheme, readTheme, THEME_KEY } from '../app/theme';

afterEach(() => {
  cleanup();
  localStorage.clear();
  vi.unstubAllGlobals();
});

describe('theme preference', () => {
  it('follows system changes and restores an explicit preference after remount', () => {
    const listeners = new Set<() => void>();
    const media = {
      matches: true,
      addEventListener: (_: string, fn: () => void) => listeners.add(fn),
      removeEventListener: (_: string, fn: () => void) => listeners.delete(fn),
    };
    vi.stubGlobal('matchMedia', () => media);
    const view = render(<ThemeSelect />);
    expect(document.documentElement.dataset.theme).toBe('dark');
    media.matches = false;
    listeners.forEach((fn) => fn());
    expect(document.documentElement.dataset.theme).toBe('light');
    fireEvent.change(screen.getByLabelText('Giao diện'), { target: { value: 'dark' } });
    expect(localStorage.getItem(THEME_KEY)).toBe('dark');
    view.unmount();
    expect(listeners.size).toBe(0);
    render(<ThemeSelect />);
    expect(document.documentElement.dataset.theme).toBe('dark');
    media.matches = false;
    listeners.forEach((fn) => fn());
    expect(document.documentElement.dataset.theme).toBe('dark');
  });

  it('falls back to system for invalid storage and applies a saved theme before rendering', () => {
    localStorage.setItem(THEME_KEY, 'invalid');
    expect(readTheme()).toBe('system');
    localStorage.setItem(THEME_KEY, 'dark');
    applyTheme(readTheme());
    expect(document.documentElement.style.colorScheme).toBe('dark');
  });

  it('still changes theme when storage is blocked', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    render(<ThemeSelect />);
    fireEvent.change(screen.getByLabelText('Giao diện'), { target: { value: 'light' } });
    expect(document.documentElement.dataset.theme).toBe('light');
    vi.restoreAllMocks();
  });
});
