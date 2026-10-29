package com.shareloop.auth.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.shareloop.integration.mail.EmailSender;
import com.shareloop.integration.mail.MailTemplateRenderer;
import com.shareloop.user.entity.OtpPurpose;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class OtpIssuedEventListenerTest {

    private static final String EMAIL = "an@shareloop.local";
    private static final String CODE = "482915";

    @Mock
    EmailSender emailSender;

    OtpIssuedEventListener listener;
    ListAppender<ILoggingEvent> logAppender;
    Logger listenerLogger;

    @BeforeEach
    void setUp() {
        listener = new OtpIssuedEventListener(emailSender, new MailTemplateRenderer());
        listenerLogger = (Logger) LoggerFactory.getLogger(OtpIssuedEventListener.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        listenerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        listenerLogger.detachAppender(logAppender);
    }

    @Test
    void sendOtpEmail_whenPurposeChangePhone_sendsChangePhoneSubjectAndCode() {
        // given
        OtpIssuedEvent event = new OtpIssuedEvent(EMAIL, CODE, 5, OtpPurpose.CHANGE_PHONE);

        // when
        listener.sendOtpEmail(event);

        // then
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq(EMAIL), eq("Mã xác nhận đổi số điện thoại"), body.capture());
        assertThat(body.getValue()).contains(CODE).contains("đổi số điện thoại");
    }

    @Test
    void sendOtpEmail_whenPurposeRegister_keepsRegisterSubject() {
        // given
        OtpIssuedEvent event = new OtpIssuedEvent(EMAIL, CODE, 5, OtpPurpose.REGISTER);

        // when
        listener.sendOtpEmail(event);

        // then
        verify(emailSender).send(eq(EMAIL), eq("Mã xác thực ShareLoop"), anyString());
    }

    @Test
    void sendOtpEmail_whenSenderFails_logsErrorWithContextButNotTheCode() {
        // given
        doThrow(new IllegalStateException("smtp down")).when(emailSender).send(anyString(), anyString(), anyString());
        OtpIssuedEvent event = new OtpIssuedEvent(EMAIL, CODE, 5, OtpPurpose.CHANGE_PHONE);

        // when: lỗi gửi không được ném ra ngoài
        listener.sendOtpEmail(event);

        // then: có log ERROR kèm mục đích và người nhận, không có mã OTP
        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent logged = logAppender.list.get(0);
        assertThat(logged.getLevel()).isEqualTo(Level.ERROR);
        assertThat(logged.getFormattedMessage())
                .contains("CHANGE_PHONE")
                .contains(EMAIL)
                .doesNotContain(CODE);
    }
}
