package com.shareloop.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModerationReasonTest {
    @Test
    void describesKnownCodes() {
        assertThat(ModerationReason.describe("LEXICON_REVIEW")).isEqualTo("Có từ khoá cần xem xét");
        for (ModerationReason reason : ModerationReason.values()) {
            assertThat(ModerationReason.describe(reason.name())).isEqualTo(reason.description());
        }
    }

    @Test
    void preservesFreeTextAndUnknownOrLowercaseCodes() {
        assertThat(ModerationReason.describe("Ảnh cần kiểm tra")).isEqualTo("Ảnh cần kiểm tra");
        assertThat(ModerationReason.describe("lexicon_review")).isEqualTo("lexicon_review");
        assertThat(ModerationReason.describe("UNKNOWN")).isEqualTo("UNKNOWN");
    }

    @Test
    void acceptsNull() {
        assertThat(ModerationReason.describe(null)).isNull();
    }
}
