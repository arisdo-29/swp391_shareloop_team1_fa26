package com.shareloop.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.moderation.ContactInfoDetector.ContactType;
import com.shareloop.moderation.ContactInfoDetector.Match;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit test cho {@link ContactInfoDetector}.
 *
 * <p>Bộ test bao gồm:
 * <ul>
 *   <li>Ca khớp đúng (true positive): SĐT, email, URL trong nhiều dạng ngụy trang.</li>
 *   <li>Ca không khớp nhầm (false negative / false positive): số tiền, năm, mã sản phẩm,
 *       mã đơn hàng không được nhận nhầm là thông tin liên hệ.</li>
 * </ul>
 */
class ContactInfoDetectorTest {

    // =========================================================================
    // Số điện thoại
    // =========================================================================

    @Nested
    @DisplayName("Phát hiện số điện thoại di động Việt Nam")
    class PhoneDetectionTest {

        @Test
        @DisplayName("Bắt SĐT viết liền — đầu số 09x")
        void detect_plainPhone_09x() {
            List<Match> hits = ContactInfoDetector.detect("Liên hệ 0912345678 để biết thêm.");
            assertThat(hits).anyMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Bắt SĐT viết liền — đầu số 03x")
        void detect_plainPhone_03x() {
            List<Match> hits = ContactInfoDetector.detect("SĐT của mình là 0356789012.");
            assertThat(hits).anyMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Bắt SĐT cách nhau bằng dấu cách")
        void detect_phoneWithSpaces() {
            List<Match> hits = ContactInfoDetector.detect("Gọi 091 234 5678 nhé.");
            assertThat(hits).anyMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Bắt SĐT cách nhau bằng dấu chấm")
        void detect_phoneWithDots() {
            List<Match> hits = ContactInfoDetector.detect("090.123.4567");
            assertThat(hits).anyMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Bắt SĐT cách nhau bằng dấu gạch ngang")
        void detect_phoneWithDashes() {
            List<Match> hits = ContactInfoDetector.detect("Nhắn 085-999-8877 cho mình.");
            assertThat(hits).anyMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Bắt SĐT có dấu tiếng Việt xung quanh")
        void detect_phoneInsideVietnameseText() {
            List<Match> hits = ContactInfoDetector.detect("Bạn nhắn tin qua 0798001234 được nha!");
            assertThat(hits).anyMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Không bắt nhầm số tiền (1.000.000)")
        void noFalsePositive_moneyAmount() {
            List<Match> hits = ContactInfoDetector.detect("Giá bán 1.000.000 đồng.");
            assertThat(hits).noneMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Không bắt nhầm số năm (2024)")
        void noFalsePositive_year() {
            List<Match> hits = ContactInfoDetector.detect("Sản xuất năm 2024, còn mới 90%.");
            assertThat(hits).noneMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Không bắt nhầm mã sản phẩm dạng SL-12345")
        void noFalsePositive_productCode() {
            List<Match> hits = ContactInfoDetector.detect("Mã sản phẩm: SL-12345, bảo hành 12 tháng.");
            assertThat(hits).noneMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Không bắt đầu số không hợp lệ (01x cũ hoặc 04x)")
        void noFalsePositive_invalidPrefix() {
            List<Match> hits = ContactInfoDetector.detect("Số cũ 0123456789, số khác 0412345678.");
            assertThat(hits).noneMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Không bắt dãy 11 chữ số liên tiếp (mã vạch, mã đơn hàng)")
        void noFalsePositive_elevenDigitsBarcode() {
            // Mã đơn hàng 11 chữ số → không phải SĐT 10 chữ số hợp lệ VN
            List<Match> hits = ContactInfoDetector.detect("Mã đơn: 09123456789.");
            assertThat(hits).noneMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("Trích đúng đoạn văn bản gốc có dấu và ký tự ngăn cách")
        void detect_returnsOriginalMatchedText() {
            List<Match> hits = ContactInfoDetector.detect("Gọi số 091 234 5678 nhé!");
            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).type()).isEqualTo(ContactType.PHONE);
            assertThat(hits.get(0).matched()).isEqualTo("091 234 5678");
        }
    }

    // =========================================================================
    // Email
    // =========================================================================

    @Nested
    @DisplayName("Phát hiện địa chỉ email")
    class EmailDetectionTest {

        @Test
        @DisplayName("Bắt email chuẩn")
        void detect_standardEmail() {
            List<Match> hits = ContactInfoDetector.detect("Nhắn vào user@gmail.com nhé.");
            assertThat(hits).anyMatch(m -> m.type() == ContactType.EMAIL);
        }

        @Test
        @DisplayName("Bắt email có subdomain")
        void detect_emailWithSubdomain() {
            List<Match> hits = ContactInfoDetector.detect("Gửi đến info@mail.company.vn.");
            assertThat(hits).anyMatch(m -> m.type() == ContactType.EMAIL);
        }

        @Test
        @DisplayName("Bắt email có dấu cộng")
        void detect_emailWithPlus() {
            List<Match> hits = ContactInfoDetector.detect("Email: user+tag@example.org");
            assertThat(hits).anyMatch(m -> m.type() == ContactType.EMAIL);
        }

        @Test
        @DisplayName("Không bắt nhầm '@' đứng một mình (mention)")
        void noFalsePositive_mentionSymbol() {
            List<Match> hits = ContactInfoDetector.detect("@shareloop là tài khoản chính thức.");
            assertThat(hits).noneMatch(m -> m.type() == ContactType.EMAIL);
        }
    }

    // =========================================================================
    // URL
    // =========================================================================

    @Nested
    @DisplayName("Phát hiện URL")
    class UrlDetectionTest {

        @Test
        @DisplayName("Bắt URL https://")
        void detect_httpsUrl() {
            List<Match> hits = ContactInfoDetector.detect("Xem tại https://shareloop.vn/items/123.");
            assertThat(hits).anyMatch(m -> m.type() == ContactType.URL);
        }

        @Test
        @DisplayName("Bắt URL http://")
        void detect_httpUrl() {
            List<Match> hits = ContactInfoDetector.detect("Truy cập http://example.com để biết thêm.");
            assertThat(hits).anyMatch(m -> m.type() == ContactType.URL);
        }

        @Test
        @DisplayName("Bắt URL bắt đầu bằng www.")
        void detect_wwwUrl() {
            List<Match> hits = ContactInfoDetector.detect("Vào www.facebook.com/nhom nhé.");
            assertThat(hits).anyMatch(m -> m.type() == ContactType.URL);
        }

        @Test
        @DisplayName("Không bắt nhầm từ 'www' đứng một mình không có TLD")
        void noFalsePositive_wwwAlone() {
            List<Match> hits = ContactInfoDetector.detect("Tên viết tắt là WWW nhưng không có link.");
            assertThat(hits).noneMatch(m -> m.type() == ContactType.URL);
        }
    }

    // =========================================================================
    // Tổng hợp và edge cases
    // =========================================================================

    @Nested
    @DisplayName("Edge cases và kiểm tra tổng hợp")
    class EdgeCaseTest {

        @Test
        @DisplayName("Văn bản null trả danh sách rỗng")
        void detect_null_returnsEmpty() {
            assertThat(ContactInfoDetector.detect(null)).isEmpty();
        }

        @Test
        @DisplayName("Văn bản rỗng trả danh sách rỗng")
        void detect_empty_returnsEmpty() {
            assertThat(ContactInfoDetector.detect("  ")).isEmpty();
        }

        @Test
        @DisplayName("Bắt nhiều loại trong cùng một tin nhắn")
        void detect_multipleTypesInOneMessage() {
            String text = "Gọi 0912345678 hoặc email user@gmail.com hoặc vào www.example.com";
            List<Match> hits = ContactInfoDetector.detect(text);
            assertThat(hits).anyMatch(m -> m.type() == ContactType.PHONE);
            assertThat(hits).anyMatch(m -> m.type() == ContactType.EMAIL);
            assertThat(hits).anyMatch(m -> m.type() == ContactType.URL);
        }

        @Test
        @DisplayName("Bắt SĐT bị ngụy trang bằng dấu cộng chữ hoa")
        void detect_phoneInVietnameseSentence() {
            // "không chín không" → cần module chat tự chuyển trước (tầng 2), ở đây chỉ test tầng 1
            List<Match> hits = ContactInfoDetector.detect("ZALO: 0 9 1 2 3 4 5 6 7 8");
            // Sau normalize: "0912345678" — phải bắt được
            assertThat(hits).anyMatch(m -> m.type() == ContactType.PHONE);
        }

        @Test
        @DisplayName("normalize bỏ dấu tiếng Việt đúng cách")
        void normalize_removesVietnameseDiacritics() {
            String result = ContactInfoDetector.normalize("Điện thoại: 0912 345 678");
            assertThat(result).isEqualTo("dien thoai: 0912345678");
        }

        @Test
        @DisplayName("normalize gộp dấu ngăn cách giữa số nhưng giữ dấu cách thông thường")
        void normalize_mergesSeparatorsBetweenDigitsOnly() {
            // Giữa chữ và số: không xoá
            String result = ContactInfoDetector.normalize("model X-500 mã 0912.345.678");
            assertThat(result).contains("0912345678");
            // "x-500" → "x-500" (không xoá vì X là chữ, không phải số)
            assertThat(result).contains("x-500");
        }
    }
}
