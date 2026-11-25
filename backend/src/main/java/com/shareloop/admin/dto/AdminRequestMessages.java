package com.shareloop.admin.dto;

import java.util.List;

/** Tin nhắn của một giao dịch cho Admin, cũ trước. */
public record AdminRequestMessages(List<AdminChatMessage> items) {}
