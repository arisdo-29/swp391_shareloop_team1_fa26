package com.shareloop.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Tạo access token HS256. Đăng ký, đăng nhập dùng service này ở sprint sau. */
@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration ttl;

    public JwtService(JwtEncoder jwtEncoder, Clock clock, @Value("${app.jwt.ttl-minutes}") long ttlMinutes) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public String issue(long userId, boolean admin) {
        Instant now = clock.instant();
        List<String> roles = admin ? List.of("USER", "ADMIN") : List.of("USER");
        var claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .claim("roles", roles)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
