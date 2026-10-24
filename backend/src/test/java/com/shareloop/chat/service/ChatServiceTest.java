package com.shareloop.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.chat.ChatErrorCode;
import com.shareloop.chat.dto.MessageResponse;
import com.shareloop.chat.dto.SendMessageRequest;
import com.shareloop.chat.entity.Message;
import com.shareloop.chat.entity.MessageKind;
import com.shareloop.chat.repository.MessageRepository;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.item.ItemErrorCode;
import com.shareloop.item.dto.ItemCardDto;
import com.shareloop.item.entity.ItemStatus;
import com.shareloop.item.entity.OfferType;
import com.shareloop.item.service.ItemQueryService;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.RequestChatContext;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.service.RequestService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    MessageRepository messageRepository;

    @Mock
    RequestService requestService;

    @Mock
    ChatContactFilter chatContactFilter;

    @Mock
    com.shareloop.media.service.MediaService mediaService;

    @Mock
    ItemQueryService itemQueryService;

    @Mock
    ChatViolationRecorder chatViolationRecorder;

    @Mock
    ChatAiModerationService chatAiModerationService;

    @InjectMocks
    ChatService chatService;

    private static final long REQUEST_ID = 100L;
    private static final long RECEIVER_ID = 10L;
    private static final long DONOR_ID = 20L;
    private static final long STRANGER_ID = 99L;

    @Nested
    @DisplayName("Gửi tin nhắn (sendMessage)")
    class SendMessageTest {

        @Test
        void tierTwoBlocksAmbiguousContactAndRecordsConfidence() {
            when(requestService.getChatContext(REQUEST_ID))
                    .thenReturn(new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID));
            when(chatAiModerationService.evaluate(REQUEST_ID, "ib mình nhé"))
                    .thenReturn(Optional.of(new ChatAiModerationService.Decision(
                            true, new BigDecimal("0.82"), "liên hệ qua mạng xã hội")));

            assertThatThrownBy(() ->
                            chatService.sendMessage(REQUEST_ID, RECEIVER_ID, new SendMessageRequest("ib mình nhé")))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ChatErrorCode.CHAT_MESSAGE_BLOCKED));

            verify(chatViolationRecorder)
                    .recordViolationAndAlertAdmin(
                            REQUEST_ID,
                            RECEIVER_ID,
                            "ib mình nhé",
                            "liên hệ qua mạng xã hội",
                            2,
                            new BigDecimal("0.82"));
            verify(messageRepository, never()).save(any(Message.class));
        }

        @Test
        void tierTwoPassPersistsConfidence() {
            when(requestService.getChatContext(REQUEST_ID))
                    .thenReturn(new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID));
            when(chatAiModerationService.evaluate(REQUEST_ID, "ib mình nhé"))
                    .thenReturn(Optional.of(
                            new ChatAiModerationService.Decision(false, new BigDecimal("0.40"), "không có liên hệ")));
            when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

            chatService.sendMessage(REQUEST_ID, RECEIVER_ID, new SendMessageRequest("ib mình nhé"));

            org.mockito.ArgumentCaptor<Message> saved = org.mockito.ArgumentCaptor.forClass(Message.class);
            verify(messageRepository).save(saved.capture());
            assertThat(saved.getValue().getModerationTier()).isEqualTo((short) 2);
            assertThat(saved.getValue().getAiConfidence()).isEqualByComparingTo("0.40");
        }

        @Test
        @DisplayName("Gửi tin nhắn thành công khi Request ở trạng thái RESERVED và người gửi là receiver")
        void sendMessage_success_byReceiver() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            Message savedMessage = new Message(REQUEST_ID, RECEIVER_ID, "Chào bạn, khi nào giao dịch được?");
            when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

            SendMessageRequest req = new SendMessageRequest("Chào bạn, khi nào giao dịch được?");
            MessageResponse response = chatService.sendMessage(REQUEST_ID, RECEIVER_ID, req);

            assertThat(response).isNotNull();
            assertThat(response.requestId()).isEqualTo(REQUEST_ID);
            assertThat(response.senderId()).isEqualTo(RECEIVER_ID);
            assertThat(response.content()).isEqualTo("Chào bạn, khi nào giao dịch được?");
            assertThat(response.kind()).isEqualTo(MessageKind.TEXT.name());
            verify(messageRepository).save(any(Message.class));
        }

        @Test
        @DisplayName("Gửi tin nhắn thành công khi người gửi là donor")
        void sendMessage_success_byDonor() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            Message savedMessage = new Message(REQUEST_ID, DONOR_ID, "Chiều nay nhé!");
            when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

            SendMessageRequest req = new SendMessageRequest("Chiều nay nhé!");
            MessageResponse response = chatService.sendMessage(REQUEST_ID, DONOR_ID, req);

            assertThat(response).isNotNull();
            assertThat(response.senderId()).isEqualTo(DONOR_ID);
        }

        @Test
        @DisplayName("Ném lỗi CHAT_MESSAGE_EMPTY khi nội dung tin nhắn rỗng hoặc chỉ có khoảng trắng")
        void sendMessage_throwsWhenEmpty() {
            SendMessageRequest req = new SendMessageRequest("   ");

            assertThatThrownBy(() -> chatService.sendMessage(REQUEST_ID, RECEIVER_ID, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ChatErrorCode.CHAT_MESSAGE_EMPTY));
        }

        @Test
        @DisplayName("Ném lỗi CHAT_REQUEST_NOT_FOUND khi Request không tồn tại")
        void sendMessage_throwsWhenRequestNotFound() {
            when(requestService.getChatContext(REQUEST_ID))
                    .thenThrow(new BusinessException(RequestErrorCode.REQUEST_NOT_FOUND));

            SendMessageRequest req = new SendMessageRequest("Alo");

            assertThatThrownBy(() -> chatService.sendMessage(REQUEST_ID, RECEIVER_ID, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ChatErrorCode.CHAT_REQUEST_NOT_FOUND));
        }

        @Test
        @DisplayName("Ném lỗi CHAT_REQUEST_NOT_RESERVED khi Request chưa được chọn (trạng thái PENDING)")
        void sendMessage_throwsWhenNotReserved() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.PENDING, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            SendMessageRequest req = new SendMessageRequest("Alo");

            assertThatThrownBy(() -> chatService.sendMessage(REQUEST_ID, RECEIVER_ID, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ChatErrorCode.CHAT_REQUEST_NOT_RESERVED));
        }

        @Test
        @DisplayName("Ném lỗi CHAT_FORBIDDEN khi người gửi không phải là bên liên quan trong giao dịch")
        void sendMessage_throwsWhenForbidden() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            SendMessageRequest req = new SendMessageRequest("Xin chào");

            assertThatThrownBy(() -> chatService.sendMessage(REQUEST_ID, STRANGER_ID, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e ->
                            assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ChatErrorCode.CHAT_FORBIDDEN));
        }

        @Test
        @DisplayName("Ném lỗi CHAT_MESSAGE_BLOCKED khi tin nhắn chứa thông tin liên hệ")
        void sendMessage_throwsWhenContactInfoDetected() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            org.mockito.Mockito.doThrow(new BusinessException(
                            ChatErrorCode.CHAT_MESSAGE_BLOCKED, "Tin nhắn bị chặn do chứa thông tin liên hệ"))
                    .when(chatContactFilter)
                    .validateMessage("Gọi không chín một hai ba bốn năm sáu bảy tám");

            SendMessageRequest req = new SendMessageRequest("Gọi không chín một hai ba bốn năm sáu bảy tám");

            assertThatThrownBy(() -> chatService.sendMessage(REQUEST_ID, RECEIVER_ID, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ChatErrorCode.CHAT_MESSAGE_BLOCKED));
        }

        @Test
        @DisplayName("Gửi ảnh thành công khi Request ở trạng thái LOGISTICS_CONFIRMED (FR-75, BR-C05)")
        void sendMessage_image_success_whenLogisticsConfirmed() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.LOGISTICS_CONFIRMED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            long mediaId = 42L;
            Message savedMessage = new Message(REQUEST_ID, RECEIVER_ID, MessageKind.IMAGE, "Ảnh gói đồ", mediaId);
            when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

            SendMessageRequest req = new SendMessageRequest("Ảnh gói đồ", mediaId);
            MessageResponse response = chatService.sendMessage(REQUEST_ID, RECEIVER_ID, req);

            assertThat(response).isNotNull();
            assertThat(response.kind()).isEqualTo(MessageKind.IMAGE.name());
            assertThat(response.mediaId()).isEqualTo(mediaId);
            assertThat(response.content()).isEqualTo("Ảnh gói đồ");

            verify(mediaService).attach(List.of(mediaId), RECEIVER_ID);
            verify(messageRepository).save(any(Message.class));
        }

        @Test
        @DisplayName("Gửi ảnh không kèm caption vẫn thành công khi ở LOGISTICS_CONFIRMED")
        void sendMessage_image_withoutContent_success() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.LOGISTICS_CONFIRMED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            long mediaId = 43L;
            Message savedMessage = new Message(REQUEST_ID, RECEIVER_ID, MessageKind.IMAGE, null, mediaId);
            when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

            SendMessageRequest req = new SendMessageRequest(null, mediaId);
            MessageResponse response = chatService.sendMessage(REQUEST_ID, RECEIVER_ID, req);

            assertThat(response).isNotNull();
            assertThat(response.kind()).isEqualTo(MessageKind.IMAGE.name());
            assertThat(response.mediaId()).isEqualTo(mediaId);
            assertThat(response.content()).isNull();

            verify(mediaService).attach(List.of(mediaId), RECEIVER_ID);
        }

        @Test
        @DisplayName("Ném lỗi CHAT_IMAGE_BEFORE_LOGISTICS_FORBIDDEN khi gửi ảnh ở trạng thái RESERVED (FR-75, BR-C05)")
        void sendMessage_image_throwsWhenReserved() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            SendMessageRequest req = new SendMessageRequest("Ảnh minh chứng", 44L);

            assertThatThrownBy(() -> chatService.sendMessage(REQUEST_ID, RECEIVER_ID, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ChatErrorCode.CHAT_IMAGE_BEFORE_LOGISTICS_FORBIDDEN));

            org.mockito.Mockito.verify(mediaService, org.mockito.Mockito.never())
                    .attach(any(), org.mockito.ArgumentMatchers.anyLong());
        }

        @Test
        @DisplayName(
                "Ném lỗi CHAT_IMAGE_BEFORE_LOGISTICS_FORBIDDEN khi gửi ảnh ở trạng thái AWAITING_LOGISTICS (FR-75, BR-C05)")
        void sendMessage_image_throwsWhenAwaitingLogistics() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.AWAITING_LOGISTICS, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            SendMessageRequest req = new SendMessageRequest("Ảnh món đồ", 45L);

            assertThatThrownBy(() -> chatService.sendMessage(REQUEST_ID, RECEIVER_ID, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ChatErrorCode.CHAT_IMAGE_BEFORE_LOGISTICS_FORBIDDEN));

            org.mockito.Mockito.verify(mediaService, org.mockito.Mockito.never())
                    .attach(any(), org.mockito.ArgumentMatchers.anyLong());
        }

        @Test
        @DisplayName("Gửi ảnh thành công khi ở trạng thái AWAITING_HANDOVER")
        void sendMessage_image_success_whenAwaitingHandover() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.AWAITING_HANDOVER, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            long mediaId = 46L;
            Message savedMessage = new Message(REQUEST_ID, DONOR_ID, MessageKind.IMAGE, "Đã giao hàng", mediaId);
            when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

            SendMessageRequest req = new SendMessageRequest("Đã giao hàng", mediaId);
            MessageResponse response = chatService.sendMessage(REQUEST_ID, DONOR_ID, req);

            assertThat(response).isNotNull();
            assertThat(response.kind()).isEqualTo(MessageKind.IMAGE.name());
            verify(mediaService).attach(List.of(mediaId), DONOR_ID);
        }

        @Test
        @DisplayName("Gửi thẻ bài đăng APPROVED thành công (FR-70, BR-C07)")
        void sendMessage_itemCard_success() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            long refItemId = 45L;
            ItemCardDto card = new ItemCardDto(
                    refItemId, "Bàn học gỗ", OfferType.SWAP, ItemStatus.APPROVED, "/api/v1/media/1", DONOR_ID);
            when(itemQueryService.getItemCard(refItemId)).thenReturn(card);

            Message savedMessage =
                    new Message(REQUEST_ID, RECEIVER_ID, MessageKind.ITEM_CARD, "Xem món này nhé", null, refItemId);
            when(messageRepository.save(any(Message.class))).thenReturn(savedMessage);

            SendMessageRequest req = new SendMessageRequest("Xem món này nhé", null, refItemId);
            MessageResponse response = chatService.sendMessage(REQUEST_ID, RECEIVER_ID, req);

            assertThat(response).isNotNull();
            assertThat(response.kind()).isEqualTo(MessageKind.ITEM_CARD.name());
            assertThat(response.refItemId()).isEqualTo(refItemId);
            assertThat(response.itemCard()).isEqualTo(card);
            verify(messageRepository).save(any(Message.class));
        }

        @Test
        @DisplayName("Ném lỗi CHAT_ITEM_NOT_APPROVED khi chia sẻ bài chưa APPROVED (FR-70, BR-C07)")
        void sendMessage_itemCard_throwsWhenNotApproved() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            long refItemId = 45L;
            ItemCardDto card = new ItemCardDto(
                    refItemId, "Bàn học gỗ", OfferType.SWAP, ItemStatus.PENDING_REVIEW, "/api/v1/media/1", DONOR_ID);
            when(itemQueryService.getItemCard(refItemId)).thenReturn(card);

            SendMessageRequest req = new SendMessageRequest("Xem món này nhé", null, refItemId);
            assertThatThrownBy(() -> chatService.sendMessage(REQUEST_ID, RECEIVER_ID, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ChatErrorCode.CHAT_ITEM_NOT_APPROVED));
        }

        @Test
        @DisplayName("Ném lỗi CHAT_ITEM_NOT_FOUND khi chia sẻ bài không tồn tại")
        void sendMessage_itemCard_throwsWhenNotFound() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            long refItemId = 999L;
            when(itemQueryService.getItemCard(refItemId))
                    .thenThrow(new BusinessException(ItemErrorCode.ITEM_NOT_FOUND));

            SendMessageRequest req = new SendMessageRequest(null, null, refItemId);
            assertThatThrownBy(() -> chatService.sendMessage(REQUEST_ID, RECEIVER_ID, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ChatErrorCode.CHAT_ITEM_NOT_FOUND));
        }

        @Test
        @DisplayName("Ném lỗi CHAT_MESSAGE_BLOCKED và ghi nhận vi phạm khi phát hiện thông tin liên hệ (FR-72, FR-76)")
        void sendMessage_blocked_whenContactInfoDetected() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            String content = "Gọi mình số 0912345678 nhé";
            when(chatContactFilter.checkViolation(content, true))
                    .thenReturn(java.util.Optional.of("thông tin liên hệ: số điện thoại (0912345678)"));

            SendMessageRequest req = new SendMessageRequest(content);
            assertThatThrownBy(() -> chatService.sendMessage(REQUEST_ID, RECEIVER_ID, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        assertThat(((BusinessException) e).getErrorCode())
                                .isEqualTo(ChatErrorCode.CHAT_MESSAGE_BLOCKED);
                        assertThat(e.getMessage()).contains("số điện thoại (0912345678)");
                    });

            verify(chatViolationRecorder)
                    .recordViolationAndAlertAdmin(
                            REQUEST_ID, RECEIVER_ID, content, "thông tin liên hệ: số điện thoại (0912345678)");
            verify(messageRepository, never()).save(any(Message.class));
        }

        @Test
        @DisplayName(
                "Ném lỗi CHAT_MESSAGE_BLOCKED và ghi nhận vi phạm khi phát hiện từ khoá cấm mức BLOCK (FR-78, FR-76)")
        void sendMessage_blocked_whenBlockKeywordDetected() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.LOGISTICS_CONFIRMED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            String content = "bán cần sa giá rẻ";
            // Ở LOGISTICS_CONFIRMED thì checkContactInfo = false
            when(chatContactFilter.checkViolation(content, false))
                    .thenReturn(java.util.Optional.of("từ khoá cấm (cần sa)"));

            SendMessageRequest req = new SendMessageRequest(content);
            assertThatThrownBy(() -> chatService.sendMessage(REQUEST_ID, RECEIVER_ID, req))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        assertThat(((BusinessException) e).getErrorCode())
                                .isEqualTo(ChatErrorCode.CHAT_MESSAGE_BLOCKED);
                        assertThat(e.getMessage()).contains("từ khoá cấm (cần sa)");
                    });

            verify(chatViolationRecorder)
                    .recordViolationAndAlertAdmin(REQUEST_ID, RECEIVER_ID, content, "từ khoá cấm (cần sa)");
            verify(messageRepository, never()).save(any(Message.class));
        }
    }

    @Nested
    @DisplayName("Lấy danh sách tin nhắn (getMessages)")
    class GetMessagesTest {

        @Test
        @DisplayName("Lấy toàn bộ tin nhắn thành công khi afterId là null")
        void getMessages_success_withoutAfterId() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            Message m1 = new Message(REQUEST_ID, RECEIVER_ID, "Tin 1");
            Message m2 = new Message(REQUEST_ID, DONOR_ID, "Tin 2");
            when(messageRepository.findByRequestIdOrderByIdAsc(REQUEST_ID)).thenReturn(List.of(m1, m2));

            List<MessageResponse> list = chatService.getMessages(REQUEST_ID, RECEIVER_ID, null);

            assertThat(list).hasSize(2);
            assertThat(list.get(0).content()).isEqualTo("Tin 1");
            assertThat(list.get(1).content()).isEqualTo("Tin 2");
        }

        @Test
        @DisplayName("Lấy tin nhắn có kèm thông tin thẻ bài ITEM_CARD được ghép dữ liệu đầy đủ")
        void getMessages_success_withItemCard() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            long refItemId = 45L;
            ItemCardDto card = new ItemCardDto(
                    refItemId, "Bàn học gỗ", OfferType.SWAP, ItemStatus.APPROVED, "/api/v1/media/1", DONOR_ID);
            when(itemQueryService.getItemCard(refItemId)).thenReturn(card);

            Message m1 =
                    new Message(REQUEST_ID, RECEIVER_ID, MessageKind.ITEM_CARD, "Đổi món này nhé", null, refItemId);
            when(messageRepository.findByRequestIdOrderByIdAsc(REQUEST_ID)).thenReturn(List.of(m1));

            List<MessageResponse> list = chatService.getMessages(REQUEST_ID, RECEIVER_ID, null);

            assertThat(list).hasSize(1);
            assertThat(list.get(0).refItemId()).isEqualTo(refItemId);
            assertThat(list.get(0).itemCard()).isEqualTo(card);
        }

        @Test
        @DisplayName("Lấy tin nhắn mới hơn afterId thành công khi polling")
        void getMessages_success_withAfterId() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            Message m2 = new Message(REQUEST_ID, DONOR_ID, "Tin mới");
            when(messageRepository.findByRequestIdAndIdGreaterThanOrderByIdAsc(REQUEST_ID, 10L))
                    .thenReturn(List.of(m2));

            List<MessageResponse> list = chatService.getMessages(REQUEST_ID, RECEIVER_ID, 10L);

            assertThat(list).hasSize(1);
            assertThat(list.get(0).content()).isEqualTo("Tin mới");
        }

        @Test
        @DisplayName("Chỉ trả về tin nhắn PASS và tự động loại bỏ các tin nhắn BLOCKED")
        void getMessages_filtersOutBlockedMessages() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            Message m1 = new Message(REQUEST_ID, RECEIVER_ID, "Tin hợp lệ 1");
            Message m2 = new Message(REQUEST_ID, DONOR_ID, "Tin vi phạm");
            m2.block(1, "từ khoá cấm");
            Message m3 = new Message(REQUEST_ID, RECEIVER_ID, "Tin hợp lệ 2");

            when(messageRepository.findByRequestIdOrderByIdAsc(REQUEST_ID)).thenReturn(List.of(m1, m2, m3));

            List<MessageResponse> list = chatService.getMessages(REQUEST_ID, RECEIVER_ID, null);

            assertThat(list).hasSize(2);
            assertThat(list.get(0).content()).isEqualTo("Tin hợp lệ 1");
            assertThat(list.get(1).content()).isEqualTo("Tin hợp lệ 2");
        }

        @Test
        @DisplayName("Ném lỗi CHAT_FORBIDDEN khi người thứ ba cố tình xem tin nhắn")
        void getMessages_throwsWhenForbidden() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            assertThatThrownBy(() -> chatService.getMessages(REQUEST_ID, STRANGER_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e ->
                            assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ChatErrorCode.CHAT_FORBIDDEN));
        }

        @Test
        @DisplayName("Ném lỗi CHAT_REQUEST_NOT_RESERVED khi xem tin nhắn của Request chưa RESERVED")
        void getMessages_throwsWhenNotReserved() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.PENDING, RECEIVER_ID, DONOR_ID);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            assertThatThrownBy(() -> chatService.getMessages(REQUEST_ID, RECEIVER_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ChatErrorCode.CHAT_REQUEST_NOT_RESERVED));
        }
    }

    @Nested
    @DisplayName("Ghim thẻ sản phẩm đầu chat (getPinnedItems)")
    class GetPinnedItemsTest {

        @Test
        @DisplayName("Ghim 1 thẻ sản phẩm khi yêu cầu là bài Cho Tặng (GIVE)")
        void getPinnedItems_success_giveItem() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID, 50L, null);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            ItemCardDto mainCard =
                    new ItemCardDto(50L, "Áo khoác", OfferType.GIVE, ItemStatus.RESERVED, "/api/v1/media/50", DONOR_ID);
            when(itemQueryService.getItemCard(50L)).thenReturn(mainCard);

            List<ItemCardDto> pinned = chatService.getPinnedItems(REQUEST_ID, RECEIVER_ID);

            assertThat(pinned).hasSize(1);
            assertThat(pinned.get(0).id()).isEqualTo(50L);
            assertThat(pinned.get(0).offerType()).isEqualTo(OfferType.GIVE);
        }

        @Test
        @DisplayName("Ghim cả hai thẻ sản phẩm khi yêu cầu là bài Trao Đổi (SWAP) (FR-69, BR-C02)")
        void getPinnedItems_success_swapBothItems() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID, 50L, 60L);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            ItemCardDto mainCard =
                    new ItemCardDto(50L, "Bàn học", OfferType.SWAP, ItemStatus.RESERVED, "/api/v1/media/50", DONOR_ID);
            ItemCardDto offeredCard = new ItemCardDto(
                    60L, "Ghế xoay", OfferType.SWAP, ItemStatus.RESERVED, "/api/v1/media/60", RECEIVER_ID);
            when(itemQueryService.getItemCard(50L)).thenReturn(mainCard);
            when(itemQueryService.getItemCard(60L)).thenReturn(offeredCard);

            List<ItemCardDto> pinned = chatService.getPinnedItems(REQUEST_ID, DONOR_ID);

            assertThat(pinned).hasSize(2);
            assertThat(pinned.get(0).id()).isEqualTo(50L);
            assertThat(pinned.get(1).id()).isEqualTo(60L);
        }

        @Test
        @DisplayName("Ném lỗi CHAT_FORBIDDEN khi người ngoài cố tình lấy thẻ sản phẩm ghim")
        void getPinnedItems_throwsWhenForbidden() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.RESERVED, RECEIVER_ID, DONOR_ID, 50L, 60L);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            assertThatThrownBy(() -> chatService.getPinnedItems(REQUEST_ID, STRANGER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e ->
                            assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ChatErrorCode.CHAT_FORBIDDEN));
        }

        @Test
        @DisplayName("Ném lỗi CHAT_REQUEST_NOT_RESERVED khi lấy thẻ ghim ở trạng thái PENDING")
        void getPinnedItems_throwsWhenNotReserved() {
            RequestChatContext context =
                    new RequestChatContext(REQUEST_ID, RequestStatus.PENDING, RECEIVER_ID, DONOR_ID, 50L, null);
            when(requestService.getChatContext(REQUEST_ID)).thenReturn(context);

            assertThatThrownBy(() -> chatService.getPinnedItems(REQUEST_ID, RECEIVER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                            .isEqualTo(ChatErrorCode.CHAT_REQUEST_NOT_RESERVED));
        }
    }
}
