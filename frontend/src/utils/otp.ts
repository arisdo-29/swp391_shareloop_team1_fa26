export interface OtpEditResult {
  value: string[];
  focusIndex: number;
}

export function createOtpValue(length = 6): string[] {
  return Array.from({ length }, () => '');
}

function normalizeOtpValue(value: readonly string[]): string[] {
  return value.map((digit) => digit.replace(/\D/g, '').slice(0, 1));
}

export function applyOtpChange(
  value: readonly string[],
  index: number,
  rawInput: string,
): OtpEditResult {
  const next = normalizeOtpValue(value);
  if (rawInput === '') {
    next[index] = '';
    return { value: next, focusIndex: index };
  }

  const digits = rawInput.replace(/\D/g, '');
  if (!digits) return { value: next, focusIndex: index };

  next[index] = digits.at(-1) ?? '';
  return {
    value: next,
    focusIndex: Math.min(index + 1, next.length - 1),
  };
}

export function applyOtpBackspace(value: readonly string[], index: number): OtpEditResult {
  const next = normalizeOtpValue(value);
  if (next[index]) {
    next[index] = '';
    return { value: next, focusIndex: index };
  }
  return { value: next, focusIndex: Math.max(0, index - 1) };
}

export function applyOtpPaste(
  value: readonly string[],
  index: number,
  clipboardText: string,
): OtpEditResult {
  const next = normalizeOtpValue(value);
  const digits = clipboardText.replace(/\D/g, '');
  if (!digits) return { value: next, focusIndex: index };

  const startIndex = digits.length >= next.length ? 0 : index;
  const accepted = digits.slice(0, next.length - startIndex);
  if (startIndex === 0 && accepted.length === next.length) next.fill('');
  for (let offset = 0; offset < accepted.length; offset += 1) {
    next[startIndex + offset] = accepted[offset];
  }
  return {
    value: next,
    focusIndex: Math.min(startIndex + accepted.length, next.length - 1),
  };
}
