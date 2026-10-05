package org.letspeppol.app.util;

import org.junit.jupiter.api.Test;
import org.letspeppol.app.config.SecurityConfig;
import org.letspeppol.app.dto.CompanyPermission;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void readsThePermissionMaskFromIntegerAndLongClaims() {
        assertThat(JwtUtil.getPermissionMask(jwt("USER", 5))).isEqualTo(5);
        assertThat(JwtUtil.getPermissionMask(jwt("USER", 5L))).isEqualTo(5);
    }

    @Test
    void ignoresBitsOutsideTheKnownPermissions() {
        assertThat(JwtUtil.getPermissionMask(jwt("USER", 256 + 4))).isEqualTo(4);
    }

    @Test
    void missingMaskMeansNothingForAUserAndEverythingForOlderTokens() {
        assertThat(JwtUtil.getPermissionMask(jwt("USER", null))).isZero();
        assertThat(JwtUtil.getPermissionMask(jwt("ADMIN", null))).isEqualTo(CompanyPermission.ALL);
        assertThat(JwtUtil.getPermissionMask(jwt("AFFILIATE", null))).isEqualTo(CompanyPermission.ALL);
    }

    @Test
    void missingMaskKeepsTheFixedPermissionsOfTheLegacyRestrictedTypes() {
        assertThat(JwtUtil.getPermissionMask(jwt("USER_DRAFT", null))).isEqualTo(3);
        assertThat(JwtUtil.getPermissionMask(jwt("USER_READ", null))).isEqualTo(1);
    }

    @Test
    void requirePermissionRejectsAMissingBit() {
        Jwt draftOnly = jwt("USER", 3);

        assertThatCode(() -> JwtUtil.requirePermission(draftOnly, CompanyPermission.INVOICE_DRAFT)).doesNotThrowAnyException();
        assertThatThrownBy(() -> JwtUtil.requirePermission(draftOnly, CompanyPermission.INVOICE_SEND))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void onlyTheAdminAccountTypeIsAdmin() {
        assertThat(JwtUtil.isAdmin(jwt("ADMIN", null))).isTrue();
        assertThat(JwtUtil.isAdmin(jwt("USER", 255))).isFalse();
        assertThat(JwtUtil.isAdmin(jwt("AFFILIATE", 255))).isFalse();
    }

    private static Jwt jwt(String accountType, Number permissionMask) {
        Jwt.Builder jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("user")
                .claim(SecurityConfig.ACCOUNT_TYPE, accountType);
        if (permissionMask != null) {
            jwt.claim(SecurityConfig.PERMISSION_MASK, permissionMask);
        }
        return jwt.build();
    }
}
