package com.shareloop.chat.service;

import com.shareloop.chat.ChatErrorCode;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.moderation.ContactInfoDetector;
import com.shareloop.moderation.ContactInfoDetector.Match;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Bộ lọc thông tin liên hệ tầng 1 cho module chat (FR-72, FR-74; BR-C01).
 *
 * <p>Tái sử dụng {@link ContactInfoDetector} và bổ sung phần riêng của chat:
 * nhận diện các chữ số viết bằng chữ tiếng Việt ("không", "một", "hai"...) kể cả viết
 * cách quãng hoặc lẫn lộn chữ số, sau đó áp bộ phát hiện của {@link ContactInfoDetector}.
 */
@Component
public class ChatContactFilter {

    private static final Pattern ZERO =
            Pattern.compile("(?<=[^a-z0-9]|^)(khong|dzo|zo|zero)(?=[^a-z0-9]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ONE =
            Pattern.compile("(?<=[^a-z0-9]|^)(mot|nhat)(?=[^a-z0-9]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TWO =
            Pattern.compile("(?<=[^a-z0-9]|^)(hai)(?=[^a-z0-9]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern THREE =
            Pattern.compile("(?<=[^a-z0-9]|^)(ba)(?=[^a-z0-9]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FOUR =
            Pattern.compile("(?<=[^a-z0-9]|^)(bon|tu)(?=[^a-z0-9]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIVE =
            Pattern.compile("(?<=[^a-z0-9]|^)(nam|lam)(?=[^a-z0-9]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SIX =
            Pattern.compile("(?<=[^a-z0-9]|^)(sau)(?=[^a-z0-9]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEVEN =
            Pattern.compile("(?<=[^a-z0-9]|^)(bay|bey)(?=[^a-z0-9]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EIGHT =
            Pattern.compile("(?<=[^a-z0-9]|^)(tam)(?=[^a-z0-9]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NINE =
            Pattern.compile("(?<=[^a-z0-9]|^)(chin)(?=[^a-z0-9]|$)", Pattern.CASE_INSENSITIVE);

    /**
     * Kiểm tra nội dung tin nhắn. Nếu phát hiện thông tin liên hệ thì ném {@link BusinessException}
     * kèm lý do chi tiết theo FR-74.
     *
     * @param content nội dung tin nhắn
     * @throws BusinessException nếu phát hiện số điện thoại, email hoặc URL
     */
    public void validateMessage(String content) {
        List<Match> violations = detectContactInfo(content);
        if (!violations.isEmpty()) {
            Match first = violations.get(0);
            String detail =
                    switch (first.type()) {
                        case PHONE -> "số điện thoại (" + first.matched() + ")";
                        case EMAIL -> "địa chỉ email (" + first.matched() + ")";
                        case URL -> "liên kết web (" + first.matched() + ")";
                    };
            throw new BusinessException(
                    ChatErrorCode.CHAT_MESSAGE_BLOCKED, "Tin nhắn bị chặn do chứa thông tin liên hệ: " + detail + ".");
        }
    }

    /**
     * Phát hiện các vi phạm thông tin liên hệ trong tin nhắn chat.
     *
     * @param content nội dung tin nhắn
     * @return danh sách vi phạm; rỗng nếu tin nhắn sạch
     */
    public List<Match> detectContactInfo(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        // Bước 1: Quét trực tiếp trên chuỗi gốc bằng ContactInfoDetector (bắt SĐT số, email, url)
        List<Match> directMatches = ContactInfoDetector.detect(content);
        if (!directMatches.isEmpty()) {
            return directMatches;
        }

        // Bước 2: Chuẩn hoá các chữ số viết bằng chữ tiếng Việt ("không", "chín"...) sang chữ số
        String converted = convertSpelledNumbers(content);

        // Bước 3: Áp ContactInfoDetector trên chuỗi đã chuyển đổi số
        List<Match> convertedMatches = ContactInfoDetector.detect(converted);
        if (!convertedMatches.isEmpty()) {
            return convertedMatches;
        }

        return List.of();
    }

    /**
     * Chuyển đổi các từ chỉ chữ số tiếng Việt ("không", "một", "hai"...) sang chữ số tương ứng ("0", "1", "2"...).
     */
    public String convertSpelledNumbers(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String unaccented = stripDiacritics(text);

        String result = ZERO.matcher(unaccented).replaceAll("0");
        result = ONE.matcher(result).replaceAll("1");
        result = TWO.matcher(result).replaceAll("2");
        result = THREE.matcher(result).replaceAll("3");
        result = FOUR.matcher(result).replaceAll("4");
        result = FIVE.matcher(result).replaceAll("5");
        result = SIX.matcher(result).replaceAll("6");
        result = SEVEN.matcher(result).replaceAll("7");
        result = EIGHT.matcher(result).replaceAll("8");
        result = NINE.matcher(result).replaceAll("9");

        return result;
    }

    private String stripDiacritics(String text) {
        String handledD = text.replace('Đ', 'D').replace('đ', 'd');
        return Normalizer.normalize(handledD, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase();
    }
}
