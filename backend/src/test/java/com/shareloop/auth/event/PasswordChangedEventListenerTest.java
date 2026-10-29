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
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class PasswordChangedEventListenerTest {

    private static final String EMAIL = "an@shareloop.local";
    private static final String SUBJECT = "Mật khẩu ShareLoop vừa được đổi";
    // 03:30 UTC ngày 29/10 là 10:30 ngày 29/10 giờ Việt Nam.
    private static final Instant CHANGED_AT = Instant.parse("2026-10-29T03:30:00Z");

    @Mock
    EmailSender emailSender;

    PasswordChangedEventListener listener;
    ListAppender<ILoggingEvent> logAppender;
    Logger listenerLogger;

    @BeforeEach
    void setUp() {
        listener = new PasswordChangedEventListener(emailSender, new MailTemplateRenderer());
        listenerLogger = (Logger) LoggerFactory.getLogger(PasswordChangedEventListener.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        listenerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        listenerLogger.detachAppender(logAppender);
    }

    @Test
    void sendPasswordChangedEmail_sendsVietnamTimeAndForgotPasswordAdvice() {
        // given
        PasswordChangedEvent event = new PasswordChangedEvent(EMAIL, CHANGED_AT);

        // when
        listener.sendPasswordChangedEmail(event);

        // then
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(eq(EMAIL), eq(SUBJECT), body.capture());
        assertThat(body.getValue())
                .contains("vừa được đổi lúc <b>10:30 29/10</b>")
                .contains("Nếu không phải bạn, hãy dùng Quên mật khẩu ngay.");
    }

    @Test
    void sendPasswordChangedEmail_whenSenderFails_onlyLogsError() {
        // given
        doThrow(new IllegalStateException("smtp down")).when(emailSender).send(anyString(), anyString(), anyString());

        // when: lỗi gửi không được ném ra ngoài
        listener.sendPasswordChangedEmail(new PasswordChangedEvent(EMAIL, CHANGED_AT));

        // then
        assertThat(logAppender.list).hasSize(1);
        assertThat(logAppender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
        assertThat(logAppender.list.get(0).getFormattedMessage()).contains(EMAIL);
    }
}
