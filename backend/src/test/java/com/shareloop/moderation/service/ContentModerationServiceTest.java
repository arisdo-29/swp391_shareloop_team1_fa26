package com.shareloop.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shareloop.catalog.dto.CategoryResponse;
import com.shareloop.catalog.service.CategoryService;
import com.shareloop.moderation.ModerationStatus;
import com.shareloop.setting.dto.LexiconEntry;
import com.shareloop.setting.service.ConfigService;
import com.shareloop.setting.service.LexiconMatcher;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContentModerationServiceTest {

    private final CategoryService categoryService = mock(CategoryService.class);
    private final ConfigService configService = mock(ConfigService.class);
    private final ContentModerationService service =
            new ContentModerationService(categoryService, configService, new LexiconMatcher());

    @BeforeEach
    void setUp() {
        when(categoryService.getById(1L)).thenReturn(new CategoryResponse(1L, "Books", null, false, true, 1));
        when(configService.lexicon()).thenReturn(List.of());
    }

    @Test
    void blocksRestrictedCategoryBeforeCheckingContent() {
        when(categoryService.getById(1L)).thenReturn(new CategoryResponse(1L, "Restricted", null, true, false, 1));

        var result = service.checkPost(1L, "Clean title", "Clean description");

        assertThat(result.status()).isEqualTo(ModerationStatus.BLOCK);
        assertThat(result.reasonCode()).isEqualTo("RESTRICTED_CATEGORY");
        verifyNoInteractions(configService);
    }

    @Test
    void blocksContactInformationInTitleOrDescription() {
        var result = service.checkPost(1L, "Used phone", "Liên hệ 0912345678 để trao đổi");

        assertThat(result.status()).isEqualTo(ModerationStatus.BLOCK);
        assertThat(result.reasonCode()).isEqualTo("CONTACT_INFO");
        assertThat(result.matchedTerms()).contains("PHONE");
    }

    @Test
    void blocksAnExactBlockLexiconMatch() {
        when(configService.lexicon()).thenReturn(List.of(rule("cần sa", "BLOCK", "PHRASE", List.of())));

        var result = service.checkPost(1L, "Cần sa", "Mô tả hợp lệ");

        assertThat(result.status()).isEqualTo(ModerationStatus.BLOCK);
        assertThat(result.reasonCode()).isEqualTo("LEXICON_BLOCK");
        assertThat(result.matchedTerms()).containsExactly("cần sa");
    }

    @Test
    void returnsReviewForAReviewSeverityMatch() {
        when(configService.lexicon()).thenReturn(List.of(rule("zalo", "REVIEW", "WORD", List.of())));

        var result = service.checkPost(1L, "ZALO", "Mô tả hợp lệ");

        assertThat(result.status()).isEqualTo(ModerationStatus.REVIEW);
        assertThat(result.reasonCode()).isEqualTo("LEXICON_REVIEW");
    }

    @Test
    void lowersAnAccentlessOnlyBlockMatchToReview() {
        when(configService.lexicon()).thenReturn(List.of(rule("cần sa", "BLOCK", "PHRASE", List.of())));

        var result = service.checkPost(1L, "Can sa", "Mô tả hợp lệ");

        assertThat(result.status()).isEqualTo(ModerationStatus.REVIEW);
        assertThat(result.reasonCode()).isEqualTo("LEXICON_REVIEW");
        assertThat(result.matchedTerms()).containsExactly("cần sa");
    }

    @Test
    void ignoresLexiconMatchesInsideConfiguredExceptions() {
        when(configService.lexicon()).thenReturn(List.of(rule("súng", "BLOCK", "WORD", List.of("súng nước"))));

        var result = service.checkPost(1L, "Súng nước đồ chơi", "Mô tả hợp lệ");

        assertThat(result.status()).isEqualTo(ModerationStatus.PASS);
    }

    @Test
    void matchesCaseInsensitivelyAndCollapsesExtraWhitespace() {
        when(configService.lexicon()).thenReturn(List.of(rule("cần sa", "BLOCK", "PHRASE", List.of())));

        var result = service.checkPost(1L, "  CẦN   SA  ", "Mô tả hợp lệ");

        assertThat(result.status()).isEqualTo(ModerationStatus.BLOCK);
    }

    @Test
    void wordMatchDoesNotBlockAnInnocentWordContainingTheTerm() {
        when(configService.lexicon()).thenReturn(List.of(rule("cunt", "BLOCK", "WORD", List.of())));

        var result = service.checkPost(1L, "Scunthorpe United shirt", "Mô tả hợp lệ");

        assertThat(result.status()).isEqualTo(ModerationStatus.PASS);
    }

    @Test
    void returnsPassWhenContentIsClean() {
        var result = service.checkPost(1L, "Áo khoác", "Áo còn tốt, dùng ít lần");

        assertThat(result.status()).isEqualTo(ModerationStatus.PASS);
        assertThat(result.matchedTerms()).isEmpty();
    }

    @Test
    void failsSafeToReviewForInvalidInput() {
        var result = service.checkPost(1L, null, "Mô tả hợp lệ");

        assertThat(result.status()).isEqualTo(ModerationStatus.REVIEW);
        assertThat(result.reasonCode()).isEqualTo("INVALID_INPUT");
    }

    @Test
    void failsSafeToReviewWhenLexiconLookupThrows() {
        when(configService.lexicon()).thenThrow(new IllegalStateException("database unavailable"));

        var result = service.checkPost(1L, "Clean title", "Clean description");

        assertThat(result.status()).isEqualTo(ModerationStatus.REVIEW);
        assertThat(result.reasonCode()).isEqualTo("MODERATION_CHECK_FAILED");
    }

    private static LexiconEntry rule(String term, String severity, String matchMode, List<String> exceptions) {
        return new LexiconEntry(term, "TEST", severity, matchMode, exceptions, "POST", 1);
    }
}
