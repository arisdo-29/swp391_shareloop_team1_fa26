package com.shareloop.integration.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Unit test đọc mẫu thật trong src/main/resources/templates/mail, không cần Spring. */
class MailTemplateRendererTest {

    private static final String LINK = "https://shareloop.local/requests/1";

    private final MailTemplateRenderer mailTemplateRenderer = new MailTemplateRenderer();

    @Test
    void renderOtp_replacesCodeAndTtlMinutes() {
        // given
        String code = "482915";

        // when
        String html = mailTemplateRenderer.renderOtp(code, 5);

        // then
        assertThat(html).contains(code).contains("5 phút").doesNotContain("{{");
    }

    @Test
    void renderOtp_codeIsFirstSixDigitSequence() {
        // given: AuthRegisterIT lấy dãy 6 chữ số đầu tiên trong email làm mã OTP
        String code = "482915";

        // when
        String html = mailTemplateRenderer.renderOtp(code, 5);

        // then
        Matcher matcher = Pattern.compile("\\d{6}").matcher(html);
        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group()).isEqualTo(code);
    }

    @Test
    void renderOtp_containsRegisterNote() {
        // when
        String html = mailTemplateRenderer.renderOtp("482915", 5);

        // then
        assertThat(html).contains("Nếu bạn không đăng ký tài khoản ShareLoop").doesNotContain("đặt lại mật khẩu");
    }

    @Test
    void renderPasswordResetOtp_containsCodeAndResetNote() {
        // given
        String code = "482915";

        // when
        String html = mailTemplateRenderer.renderPasswordResetOtp(code, 5);

        // then
        assertThat(html)
                .contains(code)
                .contains("5 phút")
                .contains("đặt lại mật khẩu ShareLoop")
                .doesNotContain("Nếu bạn không đăng ký")
                .doesNotContain("{{");
    }

    @Test
    void renderChangePhoneOtp_containsCodeHeadingAndChangePhoneNote() {
        // given
        String code = "482915";

        // when
        String html = mailTemplateRenderer.renderChangePhoneOtp(code, 5);

        // then: mẫu riêng, không lẫn lời của email đăng ký hay đặt lại mật khẩu
        assertThat(html)
                .contains(code)
                .contains("5 phút")
                .contains("Mã xác nhận đổi số điện thoại")
                .contains("đổi số điện thoại trên ShareLoop")
                .doesNotContain("Nếu bạn không đăng ký")
                .doesNotContain("{{");
    }

    @Test
    void renderChangePhoneOtp_codeIsFirstSixDigitSequence() {
        // when
        String html = mailTemplateRenderer.renderChangePhoneOtp("482915", 5);

        // then
        Matcher matcher = Pattern.compile("\\d{6}").matcher(html);
        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group()).isEqualTo("482915");
    }

    @Test
    void renderNotification_escapesHtmlInValues() {
        // given
        String title = "<script>alert('x')</script>";
        String content = "Tom & Jerry <b>đậm</b>";

        // when
        String html = mailTemplateRenderer.renderNotification(title, content, LINK);

        // then
        assertThat(html)
                .contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;")
                .contains("Tom &amp; Jerry &lt;b&gt;đậm&lt;/b&gt;")
                .doesNotContain("<script>");
    }

    @Test
    void renderNotification_keepsVietnameseCharacters() {
        // given
        String title = "Yêu cầu đã được chấp nhận";

        // when
        String html = mailTemplateRenderer.renderNotification(title, "Hẹn gặp bạn ở điểm giao nhận.", LINK);

        // then
        assertThat(html)
                .contains(title)
                .contains("Hẹn gặp bạn ở điểm giao nhận.")
                .contains("href=\"" + LINK + "\"");
    }

    @Test
    void renderNotification_whenLinkIsNotHttp_throwsIllegalArgument() {
        // given
        String link = "javascript:alert(1)";

        // when / then
        assertThatThrownBy(() -> mailTemplateRenderer.renderNotification("Tiêu đề", "Nội dung", link))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(link);
    }

    @Test
    void render_whenTemplateMissing_throwsWithTemplatePath() {
        // given
        String templateName = "khong-ton-tai";

        // when / then
        assertThatThrownBy(() -> mailTemplateRenderer.render(templateName, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("templates/mail/khong-ton-tai.html");
    }

    @Test
    void render_whenValueMissing_throwsWithPlaceholderName() {
        // given: mẫu otp cần cả otp và ttlMinutes
        Map<String, String> values = Map.of("otp", "482915");

        // when / then
        assertThatThrownBy(() -> mailTemplateRenderer.render("otp", values))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("{{ttlMinutes}}");
    }
}
