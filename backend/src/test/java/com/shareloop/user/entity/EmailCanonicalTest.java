package com.shareloop.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailCanonicalTest {

    @Test
    void of_whenGmailWithPlusTag_dropsTag() {
        // given, when, then
        assertThat(EmailCanonical.of("an+1@gmail.com")).isEqualTo("an@gmail.com");
    }

    @Test
    void of_whenGmailWithDots_dropsDots() {
        assertThat(EmailCanonical.of("a.n@gmail.com")).isEqualTo("an@gmail.com");
    }

    @Test
    void of_whenGooglemail_changesDomainToGmail() {
        assertThat(EmailCanonical.of("A.N+x@GoogleMail.com")).isEqualTo("an@gmail.com");
    }

    @Test
    void of_whenOtherDomain_keepsDotsAndPlus() {
        assertThat(EmailCanonical.of("a.n+1@shareloop.local")).isEqualTo("a.n+1@shareloop.local");
    }

    @Test
    void of_whenSpacesAndUpperCase_trimsAndLowers() {
        assertThat(EmailCanonical.of("  An@ShareLoop.Local ")).isEqualTo("an@shareloop.local");
    }
}
