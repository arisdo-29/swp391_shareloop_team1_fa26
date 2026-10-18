package com.shareloop.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shareloop.chat.ChatErrorCode;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.moderation.ContactInfoDetector.ContactType;
import com.shareloop.moderation.ContactInfoDetector.Match;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit test cho bộ lọc thông tin liên hệ tầng 1 của module chat (FR-72, FR-74; BR-C01).
 */
class ChatContactFilterTest {

    private final ChatContactFilter filter = new ChatContactFilter();

    @Nested
    @DisplayName("Ca bắt đúng thông tin liên hệ (True Positive)")
    class TruePositiveTest {

        @Test
        @DisplayName("Bắt số điện thoại viết bằng chữ tiếng Việt có dấu")
        void detect_spelledPhone_withDiacritics() {
            String text = "Zalo của mình là không chín một hai ba bốn năm sáu bảy tám nhé";
            List<Match> hits = filter.detectContactInfo(text);

            assertThat(hits).isNotEmpty();
            assertThat(hits).anyMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Bắt số điện thoại viết bằng chữ tiếng Việt không dấu")
        void detect_spelledPhone_withoutDiacritics() {
            String text = "nhan qua so khong chin mot hai ba bon nam sau bay tam nha";
            List<Match> hits = filter.detectContactInfo(text);

            assertThat(hits).isNotEmpty();
            assertThat(hits).anyMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Bắt số điện thoại viết bằng chữ ngăn cách bằng dấu chấm")
        void detect_spelledPhone_withDots() {
            String text = "không.chín.một.hai.ba.bốn.năm.sáu.bảy.tám";
            List<Match> hits = filter.detectContactInfo(text);

            assertThat(hits).isNotEmpty();
            assertThat(hits).anyMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Bắt số điện thoại viết bằng chữ ngăn cách bằng dấu gạch ngang")
        void detect_spelledPhone_withDashes() {
            String text = "không-chín-một-hai-ba-bốn-năm-sáu-bảy-tám";
            List<Match> hits = filter.detectContactInfo(text);

            assertThat(hits).isNotEmpty();
            assertThat(hits).anyMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Bắt số điện thoại viết lẫn lộn chữ và số")
        void detect_spelledPhone_mixedLettersAndDigits() {
            String text = "Gọi 0 chín 1 hai 3 bốn 5 sáu 7 8 nhé bạn";
            List<Match> hits = filter.detectContactInfo(text);

            assertThat(hits).isNotEmpty();
            assertThat(hits).anyMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Bắt số điện thoại viết số bình thường")
        void detect_standardPhone() {
            String text = "Alo cho mình số 0912345678 nhé";
            List<Match> hits = filter.detectContactInfo(text);

            assertThat(hits).isNotEmpty();
            assertThat(hits).anyMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Bắt email trong tin nhắn")
        void detect_email() {
            String text = "Gửi mail qua contact@shareloop.vn";
            List<Match> hits = filter.detectContactInfo(text);

            assertThat(hits).isNotEmpty();
            assertThat(hits).anyMatch(m -> m.type() == ContactType.EMAIL);
        }

        @Test
        @DisplayName("Bắt URL trong tin nhắn")
        void detect_url() {
            String text = "Xem ảnh ở https://facebook.com/myphotos";
            List<Match> hits = filter.detectContactInfo(text);

            assertThat(hits).isNotEmpty();
            assertThat(hits).anyMatch(m -> m.type() == ContactType.URL);
        }

        @Test
        @DisplayName("validateMessage ném BusinessException kèm lý do rõ ràng khi vi phạm (FR-74)")
        void validateMessage_throwsWithClearReason() {
            String text = "Số mình: không chín một hai ba bốn năm sáu bảy tám";

            assertThatThrownBy(() -> filter.validateMessage(text))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> {
                        BusinessException be = (BusinessException) e;
                        assertThat(be.getErrorCode()).isEqualTo(ChatErrorCode.CHAT_MESSAGE_BLOCKED);
                        assertThat(be.getMessage()).contains("Tin nhắn bị chặn do chứa thông tin liên hệ");
                    });
        }
    }

    @Nested
    @DisplayName("Ca không chặn nhầm (False Positive Prevention)")
    class FalsePositivePreventionTest {

        @Test
        @DisplayName("Không chặn câu hỏi thông thường chứa từ 'không'")
        void noFalsePositive_questionWithKhong() {
            String text = "Chào bạn, món đồ này còn sử dụng tốt không?";
            List<Match> hits = filter.detectContactInfo(text);

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("Không chặn câu có chứa từ 'hai', 'ba', 'năm' nhưng không đủ chuỗi số")
        void noFalsePositive_everydayWords() {
            String text = "Nhà mình có hai bé năm nay học lớp ba và lớp năm";
            List<Match> hits = filter.detectContactInfo(text);

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("Không chặn câu có số tiền và năm")
        void noFalsePositive_moneyAndYear() {
            String text = "Sách này mình mua 150.000đ vào năm 2024, còn rất mới";
            List<Match> hits = filter.detectContactInfo(text);

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("Không chặn lời chào tạm biệt có chữ 'tạm'")
        void noFalsePositive_goodbyeWord() {
            String text = "Cảm ơn bạn nhiều, tạm biệt bạn nhé!";
            List<Match> hits = filter.detectContactInfo(text);

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("Không chặn mã sản phẩm")
        void noFalsePositive_productCode() {
            String text = "Mã sản phẩm của máy là SL-98765";
            List<Match> hits = filter.detectContactInfo(text);

            assertThat(hits).isEmpty();
        }

        @Test
        @DisplayName("Tin nhắn rỗng hoặc null không bị chặn")
        void noFalsePositive_nullOrBlank() {
            assertThat(filter.detectContactInfo(null)).isEmpty();
            assertThat(filter.detectContactInfo("   ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("Kiểm tra từ khoá cấm mức BLOCK trong Lexicon (FR-78, BR-A10)")
    class LexiconBlockTest {

        private final com.shareloop.setting.service.ConfigService configService =
                org.mockito.Mockito.mock(com.shareloop.setting.service.ConfigService.class);
        private final com.shareloop.setting.service.LexiconMatcher lexiconMatcher =
                new com.shareloop.setting.service.LexiconMatcher();
        private final ChatContactFilter lexiconFilter = new ChatContactFilter(configService, lexiconMatcher);

        @org.junit.jupiter.api.BeforeEach
        void setUp() {
            org.mockito.Mockito.when(configService.lexicon())
                    .thenReturn(List.of(
                            new com.shareloop.setting.dto.LexiconEntry(
                                    "cần sa", "DRUGS", "BLOCK", "PHRASE", List.of(), "BOTH", 1),
                            new com.shareloop.setting.dto.LexiconEntry(
                                    "súng", "WEAPONS", "BLOCK", "WORD", List.of("súng nước"), "CHAT", 2),
                            new com.shareloop.setting.dto.LexiconEntry(
                                    "từ cấm bài", "OTHER", "BLOCK", "WORD", List.of(), "POST", 3),
                            new com.shareloop.setting.dto.LexiconEntry(
                                    "zalo", "CONTACT", "REVIEW", "WORD", List.of(), "CHAT", 4)));
        }

        @Test
        @DisplayName("Chặn tin nhắn chứa từ khoá cấm mức BLOCK có scope BOTH")
        void detect_bannedKeyword_bothScope() {
            java.util.Optional<String> result = lexiconFilter.checkViolation("Bạn có cần sa không bạn ơi", true);

            assertThat(result).isPresent();
            assertThat(result.get()).contains("từ khoá cấm (cần sa)");
        }

        @Test
        @DisplayName("Chặn tin nhắn chứa từ khoá cấm mức BLOCK có scope CHAT")
        void detect_bannedKeyword_chatScope() {
            java.util.Optional<String> result = lexiconFilter.checkViolation("Tôi mang theo khẩu súng nhé", true);

            assertThat(result).isPresent();
            assertThat(result.get()).contains("từ khoá cấm (súng)");
        }

        @Test
        @DisplayName("Không chặn khi từ khoá trùng với trường hợp ngoại lệ (exceptions)")
        void ignore_bannedKeyword_whenExceptionMatches() {
            java.util.Optional<String> result = lexiconFilter.checkViolation("Mình tặng súng nước cho bé chơi", true);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Không chặn từ khoá có scope POST trong chat")
        void ignore_bannedKeyword_whenScopeIsPostOnly() {
            java.util.Optional<String> result = lexiconFilter.checkViolation("Nội dung chứa từ cấm bài", true);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Không chặn từ khoá mức REVIEW (chỉ chặn mức BLOCK theo FR-78)")
        void ignore_reviewLevelKeyword() {
            java.util.Optional<String> result = lexiconFilter.checkViolation("Trao đổi qua zalo nhé bạn", false);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Sau khi chốt lịch (checkContactInfo = false): SĐT không bị chặn nhưng từ cấm BLOCK vẫn bị chặn")
        void afterLogisticsConfirmed_contactInfoAllowed_butBlockKeywordsStillBlocked() {
            // SĐT không bị chặn khi checkContactInfo = false
            java.util.Optional<String> phoneResult = lexiconFilter.checkViolation("Số mình 0912345678 nhé", false);
            assertThat(phoneResult).isEmpty();

            // Từ cấm mức BLOCK vẫn bị chặn
            java.util.Optional<String> drugResult = lexiconFilter.checkViolation("Giao cho mình ít cần sa nhé", false);
            assertThat(drugResult).isPresent();
            assertThat(drugResult.get()).contains("từ khoá cấm (cần sa)");
        }
    }
}
