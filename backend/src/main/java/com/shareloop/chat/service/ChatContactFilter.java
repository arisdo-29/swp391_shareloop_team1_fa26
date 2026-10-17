package com.shareloop.chat.service;

import com.shareloop.chat.ChatErrorCode;
import com.shareloop.common.exception.BusinessException;
import com.shareloop.moderation.ContactInfoDetector;
import com.shareloop.moderation.ContactInfoDetector.Match;
import com.shareloop.setting.dto.LexiconEntry;
import com.shareloop.setting.dto.LexiconMatch;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.setting.service.LexiconMatcher;
import java.text.Normalizer;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Bộ lọc thông tin liên hệ và từ khoá cấm mức BLOCK tầng 1 cho module chat (FR-72, FR-74, FR-78; BR-C01, BR-A10).
 *
 * <p>Tái sử dụng {@link ContactInfoDetector} và bổ sung phần riêng của chat:
 * nhận diện các chữ số viết bằng chữ tiếng Việt ("không", "một", "hai"...) kể cả viết
 * cách quãng hoặc lẫn lộn chữ số, sau đó áp bộ phát hiện của {@link ContactInfoDetector}.
 * Đồng thời tích hợp kiểm tra từ khoá cấm mức BLOCK từ {@link LexiconMatcher} (FR-78, BR-A10).
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

    private final ConfigService configService;
    private final LexiconMatcher lexiconMatcher;

    public ChatContactFilter() {
        this(null, null);
    }

    @Autowired
    public ChatContactFilter(
            @Autowired(required = false) ConfigService configService,
            @Autowired(required = false) LexiconMatcher lexiconMatcher) {
        this.configService = configService;
        this.lexiconMatcher = lexiconMatcher != null ? lexiconMatcher : new LexiconMatcher();
    }

    /**
     * Kiểm tra nội dung tin nhắn có vi phạm từ khoá cấm mức BLOCK (FR-78) hoặc thông tin liên hệ (FR-72) hay không.
     *
     * @param content          nội dung tin nhắn
     * @param checkContactInfo có kích hoạt kiểm tra thông tin liên hệ không (chỉ áp dụng trước LogisticsConfirmed)
     * @return Optional chứa mô tả lý do vi phạm nếu có; rỗng nếu tin nhắn hợp lệ
     */
    public Optional<String> checkViolation(String content, boolean checkContactInfo) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }

        // 1. Kiểm tra từ khoá cấm mức BLOCK (FR-78, BR-A10)
        if (configService != null) {
            List<LexiconEntry> lexicon = configService.lexicon();
            if (lexicon != null && !lexicon.isEmpty()) {
                List<LexiconEntry> applicable = lexicon.stream()
                        .filter(e -> e != null
                                && (e.scope() == null
                                        || "CHAT".equalsIgnoreCase(e.scope())
                                        || "BOTH".equalsIgnoreCase(e.scope())))
                        .toList();
                List<LexiconMatch> matches = lexiconMatcher.match(content, applicable);
                Optional<LexiconMatch> blockMatch = matches.stream()
                        .filter(m -> "BLOCK".equalsIgnoreCase(m.level()))
                        .findFirst();
                if (blockMatch.isPresent()) {
                    return Optional.of("từ khoá cấm (" + blockMatch.get().keyword() + ")");
                }
            }
        }

        // 2. Kiểm tra thông tin liên hệ nếu trước LogisticsConfirmed (FR-72, BR-C01)
        if (checkContactInfo) {
            List<Match> contactMatches = detectContactInfo(content);
            if (!contactMatches.isEmpty()) {
                Match first = contactMatches.get(0);
                String detail =
                        switch (first.type()) {
                            case PHONE -> "số điện thoại (" + first.matched() + ")";
                            case EMAIL -> "địa chỉ email (" + first.matched() + ")";
                            case URL -> "liên kết web (" + first.matched() + ")";
                        };
                return Optional.of("thông tin liên hệ: " + detail);
            }
        }

        return Optional.empty();
    }

    /**
     * Kiểm tra nội dung tin nhắn. Nếu phát hiện vi phạm thì ném {@link BusinessException}
     * kèm lý do chi tiết theo FR-74.
     *
     * @param content nội dung tin nhắn
     * @throws BusinessException nếu phát hiện số điện thoại, email, URL hoặc từ khoá cấm mức BLOCK
     */
    public void validateMessage(String content) {
        Optional<String> violation = checkViolation(content, true);
        if (violation.isPresent()) {
            throw new BusinessException(
                    ChatErrorCode.CHAT_MESSAGE_BLOCKED, "Tin nhắn bị chặn do chứa " + violation.get() + ".");
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
