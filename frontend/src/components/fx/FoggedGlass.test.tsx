// @vitest-environment jsdom

import { StrictMode, useRef } from 'react';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { FoggedGlass } from './FoggedGlass';

const MOUSE_HINT = 'Rê chuột để lau kính. Nhấn giữ để lau rộng hơn.';
const TOUCH_HINT = 'Kéo ngón tay trên kính để nhìn rõ hơn.';
const INTRO_SESSION_KEY = 'shareloop:hero-fog-intro';

function mockMatchMedia(reducedMotion: boolean, coarsePointer = false) {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    writable: true,
    value: vi.fn((query: string) => ({
      matches: (reducedMotion && query.includes('reduce')) || (coarsePointer && query.includes('coarse')),
      media: query,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  });
}

// Context 2D giả: jsdom không vẽ canvas. Mọi hàm vẽ đều là hàm rỗng, đủ để engine chạy.
function fakeContext() {
  const target: Record<string, unknown> = {
    filter: 'none',
    createImageData: (width: number, height: number) => ({ data: new Uint8ClampedArray(width * height * 4) }),
  };
  return new Proxy(target, {
    get: (object, key) => (typeof key === 'string' && key in object ? object[key] : vi.fn()),
  }) as unknown as CanvasRenderingContext2D;
}

// Ảnh giả: jsdom không tải ảnh, nên báo "đã tải xong" ngay sau khi gán src.
class LoadedImage {
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  decoding = '';
  naturalWidth = 800;
  naturalHeight = 600;
  set src(_value: string) {
    queueMicrotask(() => this.onload?.());
  }
}

/** Môi trường có canvas giả, ảnh tải được và rAF không chạy, để sương "hiện" trong jsdom. */
function mockWorkingCanvas() {
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(
    (() => fakeContext()) as unknown as typeof HTMLCanvasElement.prototype.getContext,
  );
  vi.stubGlobal('Image', LoadedImage);
  vi.stubGlobal('requestAnimationFrame', vi.fn(() => 1));
  vi.stubGlobal('cancelAnimationFrame', vi.fn());
}

async function renderWithFogShown() {
  render(<Hero />);
  // Chờ microtask của ảnh giả chạy xong.
  await act(async () => undefined);
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
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
  window.sessionStorage.clear();
  Reflect.deleteProperty(window, 'matchMedia');
});

describe('FoggedGlass gợi ý và nét lau tự động', () => {
  it('hint_whenFogShownWithMouse_showsMouseHint', async () => {
    // given
    mockMatchMedia(false);
    mockWorkingCanvas();

    // when
    await renderWithFogShown();

    // then
    const hint = screen.getByText(MOUSE_HINT);
    expect(hint.getAttribute('aria-hidden')).toBe('true');
    expect(hint.className).toContain('opacity-100');
  });

  it('hint_onCoarsePointer_showsTouchHint', async () => {
    // given
    mockMatchMedia(false, true);
    mockWorkingCanvas();

    // when
    await renderWithFogShown();

    // then
    expect(screen.getByText(TOUCH_HINT)).toBeTruthy();
    expect(screen.queryByText(MOUSE_HINT)).toBeNull();
  });

  it('hint_afterFirstWipe_fadesOut', async () => {
    // given
    mockMatchMedia(false);
    mockWorkingCanvas();
    await renderWithFogShown();

    // when
    fireEvent.pointerMove(screen.getByTestId('hero'), { clientX: 40, clientY: 40 });

    // then
    expect(screen.getByText(MOUSE_HINT).className).toContain('opacity-0');
  });

  it('hint_afterEightSecondsWithoutWipe_fadesOut', async () => {
    // given
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
    mockMatchMedia(false);
    mockWorkingCanvas();
    await renderWithFogShown();

    // when
    act(() => {
      vi.advanceTimersByTime(8000);
    });

    // then
    expect(screen.getByText(MOUSE_HINT).className).toContain('opacity-0');
  });

  it('introStroke_onFirstFogShown_marksSessionOnce', async () => {
    // given
    mockMatchMedia(false);
    mockWorkingCanvas();
    const setItem = vi.spyOn(Storage.prototype, 'setItem');

    // when: vào trang hai lần trong cùng phiên
    await renderWithFogShown();
    cleanup();
    await renderWithFogShown();

    // then: chỉ đánh dấu (tức chỉ lau tự động) một lần
    expect(window.sessionStorage.getItem(INTRO_SESSION_KEY)).toBe('1');
    expect(setItem.mock.calls.filter(([key]) => key === INTRO_SESSION_KEY)).toHaveLength(1);
  });

  it('introStroke_whenStorageBlocked_stillShowsFog', async () => {
    // given
    mockMatchMedia(false);
    mockWorkingCanvas();
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('storage blocked');
    });

    // when
    await renderWithFogShown();

    // then
    expect(screen.getByText(MOUSE_HINT)).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Món đồ cũ.' })).toBeTruthy();
  });
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
