package org.letspeppol.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void companyUserTokenIsAKycUser() {
        assertThat(authorities(jwt("ADMIN", "0208:0123456789"))).containsExactly(SecurityConfig.ROLE_KYC_USER);
    }

    @Test
    void appServiceTokenIsNotAKycUserEvenWithASendingCompany() {
        assertThat(authorities(jwt("APP", "0208:1029545627"))).isEmpty();
    }

    @Test
    void tokenWithoutCompanyIsNotAKycUser() {
        assertThat(authorities(jwt("ADMIN", null))).isEmpty();
    }

    private static List<String> authorities(Jwt jwt) {
        return new SecurityConfig().jwtAuthenticationConverter().convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private static Jwt jwt(String accountType, String peppolId) {
        Jwt.Builder jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("subject")
                .claim(SecurityConfig.UID, "b095630d-1bf3-4250-bf9e-2d49e6ce505b")
                .claim(SecurityConfig.ACCOUNT_TYPE, accountType);
        if (peppolId != null) {
            jwt.claim(SecurityConfig.PEPPOL_ID, peppolId);
        }
        return jwt.build();
    }
}
