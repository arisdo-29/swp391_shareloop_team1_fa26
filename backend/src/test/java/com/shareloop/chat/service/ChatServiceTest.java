package com.shareloop.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shareloop.chat.ChatErrorCode;
import com.shareloop.chat.dto.MessageResponse;
import com.shareloop.chat.dto.SendMessageRequest;
import com.shareloop.chat.entity.Message;
import com.shareloop.chat.entity.MessageKind;
import com.shareloop.chat.repository.MessageRepository;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.request.RequestErrorCode;
import com.shareloop.request.dto.RequestChatContext;
import com.shareloop.request.entity.RequestStatus;
import com.shareloop.request.service.RequestService;
import java.util.List;
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
}
