// Hiệu ứng "kính phủ sương, rê chuột lau sạch" cho hero trang chủ, vẽ bằng Canvas 2D.
//
// Mặt nạ là mảng Float32 riêng (0 = còn sương, >= 1 = đã lau sạch), lưu ở độ phân giải thấp.
// KHÔNG đọc ngược pixel từ canvas (getImageData/toDataURL/toBlob): ảnh nền là ảnh bài đăng
// khác origin nên canvas có vẽ ảnh đó bị "tainted", đọc ngược sẽ ném SecurityError.

import { BEAD_SEED, type Bead, drawBeads, generateBeads, seededRandom } from './fogBeads';
import {
  AMBIENT_DRIP_MIN_SECONDS,
  AMBIENT_DRIP_RANGE_SECONDS,
  type ClearTrail,
  type Drip,
  FAST_WIPE_SEGMENT_PX,
  MAX_DRIPS_DESKTOP,
  WIPE_DISTANCE_PER_DRIP_PX,
  createDrip,
  drawDrips,
  updateDrips,
} from './fogDrips';

/* ------------------------------------------------------------------ hằng số chỉnh được */

/** Kích thước một ô lưới mặt nạ (css px). Nhỏ hơn thì rìa vết lau mịn hơn nhưng tốn CPU hơn. */
export const FOG_CELL_SIZE_PX = 5;
/** Bán kính vết lau khi rê chuột. */
export const WIPE_RADIUS_MOUSE_PX = 38;
/** Bán kính vết lau khi đang giữ nút chuột. */
export const WIPE_RADIUS_MOUSE_PRESSED_PX = 58;
/** Bán kính vết lau bằng ngón tay. */
export const WIPE_RADIUS_TOUCH_PX = 40;
/** Tốc độ sương phủ lại: mỗi giây giá trị mặt nạ giảm chừng này. */
export const REFOG_PER_SECOND = 0.13;
/** Giá trị đóng dấu > 1 tạo khoảng "kính còn ướt": giữ sạch khoảng 2,7 giây rồi mới mờ lại. */
export const WIPE_STRENGTH = 1.35;
/** Độ đậm tối đa của lớp sương (0-1). Để dưới 1 cho ảnh sắc nét vẫn lấp ló phía sau. */
export const FOG_MAX_OPACITY = 0.9;
/**
 * Độ đậm của lớp màu sương (biến CSS --fx-fog-tint, đang là xanh đậm) phủ lên ảnh đã làm mờ.
 * Cao hơn thì chữ trắng dễ đọc hơn nhưng ảnh khó nhận ra hơn.
 */
export const FOG_TINT_OPACITY = 0.45;
/** Độ mờ của ảnh dưới lớp sương (css px). */
export const FOG_BLUR_PX = 16;
/** Trần devicePixelRatio: màn hình 3x không cần vẽ đủ 3x cho một lớp sương đã mờ. */
export const MAX_DEVICE_PIXEL_RATIO = 1.5;
/** Con trỏ nhảy xa hơn mức này thì chỉ đóng dấu điểm mới, không kẻ vệt nối từ chỗ cũ. */
export const STROKE_JUMP_LIMIT_PX = 220;
/** Khớp với class scale-[1.05] của ảnh nền hero để vùng đã lau không bị lệch hình. */
export const BACKGROUND_IMAGE_SCALE = 1.05;
/** Thời gian chuyển lớp sương cũ sang mới khi carousel đổi ảnh (giây). Vùng đã lau vẫn giữ nguyên. */
export const FOG_CROSSFADE_SECONDS = 0.4;
/** Nét lau tự động khi vào trang: chờ chừng này giây sau khi sương hiện rồi mới lau. */
export const INTRO_STROKE_DELAY_SECONDS = 0.6;
/** Thời gian vẽ hết nét lau tự động (giây). */
export const INTRO_STROKE_SECONDS = 1.2;
/** Bán kính nét lau tự động. */
export const INTRO_STROKE_RADIUS_PX = 44;

/** Lớp sương dựng ở 1/4 kích thước: đã làm mờ mạnh nên phóng lên không thấy khác mà rẻ hơn nhiều. */
const PLATE_DOWNSCALE = 4;
/** Nhánh dự phòng (trình duyệt không có ctx.filter): thu ảnh còn 1/12 rồi phóng lên để làm mờ. */
const FALLBACK_DOWNSCALE = 12;
/** Một frame tính tối đa chừng này giây, tránh sương phủ "nhảy cóc" sau khi tab bị treo. */
const MAX_STEP_SECONDS = 0.1;

/* ------------------------------------------------------------------ mặt nạ (hàm thuần) */

export type Point = { x: number; y: number };

export type FogMask = {
  columns: number;
  rows: number;
  cellSize: number;
  values: Float32Array;
};

export type Rect = { x: number; y: number; width: number; height: number };

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

function smoothstep01(value: number) {
  const t = clamp(value, 0, 1);
  return t * t * (3 - 2 * t);
}

/** Tạo mặt nạ phủ kín sương cho vùng rộng widthPx x heightPx (css px). */
export function createMask(widthPx: number, heightPx: number, cellSize = FOG_CELL_SIZE_PX): FogMask {
  const columns = Math.max(1, Math.ceil(widthPx / cellSize));
  const rows = Math.max(1, Math.ceil(heightPx / cellSize));
  return { columns, rows, cellSize, values: new Float32Array(columns * rows) };
}

/**
 * Mặt nạ cho kích thước mới, giữ nguyên vùng đã lau ở đúng vị trí cũ (tính từ góc trên trái).
 * Cần vì hero đổi chiều cao khi dữ liệu tải xong (thêm chip), không nên xoá mất vết người dùng vừa lau.
 */
export function resizeMask(previous: FogMask, widthPx: number, heightPx: number): FogMask {
  const next = createMask(widthPx, heightPx, previous.cellSize);
  const columns = Math.min(previous.columns, next.columns);
  const rows = Math.min(previous.rows, next.rows);
  for (let row = 0; row < rows; row++) {
    const start = row * previous.columns;
    next.values.set(previous.values.subarray(start, start + columns), row * next.columns);
  }
  return next;
}

/** Đọc giá trị mặt nạ tại một điểm (css px); ngoài lưới trả 0. */
export function maskValueAt(mask: FogMask, x: number, y: number): number {
  const column = Math.floor(x / mask.cellSize);
  const row = Math.floor(y / mask.cellSize);
  if (column < 0 || row < 0 || column >= mask.columns || row >= mask.rows) return 0;
  return mask.values[row * mask.columns + column];
}

/** Đóng một dấu lau tròn tại (x, y). Chỉ tăng giá trị, không làm vùng đã sạch mờ đi. */
export function stampMask(mask: FogMask, x: number, y: number, radius: number, strength = WIPE_STRENGTH) {
  const centerColumn = x / mask.cellSize;
  const centerRow = y / mask.cellSize;
  const radiusInCells = radius / mask.cellSize;
  const firstColumn = Math.max(0, Math.floor(centerColumn - radiusInCells));
  const lastColumn = Math.min(mask.columns - 1, Math.ceil(centerColumn + radiusInCells));
  const firstRow = Math.max(0, Math.floor(centerRow - radiusInCells));
  const lastRow = Math.min(mask.rows - 1, Math.ceil(centerRow + radiusInCells));

  for (let row = firstRow; row <= lastRow; row++) {
    for (let column = firstColumn; column <= lastColumn; column++) {
      const distance = Math.hypot(column + 0.5 - centerColumn, row + 0.5 - centerRow) / radiusInCells;
      if (distance >= 1) continue;
      // Nửa trong sạch hẳn, nửa ngoài mềm dần để rìa vết lau không bị gắt.
      const edge = distance < 0.5 ? 1 : 1 - (distance - 0.5) / 0.5;
      const value = smoothstep01(edge) * strength;
      const index = row * mask.columns + column;
      if (value > mask.values[index]) mask.values[index] = value;
    }
  }
}

/**
 * Lau từ điểm trước (from) tới điểm mới (to). from = null nghĩa là nét mới (vừa nhấc tay/chuột),
 * khi đó chỉ đóng dấu tại điểm mới để không kẻ vệt nối.
 */
export function wipeStroke(
  mask: FogMask,
  from: Point | null,
  to: Point,
  radius: number,
  strength = WIPE_STRENGTH,
) {
  if (!from) {
    stampMask(mask, to.x, to.y, radius, strength);
    return;
  }
  const distance = Math.hypot(to.x - from.x, to.y - from.y);
  // Con trỏ nhảy xa (ví dụ rời hero rồi vào ở chỗ khác): coi như nét mới.
  if (distance > STROKE_JUMP_LIMIT_PX) {
    stampMask(mask, to.x, to.y, radius, strength);
    return;
  }
  // Đóng dấu dày (mỗi 1/4 bán kính) để rê chuột nhanh vẫn không để hở giữa hai sự kiện.
  const steps = Math.max(1, Math.ceil(distance / (radius * 0.25)));
  for (let step = 1; step <= steps; step++) {
    const progress = step / steps;
    stampMask(mask, from.x + (to.x - from.x) * progress, from.y + (to.y - from.y) * progress, radius, strength);
  }
}

/** Cho sương phủ lại một lượng amount. Trả về true nếu có ô nào thay đổi (cần vẽ lại). */
export function decayMask(mask: FogMask, amount: number): boolean {
  const values = mask.values;
  let changed = false;
  for (let index = 0; index < values.length; index++) {
    if (values[index] <= 0) continue;
    values[index] = Math.max(0, values[index] - amount);
    changed = true;
  }
  return changed;
}

/** Chuyển mặt nạ sang điểm ảnh RGBA trắng, alpha = độ sương còn lại (1 - đã lau). */
export function fillMaskAlpha(mask: FogMask, pixels: Uint8ClampedArray) {
  const values = mask.values;
  for (let index = 0; index < values.length; index++) {
    const pixel = index * 4;
    pixels[pixel] = 255;
    pixels[pixel + 1] = 255;
    pixels[pixel + 2] = 255;
    pixels[pixel + 3] = Math.round(255 * (1 - smoothstep01(values[index])));
  }
}

/** Vị trí vẽ ảnh kiểu object-fit: cover, căn giữa, phóng thêm `scale` giống ảnh nền hero. */
export function coverFit(imageWidth: number, imageHeight: number, boxWidth: number, boxHeight: number, scale = 1): Rect {
  const ratio = Math.max(boxWidth / imageWidth, boxHeight / imageHeight) * scale;
  const width = imageWidth * ratio;
  const height = imageHeight * ratio;
  return { x: (boxWidth - width) / 2, y: (boxHeight - height) / 2, width, height };
}

/**
 * Điểm trên nét lau tự động ứng với progress 0 → 1: một đường cong ngang phần dưới hero
 * (dưới ô tìm kiếm và carousel), đi nhanh dần rồi chậm lại như một cái quẹt tay.
 */
export function introStrokePoint(progress: number, widthPx: number, heightPx: number): Point {
  const eased = smoothstep01(progress);
  return {
    x: widthPx * (0.12 + 0.76 * eased),
    y: heightPx * (0.88 - 0.05 * Math.sin(eased * Math.PI)),
  };
}

/** Safari cũ không có ctx.filter: khi đó phải làm mờ bằng cách thu nhỏ rồi phóng to. */
export function supportsCanvasFilter(context: CanvasRenderingContext2D): boolean {
  return typeof context.filter === 'string';
}

/* ------------------------------------------------------------------ engine vẽ */

export type FogColors = {
  /** Màu lớp sương (biến CSS --fx-fog-tint). */
  tint: string;
  /** Màu viền tối của hạt sương và giọt nước (biến CSS --fx-fog-shade). */
  shade: string;
};

/** Một canvas phụ vẽ ngoài màn hình, kèm context 2D của nó. */
type Surface = { canvas: HTMLCanvasElement; context: CanvasRenderingContext2D };

function createSurface(): Surface | null {
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d');
  return context ? { canvas, context } : null;
}

/**
 * Giữ canvas sương của hero: dựng lớp sương từ ảnh, nhận nét lau, cho sương phủ lại,
 * cho giọt nước chảy và vẽ khi cần. Không biết gì về React; phần React gọi các method public.
 */
export class FogEngine {
  private mask: FogMask = createMask(1, 1);
  private maskPixels: ImageData | null = null;
  private image: HTMLImageElement | null = null;
  private beads: Bead[] = [];
  private drips: Drip[] = [];
  private maxDrips = MAX_DRIPS_DESKTOP;
  private ambientCountdown = AMBIENT_DRIP_MIN_SECONDS;
  private wipedDistance = 0;
  private widthPx = 0;
  private heightPx = 0;
  private pixelRatio = 1;
  private lastPoint: Point | null = null;
  private fogReady = false;
  private needsDraw = false;
  /** 0 → 1 trong lúc chuyển từ lớp sương cũ sang mới; 1 = không chuyển. */
  private crossfadeProgress = 1;
  /** Số giây đã trôi của nét lau tự động; null = không chạy. */
  private introElapsedSeconds: number | null = null;
  private introLastPoint: Point | null = null;
  // Vệt sạch phía sau giọt ghi thẳng vào mặt nạ, giống một nét lau mảnh.
  private readonly clearTrail: ClearTrail = (from, to, radius, strength) =>
    wipeStroke(this.mask, from, to, radius, strength);

  private constructor(
    private readonly canvas: HTMLCanvasElement,
    private readonly context: CanvasRenderingContext2D,
    /** Ảnh đã làm mờ, ở 1/4 kích thước. */
    private readonly plate: Surface,
    /** Lớp sương đủ kích thước: ảnh mờ phóng lên cộng hạt sương; mỗi frame chỉ cần chép lớp này. */
    private readonly fogLayer: Surface,
    /** Mặt nạ: mỗi ô lưới là một pixel. Canvas này không bao giờ vẽ ảnh ngoài. */
    private readonly maskSurface: Surface,
    /** Bản chép lớp sương của ảnh trước, dùng khi chuyển ảnh. */
    private readonly previousFog: Surface,
    /** Nơi trộn lớp sương cũ và mới trong lúc chuyển ảnh. */
    private readonly blendSurface: Surface,
    private readonly colors: FogColors,
    private readonly random: () => number,
  ) {}

  /** Tạo engine; trả null nếu trình duyệt (hoặc jsdom) không cho Canvas 2D. */
  static create(canvas: HTMLCanvasElement, colors: FogColors, random: () => number = Math.random): FogEngine | null {
    const context = canvas.getContext('2d');
    const plate = createSurface();
    const fogLayer = createSurface();
    const maskSurface = createSurface();
    const previousFog = createSurface();
    const blendSurface = createSurface();
    if (!context || !plate || !fogLayer || !maskSurface || !previousFog || !blendSurface) return null;
    return new FogEngine(canvas, context, plate, fogLayer, maskSurface, previousFog, blendSurface, colors, random);
  }

  /** Đổi kích thước theo khối hero (css px). Lớp sương, hạt sương dựng lại; vùng đã lau được giữ. */
  resize(widthPx: number, heightPx: number, pixelRatio: number) {
    const width = Math.max(1, Math.round(widthPx));
    const height = Math.max(1, Math.round(heightPx));
    if (width === this.widthPx && height === this.heightPx && pixelRatio === this.pixelRatio) return;
    this.widthPx = width;
    this.heightPx = height;
    this.pixelRatio = pixelRatio;
    const canvasWidth = Math.round(width * pixelRatio);
    const canvasHeight = Math.round(height * pixelRatio);
    for (const canvas of [this.canvas, this.fogLayer.canvas, this.previousFog.canvas, this.blendSurface.canvas]) {
      canvas.width = canvasWidth;
      canvas.height = canvasHeight;
    }
    this.crossfadeProgress = 1;
    this.mask = resizeMask(this.mask, width, height);
    this.maskSurface.canvas.width = this.mask.columns;
    this.maskSurface.canvas.height = this.mask.rows;
    this.maskPixels = this.maskSurface.context.createImageData(this.mask.columns, this.mask.rows);
    this.beads = generateBeads(width, height, seededRandom(BEAD_SEED));
    this.drips = [];
    this.lastPoint = null;
    this.rebuildFog();
  }

  /**
   * Đặt ảnh nền cần phủ sương; null (chưa tải hoặc lỗi) thì không vẽ sương.
   * Đổi ảnh thì lớp sương cũ mờ dần sang lớp mới, mặt nạ giữ nguyên nên vùng đã lau vẫn sạch.
   */
  setImage(image: HTMLImageElement | null) {
    const nextImage = image && image.naturalWidth > 0 ? image : null;
    if (nextImage === this.image) return;
    this.keepPreviousFog();
    this.image = nextImage;
    this.rebuildFog();
    // Lần đầu (chưa có lớp cũ) thì lớp sương hiện dần từ trong suốt, không "bật" đột ngột.
    this.crossfadeProgress = nextImage ? 0 : 1;
  }

  /** Bắt đầu nét lau tự động (chỉ gọi một lần mỗi phiên, phía React quyết định). */
  startIntroStroke() {
    this.introElapsedSeconds = 0;
    this.introLastPoint = null;
  }

  /** Số giọt chảy cùng lúc tối đa (React tính theo màn hình và số nhân CPU). */
  setMaxDrips(count: number) {
    this.maxDrips = count;
    if (this.drips.length > count) this.drips = this.drips.slice(0, count);
  }

  /** Lau tại (x, y) css px tính từ góc trên trái khối hero. */
  wipe(x: number, y: number, radius: number) {
    const from = this.lastPoint;
    wipeStroke(this.mask, from, { x, y }, radius);
    this.lastPoint = { x, y };
    this.needsDraw = true;
    if (from) this.gatherWater(Math.hypot(x - from.x, y - from.y), x, y, radius);
  }

  /** Nhấc nét: lần lau sau không nối với điểm cũ. */
  lift() {
    this.lastPoint = null;
    this.wipedDistance = 0;
  }

  /**
   * Chạy một frame. Trả về true nếu cần frame tiếp theo: đang phủ lại, có giọt đang chảy,
   * đang chuyển ảnh, đang lau tự động, hoặc đang hiện sương (để thỉnh thoảng còn sinh giọt).
   */
  step(deltaSeconds: number): boolean {
    const seconds = clamp(deltaSeconds, 0, MAX_STEP_SECONDS);
    const refogging = decayMask(this.mask, seconds * REFOG_PER_SECOND);
    const crossfading = this.advanceCrossfade(seconds);
    const introPlaying = this.advanceIntroStroke(seconds);
    if (this.fogReady) this.spawnAmbientDrip(seconds);
    const dripping = this.drips.length > 0;
    if (dripping) this.drips = updateDrips(this.drips, seconds, this.heightPx, this.random, this.clearTrail);
    const busy = refogging || crossfading || introPlaying || dripping;
    if (busy || this.needsDraw) this.draw();
    this.needsDraw = false;
    return busy || this.fogReady;
  }

  dispose() {
    this.image = null;
    this.fogReady = false;
    this.drips = [];
    this.introElapsedSeconds = null;
    this.context.clearRect(0, 0, this.canvas.width, this.canvas.height);
    // Thu canvas phụ về 0 để trình duyệt giải phóng bộ nhớ ngay.
    for (const surface of [this.plate, this.fogLayer, this.maskSurface, this.previousFog, this.blendSurface]) {
      surface.canvas.width = 0;
    }
  }

  private advanceCrossfade(seconds: number): boolean {
    if (this.crossfadeProgress >= 1) return false;
    this.crossfadeProgress = Math.min(1, this.crossfadeProgress + seconds / FOG_CROSSFADE_SECONDS);
    return true;
  }

  /** Nét lau tự động: chờ một chút rồi lau theo đường cong introStrokePoint. */
  private advanceIntroStroke(seconds: number): boolean {
    if (this.introElapsedSeconds === null || !this.fogReady) return false;
    this.introElapsedSeconds += seconds;
    const progress = (this.introElapsedSeconds - INTRO_STROKE_DELAY_SECONDS) / INTRO_STROKE_SECONDS;
    if (progress < 0) return true;
    const point = introStrokePoint(progress, this.widthPx, this.heightPx);
    wipeStroke(this.mask, this.introLastPoint, point, INTRO_STROKE_RADIUS_PX);
    this.introLastPoint = point;
    if (progress >= 1) {
      this.introElapsedSeconds = null;
      this.introLastPoint = null;
    }
    return true;
  }

  /** Chép lớp sương đang hiện sang previousFog để lát nữa mờ dần sang ảnh mới. */
  private keepPreviousFog() {
    const { canvas, context } = this.previousFog;
    context.clearRect(0, 0, canvas.width, canvas.height);
    if (this.fogReady) context.drawImage(this.fogLayer.canvas, 0, 0);
  }

  /** Trộn lớp cũ (đậm hẳn) với lớp mới (đậm dần) để tổng độ đậm không bị hụt giữa chừng. */
  private blendFogLayers(): HTMLCanvasElement {
    const { canvas, context } = this.blendSurface;
    context.globalAlpha = 1;
    context.clearRect(0, 0, canvas.width, canvas.height);
    context.drawImage(this.previousFog.canvas, 0, 0);
    context.globalAlpha = smoothstep01(this.crossfadeProgress);
    context.drawImage(this.fogLayer.canvas, 0, 0);
    context.globalAlpha = 1;
    return canvas;
  }

  /** Lau nhanh đẩy nước dồn về cuối nét; dồn đủ thì thành một giọt chảy xuống. */
  private gatherWater(distance: number, x: number, y: number, radius: number) {
    if (distance < FAST_WIPE_SEGMENT_PX || distance > STROKE_JUMP_LIMIT_PX) return;
    this.wipedDistance += distance;
    if (this.wipedDistance < WIPE_DISTANCE_PER_DRIP_PX) return;
    this.wipedDistance = 0;
    const offsetX = (this.random() - 0.5) * radius;
    this.addDrip(x + offsetX, y + radius * 0.75, 3 + this.random() * 2.5, 0.1 + this.random() * 0.6);
  }

  /** Thỉnh thoảng sinh một giọt tự nhiên, chỉ ở chỗ kính còn sương. */
  private spawnAmbientDrip(seconds: number) {
    this.ambientCountdown -= seconds;
    if (this.ambientCountdown > 0) return;
    this.ambientCountdown = AMBIENT_DRIP_MIN_SECONDS + this.random() * AMBIENT_DRIP_RANGE_SECONDS;
    const x = this.random() * this.widthPx;
    const y = this.random() * this.heightPx * 0.7;
    if (maskValueAt(this.mask, x, y) < 0.2) this.addDrip(x, y, 2.5 + this.random() * 2, 0.3 + this.random() * 2);
  }

  private addDrip(x: number, y: number, radius: number, waitSeconds: number) {
    if (!this.fogReady || this.drips.length >= this.maxDrips) return;
    this.drips.push(createDrip(x, y, radius, waitSeconds, this.random));
  }

  /** Dựng lại lớp sương (khi đổi ảnh hoặc đổi kích thước): ảnh mờ + màu sương + hạt sương. */
  private rebuildFog() {
    this.needsDraw = true;
    this.fogReady = false;
    if (!this.image || this.widthPx === 0) {
      this.drips = [];
      return;
    }
    this.drawPlate(this.image);
    const layer = this.fogLayer.context;
    const { width, height } = this.fogLayer.canvas;
    layer.setTransform(1, 0, 0, 1, 0, 0);
    layer.clearRect(0, 0, width, height);
    layer.imageSmoothingEnabled = true;
    layer.imageSmoothingQuality = 'high';
    layer.drawImage(this.plate.canvas, 0, 0, width, height);
    // Hạt sương vẽ theo css px nên scale theo devicePixelRatio cho sắc nét.
    layer.setTransform(this.pixelRatio, 0, 0, this.pixelRatio, 0, 0);
    drawBeads(layer, this.beads, this.colors.shade);
    layer.setTransform(1, 0, 0, 1, 0, 0);
    this.fogReady = true;
  }

  /** Ảnh mờ ở 1/4 kích thước, phủ thêm một lớp màu sương mỏng. */
  private drawPlate(image: HTMLImageElement) {
    const plate = this.plate;
    plate.canvas.width = Math.ceil(this.widthPx / PLATE_DOWNSCALE);
    plate.canvas.height = Math.ceil(this.heightPx / PLATE_DOWNSCALE);
    if (supportsCanvasFilter(plate.context)) this.drawBlurredWithFilter(image);
    else this.drawBlurredByDownscale(image);
    plate.context.globalAlpha = FOG_TINT_OPACITY;
    plate.context.fillStyle = this.colors.tint;
    plate.context.fillRect(0, 0, plate.canvas.width, plate.canvas.height);
    plate.context.globalAlpha = 1;
  }

  private drawBlurredWithFilter(image: HTMLImageElement) {
    const { canvas, context } = this.plate;
    const rect = coverFit(image.naturalWidth, image.naturalHeight, canvas.width, canvas.height, BACKGROUND_IMAGE_SCALE);
    context.filter = `blur(${FOG_BLUR_PX / PLATE_DOWNSCALE}px)`;
    context.drawImage(image, rect.x, rect.y, rect.width, rect.height);
    context.filter = 'none';
  }

  private drawBlurredByDownscale(image: HTMLImageElement) {
    const tiny = createSurface();
    const { canvas, context } = this.plate;
    if (tiny) {
      tiny.canvas.width = Math.max(1, Math.ceil(this.widthPx / FALLBACK_DOWNSCALE));
      tiny.canvas.height = Math.max(1, Math.ceil(this.heightPx / FALLBACK_DOWNSCALE));
      const rect = coverFit(image.naturalWidth, image.naturalHeight, tiny.canvas.width, tiny.canvas.height, BACKGROUND_IMAGE_SCALE);
      tiny.context.drawImage(image, rect.x, rect.y, rect.width, rect.height);
    }
    // Phóng canvas nhỏ lên với nội suy chất lượng cao: kết quả gần như một lớp blur.
    context.imageSmoothingEnabled = true;
    context.imageSmoothingQuality = 'high';
    context.drawImage(tiny ? tiny.canvas : image, 0, 0, canvas.width, canvas.height);
  }

  private draw() {
    const context = this.context;
    context.globalCompositeOperation = 'source-over';
    context.clearRect(0, 0, this.canvas.width, this.canvas.height);
    if (!this.fogReady || !this.maskPixels) return;

    // Mặt nạ → canvas nhỏ, chỉ ghi bằng putImageData (không đọc ngược canvas nào).
    fillMaskAlpha(this.mask, this.maskPixels.data);
    this.maskSurface.context.putImageData(this.maskPixels, 0, 0);

    const fogSource = this.crossfadeProgress < 1 ? this.blendFogLayers() : this.fogLayer.canvas;
    context.globalAlpha = FOG_MAX_OPACITY;
    context.drawImage(fogSource, 0, 0);
    context.globalAlpha = 1;
    // destination-in: chỉ giữ lớp sương ở nơi mặt nạ còn alpha; phóng mặt nạ lên cho rìa mềm.
    context.imageSmoothingEnabled = true;
    context.globalCompositeOperation = 'destination-in';
    const scale = this.mask.cellSize * this.pixelRatio;
    context.drawImage(this.maskSurface.canvas, 0, 0, this.mask.columns * scale, this.mask.rows * scale);
    context.globalCompositeOperation = 'source-over';
    // Giọt nước vẽ sau cùng: nằm trên lớp sương và không bị mặt nạ cắt.
    if (this.drips.length === 0) return;
    context.setTransform(this.pixelRatio, 0, 0, this.pixelRatio, 0, 0);
    drawDrips(context, this.drips, this.colors.shade);
    context.setTransform(1, 0, 0, 1, 0, 0);
  }
}
