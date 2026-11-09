package com.shareloop.chat.controller;

import com.shareloop.chat.dto.MessageResponse;
import com.shareloop.chat.dto.ProposeOfferChangeRequest;
import com.shareloop.chat.dto.SendMessageRequest;
import com.shareloop.chat.service.ChatService;
import com.shareloop.common.security.CurrentUserId;
import com.shareloop.item.dto.ItemCardDto;
import com.shareloop.request.dto.RequestResponse;
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
 * Controller xử lý gửi, nhận tin nhắn và lấy thẻ sản phẩm ghim trong giao dịch (CH-01, CH-06, GN-05).
 */
@Tag(name = "Chat", description = "Quản lý tin nhắn trao đổi trong giao dịch")
@RestController
@RequestMapping("/api/v1/requests/{requestId}")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Gửi tin nhắn mới trong cuộc trò chuyện của yêu cầu giao dịch.
     */
    @Operation(summary = "Gửi tin nhắn mới trong cuộc trò chuyện")
    @PostMapping("/messages")
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
    @GetMapping("/messages")
    public List<MessageResponse> getMessages(
            @PathVariable Long requestId,
            @CurrentUserId Long currentUserId,
            @RequestParam(required = false) Long afterId) {
        return chatService.getMessages(requestId, currentUserId, afterId);
    }

    /**
     * Lấy danh sách thẻ sản phẩm ghim ở đầu khung chat (CH-06).
     */
    @Operation(summary = "Lấy danh sách thẻ sản phẩm ghim ở đầu khung chat")
    @GetMapping({"/pinned-items", "/messages/pinned-items"})
    public List<ItemCardDto> getPinnedItems(@PathVariable Long requestId, @CurrentUserId Long currentUserId) {
        return chatService.getPinnedItems(requestId, currentUserId);
    }

    /**
     * Người nhận đề nghị đưa một món của mình vào đổi (giao dịch Cho → Trao đổi).
     */
    @Operation(summary = "Người nhận đề nghị đưa món của mình vào trao đổi")
    @PostMapping("/offer-changes")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse proposeOfferChange(
            @PathVariable Long requestId,
            @CurrentUserId Long currentUserId,
            @Valid @RequestBody ProposeOfferChangeRequest request) {
        return chatService.proposeOfferChange(requestId, currentUserId, request);
    }

    /**
     * Chủ bài đồng ý đề nghị đổi món; trả về giao dịch sau khi đã chuyển sang Trao đổi.
     */
    @Operation(summary = "Chủ bài đồng ý đề nghị đổi món, giao dịch chuyển sang Trao đổi")
    @PostMapping("/offer-changes/{messageId}/accept")
    public RequestResponse acceptOfferChange(
            @PathVariable Long requestId, @PathVariable Long messageId, @CurrentUserId Long currentUserId) {
        return chatService.acceptOfferChange(requestId, messageId, currentUserId);
    }

    /**
     * Chủ bài từ chối đề nghị đổi món.
     */
    @Operation(summary = "Chủ bài từ chối đề nghị đổi món")
    @PostMapping("/offer-changes/{messageId}/decline")
    public MessageResponse declineOfferChange(
            @PathVariable Long requestId, @PathVariable Long messageId, @CurrentUserId Long currentUserId) {
        return chatService.declineOfferChange(requestId, messageId, currentUserId);
    }
}
