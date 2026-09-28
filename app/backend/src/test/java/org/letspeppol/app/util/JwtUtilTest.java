package org.letspeppol.app.util;

import org.junit.jupiter.api.Test;
import org.letspeppol.app.config.SecurityConfig;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    @Test
    void readsCompanyLastUpdatedWithSubSecondPrecision() {
        Instant timestamp = Instant.parse("2026-09-06T12:00:00.123456Z");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user")
                .claim(SecurityConfig.COMPANY_LAST_UPDATED, timestamp.toString())
                .build();

        assertThat(JwtUtil.getCompanyLastUpdated(jwt)).isEqualTo(timestamp);
    }

    @Test
    void missingCompanyLastUpdatedSupportsTokensIssuedBeforeDeployment() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user")
                .build();

        assertThat(JwtUtil.getCompanyLastUpdated(jwt)).isNull();
    }
}
