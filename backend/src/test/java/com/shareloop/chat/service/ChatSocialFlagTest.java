package com.shareloop.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.shareloop.setting.dto.LexiconEntry;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.setting.service.LexiconMatcher;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** D3: tên và đường dẫn mạng xã hội trong chat bị gắn cờ mức REVIEW, không bị chặn. Từ khoá giống migration. */
@ExtendWith(MockitoExtension.class)
class ChatSocialFlagTest {

    @Mock
    ConfigService configService;

    ChatContactFilter filter;

    @BeforeEach
    void setUp() {
        List<LexiconEntry> lexicon = List.of(
                chatEntry("facebook.com", "PHRASE"),
                chatEntry("fb.com", "PHRASE"),
                chatEntry("fb.me", "PHRASE"),
                chatEntry("m.me", "PHRASE"),
                chatEntry("zalo.me", "PHRASE"),
                chatEntry("instagram.com", "PHRASE"),
                chatEntry("tiktok.com", "PHRASE"),
                chatEntry("t.me", "PHRASE"),
                chatEntry("zalo", "WORD"),
                chatEntry("facebook", "WORD"),
                chatEntry("insta", "WORD"),
                // Luật chỉ dành cho bài đăng không được áp vào chat.
                new LexiconEntry("nhẫn kim cương", "CONTACT", "REVIEW", "PHRASE", List.of(), "POST", 1));
        when(configService.lexicon()).thenReturn(lexicon);
        filter = new ChatContactFilter(configService, new LexiconMatcher());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Bạn kết bạn zalo với mình nhé",
                "mình bán trên Facebook luôn",
                "xem thêm ảnh ở insta của mình",
                "nhắn qua m.me/shopcu giúp mình",
                "link tiktok.com/@abc"
            })
    void findReviewMatch_whenMessageMentionsSocialNetwork_returnsKeyword(String message) {
        // when
        var match = filter.findReviewMatch(message);

        // then
        assertThat(match).isPresent();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Hẹn gặp ở cổng trường lúc 5 giờ chiều",
                "Máy này mở khoá bằng Face ID được không",
                "Mì ăn liền instant còn hạn không bạn",
                "Cho mình hỏi món này còn không",
                "nhẫn kim cương này đẹp quá"
            })
    void findReviewMatch_whenOrdinarySentence_returnsEmpty(String message) {
        // when
        var match = filter.findReviewMatch(message);

        // then
        assertThat(match).isEmpty();
    }

    @Test
    void checkViolation_whenOnlyReviewKeyword_doesNotBlock() {
        // given: chỉ nhắc tên mạng xã hội, không kèm số điện thoại, email hay liên kết web
        String message = "mình hay dùng zalo";

        // when
        var violation = filter.checkViolation(message, true);

        // then: từ khoá mức REVIEW không bao giờ chặn tin nhắn
        assertThat(violation).isEmpty();
    }

    private static LexiconEntry chatEntry(String term, String mode) {
        return new LexiconEntry(term, "CONTACT", "REVIEW", mode, List.of(), "CHAT", 1);
    }
}
