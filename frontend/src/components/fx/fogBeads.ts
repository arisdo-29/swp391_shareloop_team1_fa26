// Hạt sương li ti bám trên kính. Vẽ một lần vào lớp sương dựng sẵn,
// nên lau tới đâu hạt biến mất tới đó, sương phủ lại thì hạt hiện lại.

/** Mật độ hạt: một hạt cho mỗi chừng này css px² diện tích hero. */
export const BEAD_AREA_PER_BEAD_PX = 2600;
/** Trần số hạt, để màn hình rất lớn không phải vẽ quá nhiều. */
export const MAX_BEADS = 520;
/** Hạt giống cố định: đổi slide thì hạt vẫn ở nguyên chỗ, không nhảy lung tung. */
export const BEAD_SEED = 20261117;
const BEAD_MIN_RADIUS_PX = 0.8;
const BEAD_MAX_RADIUS_PX = 4.2;
// Cung tối ở mép dưới hạt: từ góc 0.15π tới 0.85π (trục y của canvas hướng xuống).
const RIM_START_ANGLE = Math.PI * 0.15;
const RIM_END_ANGLE = Math.PI * 0.85;

export type Bead = { x: number; y: number; radius: number };

/** Bộ sinh số ngẫu nhiên có hạt giống (thuật toán mulberry32): cùng seed cho cùng một dãy số. */
export function seededRandom(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 1831565813) >>> 0;
    let value = state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

/** Rải hạt ngẫu nhiên trên vùng widthPx x heightPx; đa số hạt nhỏ, thỉnh thoảng có hạt to. */
export function generateBeads(widthPx: number, heightPx: number, random: () => number): Bead[] {
  const count = Math.min(MAX_BEADS, Math.round((widthPx * heightPx) / BEAD_AREA_PER_BEAD_PX));
  const beads: Bead[] = [];
  for (let index = 0; index < count; index++) {
    const radius = BEAD_MIN_RADIUS_PX + (BEAD_MAX_RADIUS_PX - BEAD_MIN_RADIUS_PX) * random() ** 2.5;
    beads.push({ x: random() * widthPx, y: random() * heightPx, radius });
  }
  return beads;
}

/**
 * Vẽ hạt theo ba lượt: thân mờ, viền tối mỏng phía dưới, điểm sáng phía trên.
 * Mỗi lượt gom mọi hạt vào một path rồi vẽ một lần, nên hàng trăm hạt vẫn nhanh.
 */
export function drawBeads(context: CanvasRenderingContext2D, beads: Bead[], shadeColor: string) {
  context.save();
  fillBeadBodies(context, beads);
  strokeBeadRims(context, beads, shadeColor);
  fillBeadGlints(context, beads);
  context.restore();
}

function fillBeadBodies(context: CanvasRenderingContext2D, beads: Bead[]) {
  context.fillStyle = 'white';
  context.globalAlpha = 0.3;
  context.beginPath();
  for (const bead of beads) {
    context.moveTo(bead.x + bead.radius, bead.y);
    context.arc(bead.x, bead.y, bead.radius, 0, Math.PI * 2);
  }
  context.fill();
}

function strokeBeadRims(context: CanvasRenderingContext2D, beads: Bead[], shadeColor: string) {
  context.strokeStyle = shadeColor;
  context.globalAlpha = 0.5;
  context.lineWidth = 0.8;
  context.beginPath();
  for (const bead of beads) {
    const rimRadius = bead.radius * 0.85;
    context.moveTo(bead.x + rimRadius * Math.cos(RIM_START_ANGLE), bead.y + rimRadius * Math.sin(RIM_START_ANGLE));
    context.arc(bead.x, bead.y, rimRadius, RIM_START_ANGLE, RIM_END_ANGLE);
  }
  context.stroke();
}

function fillBeadGlints(context: CanvasRenderingContext2D, beads: Bead[]) {
  context.fillStyle = 'white';
  context.globalAlpha = 0.95;
  context.beginPath();
  for (const bead of beads) {
    const glintRadius = Math.max(0.35, bead.radius * 0.32);
    const glintX = bead.x - bead.radius * 0.3;
    const glintY = bead.y - bead.radius * 0.35;
    context.moveTo(glintX + glintRadius, glintY);
    context.arc(glintX, glintY, glintRadius, 0, Math.PI * 2);
  }
  context.fill();
}
