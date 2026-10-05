package com.shareloop.integration.mail;

/** Cổng gửi email của hệ thống. Tách interface để test thay bằng mock, không gửi mail thật. */
public interface EmailSender {

    void send(String to, String subject, String htmlBody);
}
