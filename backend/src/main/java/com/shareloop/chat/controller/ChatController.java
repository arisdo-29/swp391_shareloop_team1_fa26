package com.shareloop.chat.controller;

import com.shareloop.chat.dto.MessageResponse;
import com.shareloop.chat.dto.SendMessageRequest;
import com.shareloop.chat.service.ChatService;
import com.shareloop.common.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller xử lý gửi và nhận tin nhắn theo chu kỳ polling trong giao dịch (FR-68, BR-C03).
 */
@Tag(name = "Chat", description = "Quản lý tin nhắn trao đổi trong giao dịch")
@RestController
@RequestMapping("/api/v1/requests/{requestId}/messages")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Gửi tin nhắn mới trong cuộc trò chuyện của yêu cầu giao dịch.
     */
    @Operation(summary = "Gửi tin nhắn mới trong cuộc trò chuyện")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMessage(
            @PathVariable Long requestId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody SendMessageRequest request) {
        return chatService.sendMessage(requestId, currentUserId, request);
    }

    /**
     * Lấy danh sách tin nhắn của cuộc trò chuyện (hỗ trợ polling tin mới qua afterId).
     */
    @Operation(summary = "Lấy danh sách tin nhắn trong cuộc trò chuyện (hỗ trợ polling)")
    @GetMapping
    public List<MessageResponse> getMessages(
            @PathVariable Long requestId,
            @CurrentUserId Long currentUserId,
            @RequestParam(required = false) Long afterId) {
        return chatService.getMessages(requestId, currentUserId, afterId);
    }
}
