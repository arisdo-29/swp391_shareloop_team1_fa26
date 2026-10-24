package com.shareloop.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.shareloop.moderation.service.ContentModerationService;
import com.shareloop.setting.dto.AdminLexiconEntry;
import com.shareloop.setting.service.AdminLexiconService;
import com.shareloop.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** Kiểm tra lexicon seed cho vật phẩm cấm (FR-42, BR-A04, BR-A05) bằng database thật. */
@Transactional
class ModerationBannedGoodsIT extends IntegrationTest {
    private static final String CLEAN_DESCRIPTION = "Món còn tốt và dùng ổn định";

    @Autowired
    ContentModerationService moderation;

    @Autowired
    AdminLexiconService adminLexiconService;

    @Autowired
    JdbcClient jdbc;

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Bán sổ đỏ nhà đất|sổ đỏ",
                "Xe máy có biển số đẹp|biển số",
                "Tặng sim chính chủ|sim chính chủ",
                "CCCD cũ của bạn|cccd",
                "Hộ chiếu còn hạn|hộ chiếu",
                "Thẻ cào điện thoại|thẻ cào",
                "Voucher giảm giá|voucher",
                "Tiền mã hoá đang giữ|tiền mã hoá",
                "Tiền mã hóa đang giữ|tiền mã hóa",
                "Vàng miếng nhỏ|vàng miếng",
                "Ngoại tệ còn dư|ngoại tệ",
                "Tài khoản ngân hàng cũ|tài khoản ngân hàng",
                "Tài khoản game cấp cao|tài khoản game",
                "Thực phẩm tươi sống|thực phẩm tươi sống",
                "Thực phẩm chức năng còn hạn|thực phẩm chức năng",
                "Sữa công thức cho bé|sữa công thức",
                "Đồ ăn dặm cho bé|đồ ăn dặm",
                "Rượu vang đỏ|rượu",
                "Bia lon còn nguyên|bia",
                "Thuốc lá còn nguyên bao|thuốc lá"
            })
    void checkPost_whenTitleContainsBannedGoods_blocksWithLexiconReason(String title, String expectedTerm) {
        // given
        long categoryId = firstUnrestrictedCategoryId();

        // when
        ModerationResult result = moderation.checkPost(categoryId, title, CLEAN_DESCRIPTION);

        // then
        assertThat(result.status()).isEqualTo(ModerationStatus.BLOCK);
        assertThat(result.reasonCode()).isEqualTo(ModerationReason.LEXICON_BLOCK.name());
        assertThat(result.matchedTerms()).contains(expectedTerm);
    }

    @Test
    void checkPost_whenDescriptionContainsBannedGoods_blocksPost() {
        // given
        long categoryId = firstUnrestrictedCategoryId();

        // when
        ModerationResult result = moderation.checkPost(categoryId, "Món đồ cũ", "Mình có thẻ cào còn nguyên");

        // then
        assertThat(result.status()).isEqualTo(ModerationStatus.BLOCK);
        assertThat(result.matchedTerms()).contains("thẻ cào");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Ví đựng thẻ da còn mới",
                "Hộp đựng sữa cho bé",
                "Bình sữa cho bé",
                "Kệ rượu bằng gỗ",
                "Ly rượu pha lê",
                "Gạt tàn thuốc lá bằng sứ",
                "Khung biển số xe máy",
                "Bìa sách giáo khoa cũ",
                "Áo thun màu vàng",
                "Sữa rửa mặt còn mới",
                "Hộp đựng sữa bột có nắp",
                "Bộ đồ ăn dặm cho bé"
            })
    void checkPost_whenOrdinaryTitleOrException_passes(String title) {
        // given
        long categoryId = firstUnrestrictedCategoryId();

        // when
        ModerationResult result = moderation.checkPost(categoryId, title, CLEAN_DESCRIPTION);

        // then
        assertThat(result.status()).isEqualTo(ModerationStatus.PASS);
    }

    @Test
    void checkPost_whenAdminHidesSeededKeyword_noLongerBlocks() {
        // given: admin ẩn từ "thẻ cào" ở trang Từ khoá cấm
        long categoryId = firstUnrestrictedCategoryId();
        AdminLexiconEntry entry = adminLexiconService.listAll().stream()
                .filter(candidate -> "thẻ cào".equals(candidate.keyword()))
                .findFirst()
                .orElseThrow();
        assertThat(moderation
                        .checkPost(categoryId, "Thẻ cào điện thoại", CLEAN_DESCRIPTION)
                        .status())
                .isEqualTo(ModerationStatus.BLOCK);

        // when
        adminLexiconService.setActive(entry.id(), false, null);

        // then
        assertThat(moderation
                        .checkPost(categoryId, "Thẻ cào điện thoại", CLEAN_DESCRIPTION)
                        .status())
                .isEqualTo(ModerationStatus.PASS);
    }

    private long firstUnrestrictedCategoryId() {
        return jdbc.sql("SELECT id FROM item_categories WHERE is_active AND NOT is_restricted ORDER BY id LIMIT 1")
                .query(Long.class)
                .single();
    }
}
