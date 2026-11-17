import { type RefObject, useEffect, useRef, useState } from 'react';
import { resolveMediaUrl } from '../../services/mediaUrl';
import {
  FogEngine,
  MAX_DEVICE_PIXEL_RATIO,
  WIPE_RADIUS_MOUSE_PRESSED_PX,
  WIPE_RADIUS_MOUSE_PX,
  WIPE_RADIUS_TOUCH_PX,
} from './fogEngine';

const REDUCED_MOTION_QUERY = '(prefers-reduced-motion: reduce)';
// Rê hoặc chạm lên phần tử bấm được thì không lau, để bấm nút và gõ chữ không bị vệt lau đuổi theo.
const CONTROL_SELECTOR = 'button, a, input, select, textarea, [role="button"]';
// Dự phòng khi thiếu biến --fx-fog-tint trong globals.css.
const FALLBACK_FOG_TINT = 'white';

type FrameLoop = {
  wake: () => void;
  refresh: () => void;
  setOnScreen: (onScreen: boolean) => void;
  dispose: () => void;
};

function readMotionAllowed(): boolean {
  // jsdom và trình duyệt rất cũ không có matchMedia: không bật hiệu ứng cho an toàn.
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false;
  return !window.matchMedia(REDUCED_MOTION_QUERY).matches;
}

/** true khi người dùng không bật "giảm chuyển động"; tự cập nhật khi họ đổi cài đặt. */
function useMotionAllowed(): boolean {
  const [motionAllowed, setMotionAllowed] = useState(readMotionAllowed);
  useEffect(() => {
    if (typeof window.matchMedia !== 'function') return undefined;
    const query = window.matchMedia(REDUCED_MOTION_QUERY);
    const handleChange = () => setMotionAllowed(!query.matches);
    query.addEventListener?.('change', handleChange);
    return () => query.removeEventListener?.('change', handleChange);
  }, []);
  return motionAllowed;
}

function readFogTint(): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue('--fx-fog-tint').trim();
  return value || FALLBACK_FOG_TINT;
}

function isOverControl(target: EventTarget | null): boolean {
  return target instanceof Element && target.closest(CONTROL_SELECTOR) !== null;
}

/** Vòng lặp requestAnimationFrame: chỉ chạy khi còn việc (đang lau/đang phủ lại) và hero đang hiện. */
function createFrameLoop(engine: FogEngine): FrameLoop {
  let frameId = 0;
  let lastTime = 0;
  let onScreen = true;
  const canRun = () => onScreen && !document.hidden;
  const stop = () => {
    if (frameId) cancelAnimationFrame(frameId);
    frameId = 0;
  };
  const tick = (time: number) => {
    frameId = 0;
    const keepGoing = engine.step((time - lastTime) / 1000);
    lastTime = time;
    if (keepGoing && canRun()) frameId = requestAnimationFrame(tick);
  };
  const wake = () => {
    if (frameId || !canRun()) return;
    lastTime = performance.now();
    frameId = requestAnimationFrame(tick);
  };
  const refresh = () => (canRun() ? wake() : stop());
  const setOnScreen = (value: boolean) => {
    onScreen = value;
    refresh();
  };
  return { wake, refresh, setOnScreen, dispose: stop };
}

/** Theo dõi kích thước hero, hero ra/vào màn hình và tab ẩn/hiện. Trả về hàm dọn dẹp. */
function watchHost(host: HTMLElement, engine: FogEngine, loop: FrameLoop): () => void {
  const resize = () => {
    const pixelRatio = Math.min(window.devicePixelRatio || 1, MAX_DEVICE_PIXEL_RATIO);
    engine.resize(host.clientWidth, host.clientHeight, pixelRatio);
    loop.wake();
  };
  resize();
  const resizeObserver = typeof ResizeObserver === 'function' ? new ResizeObserver(resize) : null;
  resizeObserver?.observe(host);
  const intersectionObserver =
    typeof IntersectionObserver === 'function'
      ? new IntersectionObserver(([entry]) => loop.setOnScreen(entry.isIntersecting))
      : null;
  intersectionObserver?.observe(host);
  document.addEventListener('visibilitychange', loop.refresh);
  return () => {
    resizeObserver?.disconnect();
    intersectionObserver?.disconnect();
    document.removeEventListener('visibilitychange', loop.refresh);
  };
}

/** Chuột và bút: rê để lau, giữ nút để lau rộng hơn, rời hero thì nhấc nét. */
function attachPointerWiping(host: HTMLElement, wipeAt: (x: number, y: number, radius: number) => void, engine: FogEngine) {
  let pressed = false;
  const handleMove = (event: PointerEvent) => {
    if (event.pointerType === 'touch') return;
    if (isOverControl(event.target)) {
      engine.lift();
      return;
    }
    wipeAt(event.clientX, event.clientY, pressed ? WIPE_RADIUS_MOUSE_PRESSED_PX : WIPE_RADIUS_MOUSE_PX);
  };
  const handleDown = (event: PointerEvent) => {
    if (event.pointerType !== 'touch') pressed = true;
  };
  const handleUp = () => {
    pressed = false;
  };
  const handleLeave = (event: PointerEvent) => {
    if (event.pointerType !== 'touch') engine.lift();
  };
  host.addEventListener('pointermove', handleMove);
  host.addEventListener('pointerdown', handleDown);
  window.addEventListener('pointerup', handleUp);
  host.addEventListener('pointerleave', handleLeave);
  return () => {
    host.removeEventListener('pointermove', handleMove);
    host.removeEventListener('pointerdown', handleDown);
    window.removeEventListener('pointerup', handleUp);
    host.removeEventListener('pointerleave', handleLeave);
  };
}

/**
 * Cảm ứng: dùng touch event với passive: true để ngón tay vừa cuộn trang vừa lau.
 * Tuyệt đối không preventDefault, nếu không trang sẽ không cuộn được khi chạm vào hero.
 */
function attachTouchWiping(host: HTMLElement, wipeAt: (x: number, y: number, radius: number) => void, engine: FogEngine) {
  const handleMove = (event: TouchEvent) => {
    const touch = event.touches[0];
    if (!touch || isOverControl(event.target)) return;
    wipeAt(touch.clientX, touch.clientY, WIPE_RADIUS_TOUCH_PX);
  };
  const handleStart = (event: TouchEvent) => {
    engine.lift();
    handleMove(event);
  };
  const handleEnd = () => engine.lift();
  const options = { passive: true };
  host.addEventListener('touchstart', handleStart, options);
  host.addEventListener('touchmove', handleMove, options);
  host.addEventListener('touchend', handleEnd, options);
  host.addEventListener('touchcancel', handleEnd, options);
  return () => {
    host.removeEventListener('touchstart', handleStart);
    host.removeEventListener('touchmove', handleMove);
    host.removeEventListener('touchend', handleEnd);
    host.removeEventListener('touchcancel', handleEnd);
  };
}

function attachWiping(host: HTMLElement, engine: FogEngine, wake: () => void): () => void {
  const wipeAt = (clientX: number, clientY: number, radius: number) => {
    const rect = host.getBoundingClientRect();
    engine.wipe(clientX - rect.left, clientY - rect.top, radius);
    wake();
  };
  const detachPointer = attachPointerWiping(host, wipeAt, engine);
  const detachTouch = attachTouchWiping(host, wipeAt, engine);
  return () => {
    detachPointer();
    detachTouch();
  };
}

type FoggedGlassProps = {
  /** Khối hero: nơi lắng nghe chuột/cảm ứng (canvas không nhận sự kiện). */
  hostRef: RefObject<HTMLElement | null>;
  /** Ảnh nền đang hiện của hero (đường dẫn như trong item.images). */
  imageSrc?: string;
  /** false để tắt hẳn hiệu ứng. */
  enabled?: boolean;
};

/**
 * Lớp kính phủ sương nằm trên ảnh nền hero; rê chuột hoặc kéo ngón tay để lau.
 * Không render gì khi người dùng bật giảm chuyển động hoặc trình duyệt không hỗ trợ.
 */
export function FoggedGlass({ hostRef, imageSrc, enabled = true }: FoggedGlassProps) {
  const motionAllowed = useMotionAllowed();
  const active = enabled && motionAllowed;
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const engineRef = useRef<FogEngine | null>(null);
  const wakeRef = useRef<() => void>(() => undefined);

  // useEffect chạy sau khi DOM đã gắn, nên lúc này canvas và khối hero đã có thật.
  // Hàm return dọn sạch mọi thứ: StrictMode (dev) mount, unmount rồi mount lại vẫn không rò listener.
  useEffect(() => {
    const host = hostRef.current;
    const canvas = canvasRef.current;
    if (!active || !host || !canvas) return undefined;
    const engine = FogEngine.create(canvas, readFogTint());
    if (!engine) return undefined;
    const loop = createFrameLoop(engine);
    const stopWatching = watchHost(host, engine, loop);
    const detachWiping = attachWiping(host, engine, loop.wake);
    engineRef.current = engine;
    wakeRef.current = loop.wake;
    return () => {
      detachWiping();
      stopWatching();
      loop.dispose();
      engine.dispose();
      engineRef.current = null;
      wakeRef.current = () => undefined;
    };
  }, [active, hostRef]);

  // Tải ảnh nền đang hiện để dựng lớp sương; cùng URL với thẻ <img> nên lấy lại từ cache.
  useEffect(() => {
    const engine = engineRef.current;
    const url = resolveMediaUrl(imageSrc);
    if (!active || !engine) return undefined;
    if (!url) {
      engine.setImage(null);
      wakeRef.current();
      return undefined;
    }
    // Cờ alive: callback ảnh về trễ sau khi effect đã dọn thì bỏ qua, không đụng engine cũ.
    let alive = true;
    const image = new Image();
    image.decoding = 'async';
    image.onload = () => {
      if (!alive) return;
      engine.setImage(image);
      wakeRef.current();
    };
    // Ảnh lỗi thì bỏ lớp sương, hero hiện như cũ. Không log vì thẻ <img> của hero đã tự xử lý.
    image.onerror = () => {
      if (!alive) return;
      engine.setImage(null);
      wakeRef.current();
    };
    image.src = url;
    return () => {
      alive = false;
      image.onload = null;
      image.onerror = null;
    };
  }, [active, imageSrc]);

  if (!active) return null;

  return (
    <canvas
      ref={canvasRef}
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 z-0 block size-full"
    />
  );
}
