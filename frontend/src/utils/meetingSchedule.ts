import type { ApiError } from '../services/http';

const LOCAL_DATE_TIME_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/;

export function parseLocalMeetingTime(value: string): Date | null {
  const match = LOCAL_DATE_TIME_PATTERN.exec(value.trim());
  if (!match) return null;

  const [, yearValue, monthValue, dayValue, hourValue, minuteValue, secondValue = '0'] = match;
  const year = Number(yearValue);
  const month = Number(monthValue) - 1;
  const day = Number(dayValue);
  const hour = Number(hourValue);
  const minute = Number(minuteValue);
  const second = Number(secondValue);
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

export function toLocalMeetingTimeValue(value: string | Date): string {
  const date = typeof value === 'string' ? new Date(value) : value;
  if (Number.isNaN(date.getTime())) return '';
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return localDate.toISOString().slice(0, 16);
}

export function minimumMeetingTimeValue(now: Date): string {
  const minimum = new Date(now);
  minimum.setSeconds(0, 0);
  minimum.setMinutes(minimum.getMinutes() + 1);
  return toLocalMeetingTimeValue(minimum);
}

export function validateMeetingPlace(value: string): string | null {
  const place = value.trim();
  if (!place) return 'Vui lòng nhập địa điểm giao nhận.';
  if (place.length > 255) return 'Địa điểm giao nhận không được vượt quá 255 ký tự.';
  return null;
}

export function validateLocalMeetingTime(value: string, now: Date): string | null {
  if (!value.trim()) return 'Vui lòng chọn thời gian giao nhận.';
  const meetingTime = parseLocalMeetingTime(value);
  if (!meetingTime) return 'Thời gian giao nhận không hợp lệ.';
  if (meetingTime.getTime() <= now.getTime()) {
    return 'Thời gian giao nhận phải sau thời điểm hiện tại.';
  }
  return null;
}

export function validateStoredMeetingTime(value: string | undefined, now: Date): string | null {
  if (!value) return 'Vui lòng chọn thời gian giao nhận.';
  const meetingTime = new Date(value);
  if (Number.isNaN(meetingTime.getTime())) return 'Thời gian giao nhận không hợp lệ.';
  if (meetingTime.getTime() <= now.getTime()) {
    return 'Thời gian giao nhận đã qua. Vui lòng chọn và lưu lại một thời gian mới.';
  }
  return null;
}

export function canConfirmMeetingSchedule({
  meetingPlace,
  localMeetingTime,
  storedMeetingTime,
  agreed,
  hasUnsavedChanges,
  now,
}: {
  meetingPlace: string;
  localMeetingTime: string;
  storedMeetingTime?: string;
  agreed: boolean;
  hasUnsavedChanges: boolean;
  now: Date;
}): boolean {
  return Boolean(
    agreed &&
    !hasUnsavedChanges &&
    !validateMeetingPlace(meetingPlace) &&
    !validateLocalMeetingTime(localMeetingTime, now) &&
    !validateStoredMeetingTime(storedMeetingTime, now),
  );
}

export function scheduleApiErrorMessage(error: unknown, fallback: string): string {
  const apiError = error as Partial<ApiError>;
  if (apiError.code === 'REQUEST_SCHEDULE_PAST_TIME') {
    return 'Thời gian giao nhận phải sau thời điểm hiện tại. Vui lòng chọn lại.';
  }
  if (apiError.code === 'VALIDATION_FAILED') {
    return 'Thông tin lịch giao nhận chưa hợp lệ. Vui lòng kiểm tra địa điểm và thời gian.';
  }
  return apiError.message || fallback;
}
