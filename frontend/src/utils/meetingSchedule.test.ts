import { describe, expect, it } from 'vitest';
import {
  canConfirmMeetingSchedule,
  minimumMeetingTimeValue,
  parseLocalMeetingTime,
  scheduleApiErrorMessage,
  validateLocalMeetingTime,
  validateMeetingPlace,
} from './meetingSchedule';

const now = new Date(2026, 9, 22, 10, 30, 0, 0);

describe('meetingSchedule', () => {
  it('từ chối ngày hôm qua, giờ đã qua và giờ bằng hiện tại', () => {
    expect(validateLocalMeetingTime('2026-10-21T15:00', now)).toMatch(/sau thời điểm hiện tại/);
    expect(validateLocalMeetingTime('2026-10-22T10:29', now)).toMatch(/sau thời điểm hiện tại/);
    expect(validateLocalMeetingTime('2026-10-22T10:30', now)).toMatch(/sau thời điểm hiện tại/);
  });

  it('chấp nhận giờ tương lai trong hôm nay và ngày mai bằng so sánh Date', () => {
    expect(validateLocalMeetingTime('2026-10-22T10:31', now)).toBeNull();
    expect(validateLocalMeetingTime('2026-10-23T08:00', now)).toBeNull();
    expect(parseLocalMeetingTime('2026-02-30T10:00')).toBeNull();
  });

  it('đặt min của datetime-local ở phút kế tiếp', () => {
    expect(minimumMeetingTimeValue(new Date(2026, 9, 22, 10, 30, 45))).toBe('2026-10-22T10:31');
  });

  it('khóa xác nhận khi thiếu địa điểm, chưa cam kết hoặc có thay đổi chưa lưu', () => {
    const validInput = {
      meetingPlace: 'Cổng trường FPT',
      localMeetingTime: '2026-10-22T10:31',
      storedMeetingTime: new Date(2026, 9, 22, 10, 31).toISOString(),
      agreed: true,
      hasUnsavedChanges: false,
      now,
    };

    expect(canConfirmMeetingSchedule(validInput)).toBe(true);
    expect(canConfirmMeetingSchedule({ ...validInput, meetingPlace: '   ' })).toBe(false);
    expect(canConfirmMeetingSchedule({ ...validInput, agreed: false })).toBe(false);
    expect(canConfirmMeetingSchedule({ ...validInput, hasUnsavedChanges: true })).toBe(false);
    expect(validateMeetingPlace('x'.repeat(256))).toMatch(/255/);
  });

  it('tự hết hợp lệ khi thời gian thực vượt qua lịch đã chọn', () => {
    const selectedTime = '2026-10-22T10:31';
    const input = {
      meetingPlace: 'Cổng trường FPT',
      localMeetingTime: selectedTime,
      storedMeetingTime: new Date(2026, 9, 22, 10, 31).toISOString(),
      agreed: true,
      hasUnsavedChanges: false,
    };

    expect(canConfirmMeetingSchedule({ ...input, now })).toBe(true);
    expect(canConfirmMeetingSchedule({ ...input, now: new Date(2026, 9, 22, 10, 31) })).toBe(false);
  });

  it('hiển thị thông báo rõ ràng khi Backend từ chối validation thời gian', () => {
    expect(scheduleApiErrorMessage(
      { code: 'REQUEST_SCHEDULE_PAST_TIME', message: 'technical', status: 400 },
      'Không lưu được lịch.',
    )).toMatch(/sau thời điểm hiện tại/);
    expect(scheduleApiErrorMessage(
      { code: 'VALIDATION_FAILED', message: 'technical', status: 400 },
      'Không lưu được lịch.',
    )).toMatch(/chưa hợp lệ/);
  });
});
