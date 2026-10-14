package com.shareloop.moderation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phát hiện thông tin liên hệ (số điện thoại, email, URL) trong văn bản.
 *
 * <p>Dùng chung cho:
 * <ul>
 *   <li>Kiểm duyệt bài đăng R1 (module {@code moderation}).</li>
 *   <li>Lọc chat tầng 1 (module {@code chat}) — gọi thêm {@link #detectInNormalized} sau khi
 *       chuẩn hoá chữ số viết bằng chữ (hai, ba, bốn… → 2, 3, 4…).</li>
 * </ul>
 *
 * <p><b>Quy trình:</b>
 * <ol>
 *   <li>Chuẩn hoá văn bản: bỏ dấu tiếng Việt, hạ chữ thường, bỏ ký tự ngăn cách chen giữa số
 *       (dấu chấm, gạch ngang, khoảng trắng khi nằm giữa hai chữ số).</li>
 *   <li>Áp regex bắt số điện thoại di động VN, email, URL.</li>
 *   <li>Trả danh sách {@link Match} — mỗi phần tử gồm loại và đoạn văn bản gốc khớp.</li>
 * </ol>
 *
 * <p><b>Sử dụng (BE3 — module chat):</b>
 * <pre>{@code
 * List<ContactInfoDetector.Match> hits = ContactInfoDetector.detect(messageText);
 * if (!hits.isEmpty()) {
 *     // chặn / cảnh báo
 * }
 * }</pre>
 *
 * <p><b>Chú ý false-positive đã xử lý:</b> mã sản phẩm (SL-12345), số tiền (1.000.000),
 * năm (2024), mã đơn hàng, không bị nhận nhầm là số điện thoại hay URL.
 *
 * <p>Người sở hữu: BE3 (@dwargon73-sketch). FR-41, FR-72; BR-C01.
 */
public final class ContactInfoDetector {

    private ContactInfoDetector() {}

    // ---------------------------------------------------------------------------
    // Kiểu kết quả
    // ---------------------------------------------------------------------------

    /** Loại thông tin liên hệ được phát hiện. */
    public enum ContactType {
        /** Số điện thoại di động Việt Nam (đầu 03x, 05x, 07x, 08x, 09x). */
        PHONE,
        /** Địa chỉ email. */
        EMAIL,
        /** URL (http/https/www). */
        URL
    }

    /**
     * Một phát hiện cụ thể trong văn bản.
     *
     * @param type    loại thông tin liên hệ
     * @param matched đoạn văn bản gốc (chưa chuẩn hoá) tương ứng với vị trí khớp
     */
    public record Match(ContactType type, String matched) {}

    // ---------------------------------------------------------------------------
    // Regex patterns
    // ---------------------------------------------------------------------------

    /**
     * Số điện thoại di động VN: đầu số 03x / 05x / 07x / 08x / 09x, tổng đúng 10 chữ số.
     * Đầu số gồm 3 chữ số, theo sau là đúng 7 chữ số.
     * Áp dụng trên chuỗi đã chuẩn hoá (đã gộp các ký tự ngăn cách giữa các chữ số).
     * Dùng (?<!\d) và (?!\d) để tránh bắt nhầm mã đơn hàng, mã vạch (>= 11 chữ số).
     */
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)" + "0(3[2-9]|5[25689]|7[06-9]|8[0-9]|9[0-9])" + "\\d{7}" + "(?!\\d)");

    /** Email chuẩn. */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");

    /**
     * URL: bắt đầu bằng http://, https://, hoặc www. (bắt cả viết tắt không có scheme).
     * Không bắt các cụm "www" đứng một mình không có TLD.
     */
    private static final Pattern URL_PATTERN = Pattern.compile("(?:https?://|www\\.)"
            + "[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+"
            + "(?:\\.[a-zA-Z]{2,}[a-zA-Z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)");

    // ---------------------------------------------------------------------------
    // API công khai
    // ---------------------------------------------------------------------------

    /**
     * Phát hiện thông tin liên hệ trong văn bản đầu vào.
     *
     * <p>Văn bản được chuẩn hoá nội bộ (bỏ dấu, hạ thường, gộp ký tự ngăn cách giữa số)
     * trước khi áp regex, nhưng kết quả {@link Match#matched()} luôn là đoạn văn bản
     * <em>gốc</em> (nguyên dấu, chưa thay đổi).
     *
     * @param text văn bản cần kiểm tra (null hoặc rỗng → danh sách rỗng)
     * @return danh sách các phát hiện, có thể rỗng; không null
     */
    public static List<Match> detect(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        NormalizedResult norm = normalizeWithIndices(text);
        List<Match> results = new ArrayList<>();

        addMatchesWithIndices(results, URL_PATTERN, norm, text, ContactType.URL);
        addMatchesWithIndices(results, EMAIL_PATTERN, norm, text, ContactType.EMAIL);
        addMatchesWithIndices(results, PHONE_PATTERN, norm, text, ContactType.PHONE);

        return List.copyOf(results);
    }

    /**
     * Phát hiện trên văn bản đã chuẩn hoá sẵn (dành cho module chat tầng 1 muốn tự chuẩn hoá
     * thêm số viết bằng chữ trước khi gọi).
     *
     * @param original   văn bản gốc (dùng để trích đoạn khớp có thể đọc được)
     * @param normalized văn bản sau khi đã bỏ dấu, hạ thường, gộp ký tự ngăn cách
     * @return danh sách các phát hiện, có thể rỗng; không null
     */
    public static List<Match> detectInNormalized(String original, String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return List.of();
        }
        List<Match> results = new ArrayList<>();

        addMatchesSimple(results, URL_PATTERN, normalized, original, ContactType.URL);
        addMatchesSimple(results, EMAIL_PATTERN, normalized, original, ContactType.EMAIL);
        addMatchesSimple(results, PHONE_PATTERN, normalized, original, ContactType.PHONE);

        return List.copyOf(results);
    }

    /**
     * Chuẩn hoá văn bản để dễ khớp regex hơn:
     * <ol>
     *   <li>Bỏ dấu tiếng Việt (NFD → xoá combining marks).</li>
     *   <li>Hạ chữ thường.</li>
     *   <li>Gộp ký tự ngăn cách (dấu cách, chấm, gạch) chen giữa hai chữ số:
     *       "091 234 5678" → "0912345678".</li>
     * </ol>
     *
     * <p>Phương thức này là {@code public} để module {@code chat} tự thêm bước chuyển
     * số viết bằng chữ (một, hai, ba… → 1, 2, 3…) trước khi gọi
     * {@link #detectInNormalized(String, String)}.
     *
     * @param text văn bản gốc
     * @return văn bản sau chuẩn hoá
     */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return normalizeWithIndices(text).normalizedText();
    }

    // ---------------------------------------------------------------------------
    // Helpers nội bộ
    // ---------------------------------------------------------------------------

    private record NormalizedResult(String normalizedText, int[] sourceIndices) {}

    private static NormalizedResult normalizeWithIndices(String text) {
        int len = text.length();
        StringBuilder stage1 = new StringBuilder(len);
        int[] stage1Indices = new int[len * 2];
        int s1Count = 0;

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == 'Đ') {
                c = 'd';
            } else if (c == 'đ') {
                c = 'd';
            }
            String nfd = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            for (int k = 0; k < nfd.length(); k++) {
                char ch = nfd.charAt(k);
                // Bỏ combining diacritical marks
                if (Character.getType(ch) != Character.NON_SPACING_MARK) {
                    stage1.append(Character.toLowerCase(ch));
                    stage1Indices[s1Count++] = i;
                }
            }
        }

        // Bước 2: loại bỏ ký tự ngăn cách (dấu cách, chấm, gạch) nằm ngay giữa hai chữ số
        StringBuilder stage2 = new StringBuilder(s1Count);
        int[] stage2Indices = new int[s1Count];
        int s2Count = 0;

        for (int i = 0; i < stage1.length(); i++) {
            char curr = stage1.charAt(i);
            boolean isSeparatorBetweenDigits = (curr == ' ' || curr == '.' || curr == '-')
                    && i > 0
                    && i < stage1.length() - 1
                    && Character.isDigit(stage1.charAt(i - 1))
                    && Character.isDigit(stage1.charAt(i + 1));

            if (!isSeparatorBetweenDigits) {
                stage2.append(curr);
                stage2Indices[s2Count++] = stage1Indices[i];
            }
        }

        int[] finalIndices = new int[s2Count];
        System.arraycopy(stage2Indices, 0, finalIndices, 0, s2Count);
        return new NormalizedResult(stage2.toString(), finalIndices);
    }

    private static void addMatchesWithIndices(
            List<Match> results, Pattern pattern, NormalizedResult norm, String original, ContactType type) {
        Matcher matcher = pattern.matcher(norm.normalizedText());
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            int origStart = norm.sourceIndices()[start];
            int origEnd = norm.sourceIndices()[end - 1] + 1;
            String matched = safeSubstring(original, origStart, origEnd);
            results.add(new Match(type, matched));
        }
    }

    private static void addMatchesSimple(
            List<Match> results, Pattern pattern, String normalized, String original, ContactType type) {
        Matcher matcher = pattern.matcher(normalized);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            String matched;
            if (original != null && original.length() == normalized.length()) {
                matched = safeSubstring(original, start, end);
            } else {
                matched = matcher.group();
            }
            results.add(new Match(type, matched));
        }
    }

    private static String safeSubstring(String text, int start, int end) {
        if (text == null || start < 0 || end > text.length() || start > end) {
            return text == null ? "" : text;
        }
        return text.substring(start, end);
    }
}
