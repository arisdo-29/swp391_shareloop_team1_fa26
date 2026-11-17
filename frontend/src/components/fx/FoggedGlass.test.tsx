// @vitest-environment jsdom

import { StrictMode, useRef } from 'react';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { FoggedGlass } from './FoggedGlass';

function mockMatchMedia(reducedMotion: boolean) {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: vi.fn((query: string) => ({
      matches: reducedMotion && query.includes('reduce'),
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  });
}

// Context 2D giả: jsdom không vẽ canvas, chỉ cần đủ hàm để engine chạy.
function fakeContext() {
  return {
    filter: 'none',
    clearRect: vi.fn(),
    drawImage: vi.fn(),
    fillRect: vi.fn(),
    putImageData: vi.fn(),
    createImageData: (width: number, height: number) => ({ data: new Uint8ClampedArray(width * height * 4) }),
  } as unknown as CanvasRenderingContext2D;
}

function Hero() {
  const heroRef = useRef<HTMLElement>(null);
  return (
    <section ref={heroRef} data-testid="hero">
      <FoggedGlass hostRef={heroRef} imageSrc="/api/v1/media/1" />
      <h1>Món đồ cũ.</h1>
      <button type="button">Tìm đồ</button>
    </section>
  );
}

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  Reflect.deleteProperty(window, 'matchMedia');
});

describe('FoggedGlass', () => {
  it('render_whenCanvasContextIsNull_keepsHeroContent', () => {
    // given
    mockMatchMedia(false);
    const getContext = vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);

    // when
    render(<Hero />);

    // then
    expect(screen.getByRole('heading', { name: 'Món đồ cũ.' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Tìm đồ' })).toBeTruthy();
    expect(getContext).toHaveBeenCalled();
  });

  it('render_whenReducedMotion_createsNoCanvas', () => {
    // given
    mockMatchMedia(true);

    // when
    render(<Hero />);

    // then
    expect(screen.getByTestId('hero').querySelector('canvas')).toBeNull();
    expect(screen.getByRole('heading', { name: 'Món đồ cũ.' })).toBeTruthy();
  });

  it('render_whenMatchMediaMissing_createsNoCanvas', () => {
    // when
    render(<Hero />);

    // then
    expect(screen.getByTestId('hero').querySelector('canvas')).toBeNull();
    expect(screen.getByRole('heading', { name: 'Món đồ cũ.' })).toBeTruthy();
  });

  it('render_whenMotionAllowed_addsHiddenCanvas', () => {
    // given
    mockMatchMedia(false);
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);

    // when
    render(<Hero />);

    // then: canvas chỉ để trang trí, không nhận sự kiện
    const canvas = screen.getByTestId('hero').querySelector('canvas');
    expect(canvas?.getAttribute('aria-hidden')).toBe('true');
    expect(canvas?.className).toContain('pointer-events-none');
  });

  it('unmount_inStrictMode_removesEveryListener', () => {
    // given
    mockMatchMedia(false);
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(
      (() => fakeContext()) as unknown as typeof HTMLCanvasElement.prototype.getContext,
    );
    vi.stubGlobal('requestAnimationFrame', vi.fn(() => 1));
    vi.stubGlobal('cancelAnimationFrame', vi.fn());
    const addToWindow = vi.spyOn(window, 'addEventListener');
    const removeFromWindow = vi.spyOn(window, 'removeEventListener');
    const addToHero = vi.spyOn(HTMLElement.prototype, 'addEventListener');
    const removeFromHero = vi.spyOn(HTMLElement.prototype, 'removeEventListener');

    // when: StrictMode mount → unmount → mount, rồi unmount thật
    const view = render(
      <StrictMode>
        <Hero />
      </StrictMode>,
    );
    view.unmount();

    // then
    const pointerUpAdded = addToWindow.mock.calls.filter(([type]) => type === 'pointerup').length;
    const pointerUpRemoved = removeFromWindow.mock.calls.filter(([type]) => type === 'pointerup').length;
    expect(pointerUpAdded).toBeGreaterThan(0);
    expect(pointerUpRemoved).toBe(pointerUpAdded);
    // React cũng gắn listener lên thẻ gốc, nên chỉ đếm các lần gọi trên chính khối hero.
    const isHero = (element: unknown) => element instanceof HTMLElement && element.dataset.testid === 'hero';
    const heroAdded = addToHero.mock.contexts.filter(isHero).length;
    const heroRemoved = removeFromHero.mock.contexts.filter(isHero).length;
    expect(heroAdded).toBeGreaterThan(0);
    expect(heroRemoved).toBe(heroAdded);
  });
});
