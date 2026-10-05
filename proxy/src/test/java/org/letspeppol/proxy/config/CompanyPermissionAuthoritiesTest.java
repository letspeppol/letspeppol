package org.letspeppol.proxy.config;

import org.junit.jupiter.api.Test;
import org.letspeppol.proxy.model.AccountType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyPermissionAuthoritiesTest {

    private static final String[] ALL_PERMISSIONS = {
            "INVOICE_READ", "INVOICE_DRAFT", "INVOICE_SEND", "INVOICE_STATUS",
            "INVOICE_EXPORT", "PARTNER_MANAGE", "PRODUCT_MANAGE", "COMPANY_SETTINGS"
    };

    @Test
    void userGetsOnlyTheBitsInItsMask() {
        assertThat(authorities(userJwt(AccountType.USER).claim(SecurityConfig.PERMISSION_MASK, 3).build()))
                .contains(SecurityConfig.ROLE_KYC_USER, "INVOICE_READ", "INVOICE_DRAFT")
                .doesNotContain("INVOICE_SEND", "INVOICE_STATUS", "INVOICE_EXPORT", SecurityConfig.ROLE_SERVICE);
    }

    @Test
    void userWithoutMaskClaimGetsNoPermissions() {
        assertThat(authorities(userJwt(AccountType.USER).build()))
                .contains(SecurityConfig.ROLE_KYC_USER)
                .doesNotContain(ALL_PERMISSIONS);
    }

    @Test
    void legacyRestrictedTypesWithoutMaskClaimKeepTheirFixedPermissions() {
        assertThat(authorities(userJwt(AccountType.USER_DRAFT).build()))
                .contains("INVOICE_READ", "INVOICE_DRAFT")
                .doesNotContain("INVOICE_SEND", "INVOICE_STATUS", "INVOICE_EXPORT", "PARTNER_MANAGE", "PRODUCT_MANAGE", "COMPANY_SETTINGS");
        assertThat(authorities(userJwt(AccountType.USER_READ).build()))
                .contains("INVOICE_READ")
                .doesNotContain("INVOICE_DRAFT", "INVOICE_SEND", "INVOICE_STATUS", "INVOICE_EXPORT", "PARTNER_MANAGE", "PRODUCT_MANAGE", "COMPANY_SETTINGS");
    }

    @Test
    void userWithEmptyMaskGetsNoPermissions() {
        assertThat(authorities(userJwt(AccountType.USER).claim(SecurityConfig.PERMISSION_MASK, 0).build()))
                .contains(SecurityConfig.ROLE_KYC_USER)
                .doesNotContain(ALL_PERMISSIONS);
    }

    @Test
    void adminTokenMintedBeforeTheClaimExistedKeepsFullAccess() {
        assertThat(authorities(userJwt(AccountType.ADMIN).build()))
                .contains(SecurityConfig.ROLE_KYC_USER)
                .contains(ALL_PERMISSIONS);
    }

    @Test
    void maskIsReadFromLongClaims() {
        assertThat(authorities(userJwt(AccountType.USER).claim(SecurityConfig.PERMISSION_MASK, 5L).build()))
                .contains("INVOICE_READ", "INVOICE_SEND")
                .doesNotContain("INVOICE_DRAFT");
    }

    @Test
    void unknownBitsAreIgnored() {
        assertThat(authorities(userJwt(AccountType.USER).claim(SecurityConfig.PERMISSION_MASK, 256 + 1).build()))
                .contains("INVOICE_READ")
                .doesNotContain("INVOICE_DRAFT", "COMPANY_SETTINGS");
    }

    @Test
    void appServiceTokenKeepsServiceAndFullDocumentAccess() {
        Jwt serviceJwt = userJwt(AccountType.APP).claim("scope", List.of("service")).build();

        assertThat(authorities(serviceJwt))
                .contains(SecurityConfig.ROLE_SERVICE, SecurityConfig.ROLE_KYC_USER)
                .contains(ALL_PERMISSIONS);
    }

    @Test
    void tokenWithoutUidGetsNothing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim(SecurityConfig.PEPPOL_ID, "0208:0123456789")
                .claim(SecurityConfig.PERMISSION_MASK, 255)
                .build();

        assertThat(authorities(jwt))
                .doesNotContain(SecurityConfig.ROLE_KYC_USER, SecurityConfig.ROLE_SERVICE)
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

    private static Jwt.Builder userJwt(AccountType accountType) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim(SecurityConfig.ACCOUNT_TYPE, accountType.name())
                .claim(SecurityConfig.PEPPOL_ID, "0208:0123456789")
                .claim(SecurityConfig.UID, UUID.randomUUID().toString());
    }
}
