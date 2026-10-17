package com.shareloop.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit test đọc danh sách tên miền email dùng một lần; dùng file thật trong src/main/resources. */
class DisposableEmailDomainsTest {

    private final DisposableEmailDomains disposableEmailDomains = new DisposableEmailDomains();

    @Test
    void isDisposable_whenDomainInListWithDifferentCase_returnsTrue() {
        // given
        String email = "Spammer@MAILINATOR.com";

        // when
        boolean disposable = disposableEmailDomains.isDisposable(email);

        // then
        assertThat(disposable).isTrue();
    }

    @Test
    void isDisposable_whenNormalDomain_returnsFalse() {
        // given
        String email = "an@gmail.com";

        // when
        boolean disposable = disposableEmailDomains.isDisposable(email);

        // then
        assertThat(disposable).isFalse();
    }

    @Test
    void parseDomains_skipsCommentAndBlankLinesAndLowercases() {
        // given
        String content = "# chú thích\n\n  YopMail.com  \r\nmailinator.com\n";

        // when
        Set<String> domains = DisposableEmailDomains.parseDomains(content);

        // then
        assertThat(domains).containsExactlyInAnyOrder("yopmail.com", "mailinator.com");
    }
}
