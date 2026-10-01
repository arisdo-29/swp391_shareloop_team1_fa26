package com.shareloop.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shareloop.auth.service.JwtService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class JwtConfigTest {

    private static final String SECRET_32 = "0123456789abcdef0123456789abcdef";

    private final Clock clock = Clock.fixed(Instant.now().truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC);

    @Test
    void secretShorterThan32BytesIsRejected() {
        assertThatThrownBy(() -> JwtConfig.buildKey("0123456789abcdef0123456789abcde"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtConfig.buildKey("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JwtConfig.buildKey(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void secretOf32BytesIsAccepted() {
        assertThat(JwtConfig.buildKey(SECRET_32).getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    void issuedTokenCarriesSubjectRolesAndExpiry() {
        var config = new JwtConfig();
        var key = JwtConfig.buildKey(SECRET_32);
        var service = new JwtService(config.jwtEncoder(key), clock, 120);

        var jwt = config.jwtDecoder(key).decode(service.issue(5, true));

        assertThat(jwt.getSubject()).isEqualTo("5");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER", "ADMIN");
        assertThat(jwt.getIssuedAt()).isEqualTo(clock.instant());
        assertThat(jwt.getExpiresAt()).isEqualTo(clock.instant().plusSeconds(120 * 60));
    }
}
