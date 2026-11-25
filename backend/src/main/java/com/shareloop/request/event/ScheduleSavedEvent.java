package com.shareloop.request.event;

/** Phát ra khi một bên lưu lịch giao nhận của giao dịch. */
public record ScheduleSavedEvent(long requestId, long actorId) {}
