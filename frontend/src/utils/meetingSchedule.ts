import type { ApiError } from '../services/http';
import { DEFAULT_PUBLIC_CONFIG, type PublicConfig } from '../services/publicConfigApi';

const LOCAL_DATE_TIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/;

const VIETNAM_OFFSET_MINUTES = 7 * 60;
const MINUTE_MS = 60_000;

export function parseLocalMeetingTime(value: string, vietnamTime = false): Date | null {
  const match = LOCAL_DATE_TIME_PATTERN.exec(value.trim());
  if (!match) return null;

  const [, yearValue, monthValue, dayValue, hourValue, minuteValue, secondValue = '0'] = match;
  const year = Number(yearValue);
  const month = Number(monthValue) - 1;
  const day = Number(dayValue);
  const hour = Number(hourValue);
  const minute = Number(minuteValue);
  const second = Number(secondValue);
  if (vietnamTime) {
    const date = new Date(Date.UTC(year, month, day, hour, minute, second));
    if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month || date.getUTCDate() !== day ||
        date.getUTCHours() !== hour || date.getUTCMinutes() !== minute || date.getUTCSeconds() !== second) return null;
    return new Date(date.getTime() - VIETNAM_OFFSET_MINUTES * MINUTE_MS);
  }
  const date = new Date(year, month, day, hour, minute, second, 0);

  if (
    date.getFullYear() !== year ||
    date.getMonth() !== month ||
    date.getDate() !== day ||
    date.getHours() !== hour ||
    date.getMinutes() !== minute ||
    date.getSeconds() !== second
  ) return null;

  return date;
}

export function toLocalMeetingTimeValue(value: string | Date, vietnamTime = false): string {
  const date = typeof value === 'string' ? new Date(value) : value;
  if (Number.isNaN(date.getTime())) return '';
  const offset = vietnamTime ? VIETNAM_OFFSET_MINUTES : -date.getTimezoneOffset();
  const localDate = new Date(date.getTime() + offset * MINUTE_MS);
  return localDate.toISOString().slice(0, 16);
}

export function minimumMeetingTimeValue(now: Date, vietnamTime = false): string {
  const minimum = new Date(now);
  minimum.setSeconds(0, 0);
  minimum.setMinutes(minimum.getMinutes() + 1);
  return toLocalMeetingTimeValue(minimum, vietnamTime);
}

// Giờ hẹn xa nhất theo cấu hình meeting_max_days_ahead (backend vẫn kiểm lại).
export function maximumMeetingTimeValue(now: Date, maxDays: number, vietnamTime = false): string {
  const maximum = new Date(now.getTime() + maxDays * 86_400_000);
  return toLocalMeetingTimeValue(maximum, vietnamTime);
}

export function validateMeetingPlace(value: string): string | null {
  const place = value.trim();
  if (!place) return 'Vui lòng nhập địa điểm giao nhận.';
  if (place.length > 255) return 'Địa điểm tối đa 255 ký tự.';
  return null;
}

export function validateLocalMeetingTime(
  value: string, now: Date, maxDays?: number, vietnamTime = false,
): string | null {
  if (!value.trim()) return 'Vui lòng chọn thời gian giao nhận.';
  const meetingTime = parseLocalMeetingTime(value, vietnamTime);
  if (!meetingTime) return 'Thời gian giao nhận không hợp lệ.';
  if (meetingTime.getTime() <= now.getTime()) {
    return 'Thời gian giao nhận phải sau thời điểm hiện tại.';
  }
  if (maxDays !== undefined && meetingTime.getTime() > now.getTime() + maxDays * 86_400_000) {
    return `Chỉ hẹn trong vòng ${maxDays} ngày tới.`;
  }
  return null;
}

export function exchangeShippingWindowMinutes(config: PublicConfig): number {
  return config.exchange_shipping_window_minutes ?? DEFAULT_PUBLIC_CONFIG.exchange_shipping_window_minutes;
}

export function validateExchangeShippingMeeting(meeting: Date | null, now: Date, config: PublicConfig): string | null {
  if (!meeting || Number.isNaN(meeting.getTime())) return null;
  const lead = config.exchange_shipping_lead_minutes ?? DEFAULT_PUBLIC_CONFIG.exchange_shipping_lead_minutes;
  if (meeting.getTime() < now.getTime() + lead * MINUTE_MS) {
    return `Giờ hẹn hơi gấp rồi. Bạn chọn giờ cách hiện tại ít nhất ${lead === 60 ? '1 tiếng' : `${lead} phút`} để cả hai kịp chuẩn bị nhé.`;
  }
  const window = exchangeShippingWindowMinutes(config);
  const local = new Date(meeting.getTime() + VIETNAM_OFFSET_MINUTES * MINUTE_MS);
  const minutes = local.getUTCHours() * 60 + local.getUTCMinutes() + local.getUTCSeconds() / 60;
  const minute = (key: string, extra = 0) => Math.min((config[key] ?? DEFAULT_PUBLIC_CONFIG[key]) + extra, 1439);
  const morning = [minute('exchange_shipping_morning_start', window), minute('exchange_shipping_morning_end')];
  const afternoon = [minute('exchange_shipping_afternoon_start', window), minute('exchange_shipping_afternoon_end')];
  if ([morning, afternoon].some(([start, end]) => minutes >= start && minutes <= end)) return null;
  const time = (value: number) => `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}`;
  return `Bạn chọn giúp một giờ trong khoảng ${time(morning[0])} đến ${time(morning[1])} hoặc ${time(afternoon[0])} đến ${time(afternoon[1])} nhé.`;
}

export function validateStoredMeetingTime(value: string | undefined, now: Date): string | null {
  if (!value) return 'Vui lòng chọn thời gian giao nhận.';
  const meetingTime = new Date(value);
  if (Number.isNaN(meetingTime.getTime())) return 'Thời gian giao nhận không hợp lệ.';
  if (meetingTime.getTime() <= now.getTime()) {
    return 'Thời gian giao nhận đã qua. Vui lòng chọn và gửi lại thời gian mới.';
  }
  return null;
}

export function hasConfirmedMeetingSchedule(scheduleOkAt?: string, waiverAt?: string): boolean {
  return Boolean(scheduleOkAt && waiverAt);
}

export function scheduleApiErrorMessage(error: unknown, fallback: string): string {
  const apiError = error as Partial<ApiError>;
  if (apiError.code === 'REQUEST_SCHEDULE_PAST_TIME') {
    return 'Thời gian giao nhận phải sau thời điểm hiện tại. Vui lòng chọn lại.';
  }
  if (apiError.code === 'REQUEST_SCHEDULE_TOO_FAR') {
    return apiError.message ? `${apiError.message}.` : 'Giờ hẹn quá xa. Vui lòng chọn lại.';
  }
  if (apiError.code === 'VALIDATION_FAILED') {
    return 'Thông tin lịch giao nhận chưa hợp lệ. Vui lòng kiểm tra địa điểm và thời gian.';
  }
  return apiError.message || fallback;
}
