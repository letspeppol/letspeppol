package org.letspeppol.app.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private static final String[] ALL_PERMISSIONS = {
            "INVOICE_READ", "INVOICE_DRAFT", "INVOICE_SEND", "INVOICE_STATUS",
            "INVOICE_EXPORT", "PARTNER_MANAGE", "PRODUCT_MANAGE", "COMPANY_SETTINGS"
    };

    @Test
    void adminTokenMintedBeforeTheClaimExistedKeepsFullAccess() {
        assertThat(authorities(jwt("ADMIN", "0208:0123456789", null)))
                .contains(SecurityConfig.ROLE_KYC_USER, SecurityConfig.ROLE_COMPANY_ADMIN)
                .contains(ALL_PERMISSIONS);
    }

    @Test
    void userGetsOnlyTheBitsInItsMask() {
        assertThat(authorities(jwt("USER", "0208:0123456789", 3)))
                .contains(SecurityConfig.ROLE_KYC_USER, "INVOICE_READ", "INVOICE_DRAFT")
                .doesNotContain(SecurityConfig.ROLE_COMPANY_ADMIN, "INVOICE_SEND", "INVOICE_STATUS",
                        "INVOICE_EXPORT", "PARTNER_MANAGE", "PRODUCT_MANAGE", "COMPANY_SETTINGS");
    }

    @Test
    void userWithEveryBitIsStillNotAnAdmin() {
        assertThat(authorities(jwt("USER", "0208:0123456789", 255)))
                .contains(ALL_PERMISSIONS)
                .doesNotContain(SecurityConfig.ROLE_COMPANY_ADMIN);
    }

    @Test
    void userWithoutMaskClaimIsAMemberWithoutPermissions() {
        assertThat(authorities(jwt("USER", "0208:0123456789", null)))
                .contains(SecurityConfig.ROLE_KYC_USER)
                .doesNotContain(ALL_PERMISSIONS)
                .doesNotContain(SecurityConfig.ROLE_COMPANY_ADMIN);
    }

    @Test
    void affiliateKeepsFullAccessButIsNotAnAdmin() {
        assertThat(authorities(jwt("AFFILIATE", "0208:0123456789", 255)))
                .contains(SecurityConfig.ROLE_KYC_USER)
                .contains(ALL_PERMISSIONS)
                .doesNotContain(SecurityConfig.ROLE_COMPANY_ADMIN);
    }

    @Test
    void appServiceTokenIsNotAKycUserEvenWithASendingCompany() {
        assertThat(authorities(jwt("APP", "0208:1029545627", null)))
                .doesNotContain(SecurityConfig.ROLE_KYC_USER, SecurityConfig.ROLE_COMPANY_ADMIN)
                .doesNotContain(ALL_PERMISSIONS);
    }

    @Test
    void tokenWithoutCompanyIsNotAKycUser() {
        assertThat(authorities(jwt("ADMIN", null, 255)))
                .doesNotContain(SecurityConfig.ROLE_KYC_USER, SecurityConfig.ROLE_COMPANY_ADMIN)
                .doesNotContain(ALL_PERMISSIONS);
    }

    @Test
    void methodSecurityIsEnabled() {
        assertThat(SecurityConfig.class.isAnnotationPresent(EnableMethodSecurity.class)).isTrue();
    }

    private static List<String> authorities(Jwt jwt) {
        return new SecurityConfig().jwtAuthenticationConverter().convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private static Jwt jwt(String accountType, String peppolId, Integer permissionMask) {
        Jwt.Builder jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("subject")
                .claim(SecurityConfig.UID, "b095630d-1bf3-4250-bf9e-2d49e6ce505b")
                .claim(SecurityConfig.ACCOUNT_TYPE, accountType);
        if (peppolId != null) {
            jwt.claim(SecurityConfig.PEPPOL_ID, peppolId);
        }
        if (permissionMask != null) {
            jwt.claim(SecurityConfig.PERMISSION_MASK, permissionMask);
        }
        return jwt.build();
    }
}
