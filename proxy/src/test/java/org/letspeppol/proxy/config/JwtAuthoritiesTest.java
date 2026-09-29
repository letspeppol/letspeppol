package org.letspeppol.proxy.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthoritiesTest {

    @Test
    void companyScopedUserTokenIsAKycUser() {
        assertThat(authorities(Map.of(
                SecurityConfig.UID, "5f0c7a52-8a8d-4f38-9f62-2d0a3b2f7c11",
                SecurityConfig.PEPPOL_ID, "0208:1234567890",
                SecurityConfig.ACCOUNT_TYPE, "ADMIN")))
                .contains(SecurityConfig.ROLE_KYC_USER)
                .doesNotContain(SecurityConfig.ROLE_SERVICE);
    }

    @Test
    void appServiceTokenIsAKycUserAndAService() {
        assertThat(authorities(Map.of(
                SecurityConfig.UID, "b095630d-1bf3-4250-bf9e-2d49e6ce505b",
                SecurityConfig.PEPPOL_ID, "0208:1029545627",
                SecurityConfig.ACCOUNT_TYPE, "APP",
                "scope", List.of("service"))))
                .contains(SecurityConfig.ROLE_KYC_USER, SecurityConfig.ROLE_SERVICE);
    }

    @Test
    void staffTokenWithoutCompanyContextIsNotAKycUser() {
        assertThat(authorities(Map.of(
                SecurityConfig.UID, "5f0c7a52-8a8d-4f38-9f62-2d0a3b2f7c11",
                "permissions", List.of("REVIEW_REGISTRATIONS"))))
                .doesNotContain(SecurityConfig.ROLE_KYC_USER, SecurityConfig.ROLE_SERVICE);
    }

    private static List<String> authorities(Map<String, Object> claims) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claims(values -> values.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        Collection<? extends GrantedAuthority> granted = new SecurityConfig().jwtAuthenticationConverter().convert(jwt).getAuthorities();
        return granted.stream().map(GrantedAuthority::getAuthority).toList();
    }
}
