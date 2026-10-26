package com.shareloop.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PostLimitPropertiesTest {

    @Test
    void canBypassPostLimit_whenEmailInList_returnsTrue() {
        // given
        PostLimitProperties properties = new PostLimitProperties();
        properties.setPostLimitBypassEmails(List.of("tester1@shareloop.test"));

        // when
        boolean result = properties.canBypassPostLimit("tester1@shareloop.test");

        // then
        assertThat(result).isTrue();
    }

    @Test
    void canBypassPostLimit_whenCaseDiffers_returnsTrue() {
        // given
        PostLimitProperties properties = new PostLimitProperties();
        properties.setPostLimitBypassEmails(List.of("tester1@shareloop.test"));

        // when
        boolean result = properties.canBypassPostLimit("Tester1@ShareLoop.TEST");

        // then
        assertThat(result).isTrue();
    }

    @Test
    void canBypassPostLimit_whenEmailNotInList_returnsFalse() {
        // given
        PostLimitProperties properties = new PostLimitProperties();
        properties.setPostLimitBypassEmails(List.of("tester1@shareloop.test"));

        // when
        boolean result = properties.canBypassPostLimit("user@shareloop.com");

        // then
        assertThat(result).isFalse();
    }

    @Test
    void canBypassPostLimit_whenEmailIsNull_returnsFalse() {
        // given
        PostLimitProperties properties = new PostLimitProperties();
        properties.setPostLimitBypassEmails(List.of("tester1@shareloop.test"));

        // when
        boolean result = properties.canBypassPostLimit(null);

        // then
        assertThat(result).isFalse();
    }

    @Test
    void canBypassPostLimit_whenListIsEmptyByDefault_returnsFalse() {
        // given
        PostLimitProperties properties = new PostLimitProperties();

        // when
        boolean result = properties.canBypassPostLimit("tester1@shareloop.test");

        // then
        assertThat(result).isFalse();
    }
}
