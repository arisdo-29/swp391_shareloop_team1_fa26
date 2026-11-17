// Giọt nước chảy trên kính: thỉnh thoảng một giọt chảy xuống từ vùng còn sương,
// hoặc nước bị gạt dồn lại ở cuối nét lau nhanh. Giọt chảy để lại vệt sạch phía sau.
import type { Point } from './fogEngine';

/** Số giọt chảy cùng lúc tối đa trên máy tính. */
export const MAX_DRIPS_DESKTOP = 12;
/** Số giọt tối đa trên màn hình hẹp hoặc máy yếu. */
export const MAX_DRIPS_LIGHT = 5;
/** Màn hình hẹp hơn mức này (css px) coi là máy nhẹ. */
const LIGHT_SCREEN_WIDTH_PX = 700;
/** Máy có từ chừng này nhân CPU trở xuống coi là máy yếu. */
const LIGHT_DEVICE_CORES = 4;
/** Giọt tự nhiên: cứ khoảng 0,9 đến 2,5 giây thử sinh một giọt. */
export const AMBIENT_DRIP_MIN_SECONDS = 0.9;
export const AMBIENT_DRIP_RANGE_SECONDS = 1.6;
/** Lau nhanh dồn đủ quãng đường này (css px) thì nước gom lại thành một giọt ở cuối nét. */
export const WIPE_DISTANCE_PER_DRIP_PX = 160;
/** Một lần di chuột dài từ chừng này trở lên mới tính là "lau nhanh". */
export const FAST_WIPE_SEGMENT_PX = 12;
/** Gia tốc rơi (css px/giây²). */
const GRAVITY_PX_PER_SECOND_SQUARED = 700;
/** Vệt sạch sau giọt rộng hơn giọt một chút, giữ sạch ngắn hơn vết lau tay. */
const TRAIL_RADIUS_FACTOR = 1.25;
const TRAIL_STRENGTH = 1.25;

export type Drip = {
  x: number;
  y: number;
  radius: number;
  speed: number;
  waitSeconds: number;
  stickSeconds: number;
  seed: number;
};

/** Hàm lau dùng cho vệt sạch của giọt (engine truyền vào, ghi thẳng vào mặt nạ). */
export type ClearTrail = (from: Point, to: Point, radius: number, strength: number) => void;

/** Màn hình hẹp hoặc máy ít nhân thì giảm số giọt để không giật. */
export function maxDripsFor(screenWidthPx: number, cpuCores: number | undefined): number {
  const lightScreen = screenWidthPx < LIGHT_SCREEN_WIDTH_PX;
  const lightDevice = cpuCores !== undefined && cpuCores <= LIGHT_DEVICE_CORES;
  return lightScreen || lightDevice ? MAX_DRIPS_LIGHT : MAX_DRIPS_DESKTOP;
}

/** Tạo một giọt đứng yên waitSeconds giây rồi mới bắt đầu chảy. */
export function createDrip(x: number, y: number, radius: number, waitSeconds: number, random: () => number): Drip {
  return { x, y, radius, speed: 0, waitSeconds, stickSeconds: 0, seed: random() * 100 };
}

/** Cho các giọt chảy thêm `seconds` giây, ghi vệt sạch, trả về những giọt còn trên kính. */
export function updateDrips(
  drips: Drip[],
  seconds: number,
  heightPx: number,
  random: () => number,
  clearTrail: ClearTrail,
): Drip[] {
  const remaining: Drip[] = [];
  for (const drip of drips) {
    if (drip.waitSeconds > 0) {
      drip.waitSeconds -= seconds;
      remaining.push(drip);
      continue;
    }
    const previous = { x: drip.x, y: drip.y };
    moveDrip(drip, seconds, random);
    clearTrail(previous, { x: drip.x, y: drip.y }, drip.radius * TRAIL_RADIUS_FACTOR, TRAIL_STRENGTH);
    // Giữ giọt thêm 30px dưới mép để nó trôi hẳn ra ngoài rồi mới bỏ.
    if (drip.y < heightPx + 30) remaining.push(drip);
  }
  return remaining;
}

/** Giọt rơi nhanh dần nhưng thỉnh thoảng khựng lại (vướng hạt sương), đường đi hơi lượn. */
function moveDrip(drip: Drip, seconds: number, random: () => number) {
  if (drip.stickSeconds > 0) {
    drip.stickSeconds -= seconds;
    drip.speed *= Math.exp(-seconds * 20);
  } else {
    const maxSpeed = 35 + drip.radius * 32;
    drip.speed = Math.min(maxSpeed, drip.speed + GRAVITY_PX_PER_SECOND_SQUARED * seconds);
    if (random() < seconds * 1.1) drip.stickSeconds = 0.06 + random() * 0.45;
  }
  drip.y += drip.speed * seconds;
  drip.x += Math.sin(drip.y * 0.045 + drip.seed) * drip.speed * seconds * 0.12;
}

/** Vẽ giọt (css px; engine đã scale theo devicePixelRatio): đuôi mảnh, thân, viền tối, điểm sáng. */
export function drawDrips(context: CanvasRenderingContext2D, drips: Drip[], shadeColor: string) {
  context.save();
  for (const drip of drips) {
    // Chảy càng nhanh giọt càng dài ra.
    const stretch = 1 + Math.min(0.6, drip.speed / 220);
    const radiusX = drip.radius;
    const radiusY = drip.radius * stretch;
    fillEllipse(context, drip.x, drip.y - radiusY * 1.5, radiusX * 0.45, radiusY * 1.1, 'white', 0.16);
    fillEllipse(context, drip.x, drip.y, radiusX, radiusY, 'white', 0.24);
    context.globalAlpha = 0.45;
    context.strokeStyle = shadeColor;
    context.lineWidth = Math.max(0.8, radiusX * 0.28);
    context.beginPath();
    context.ellipse(drip.x, drip.y, radiusX * 0.9, radiusY * 0.9, 0, Math.PI * 0.1, Math.PI * 0.9);
    context.stroke();
    fillEllipse(context, drip.x - radiusX * 0.35, drip.y - radiusY * 0.4, radiusX * 0.3, radiusY * 0.25, 'white', 0.85);
  }
  context.restore();
}

function fillEllipse(
  context: CanvasRenderingContext2D,
  x: number,
  y: number,
  radiusX: number,
  radiusY: number,
  color: string,
  alpha: number,
) {
  context.globalAlpha = alpha;
  context.fillStyle = color;
  context.beginPath();
  context.ellipse(x, y, radiusX, radiusY, 0, 0, Math.PI * 2);
  context.fill();
}
