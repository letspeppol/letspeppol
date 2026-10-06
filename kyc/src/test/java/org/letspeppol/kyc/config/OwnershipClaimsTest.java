package org.letspeppol.kyc.config;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.model.OwnershipStatus;
import org.letspeppol.kyc.model.kbo.Company;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OwnershipClaimsTest {

    private static final String PEPPOL_ID = "0208:0123456789";

    @Test
    void adminTokenCarriesEveryPermission() {
        Map<String, Object> claims = claimsFor(ownership(AccountType.ADMIN, 0));

        assertThat(claims)
                .containsEntry("accountType", "ADMIN")
                .containsEntry("peppolId", PEPPOL_ID)
                .containsEntry("permissionMask", 255);
    }

    @Test
    void userTokenCarriesTheNormalisedStoredMask() {
        Map<String, Object> claims = claimsFor(ownership(AccountType.USER, 4));

        assertThat(claims)
                .containsEntry("accountType", "USER")
                .containsEntry("permissionMask", 7);
    }

    @Test
    void affiliateTokenCarriesTheNormalisedStoredMask() {
        assertThat(claimsFor(ownership(AccountType.AFFILIATE, 16)))
                .containsEntry("accountType", "AFFILIATE")
                .containsEntry("permissionMask", 17);
    }

    @Test
    void userWithoutPermissionsCarriesAnEmptyMask() {
        assertThat(claimsFor(ownership(AccountType.USER, 0))).containsEntry("permissionMask", 0);
    }

    @Test
    void newOwnershipsAreActiveWithoutPermissions() {
        Ownership ownership = ownership(AccountType.USER, 0);

        assertThat(ownership.getStatus()).isEqualTo(OwnershipStatus.ACTIVE);
        assertThat(Ownership.builder().build().getStatus()).isEqualTo(OwnershipStatus.ACTIVE);
        assertThat(ownership.getPermissionMask()).isZero();
    }

    private static Map<String, Object> claimsFor(Ownership ownership) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        SecurityConfig.addOwnershipClaims(claims, ownership);
        return claims.build().getClaims();
    }

    private static Ownership ownership(AccountType type, int permissionMask) {
        Company company = new Company(PEPPOL_ID, "0123456789", "BE0123456789", "Claims Company");
        Ownership ownership = new Ownership(new Account(), type, company);
        ownership.setPermissionMask(permissionMask);
        return ownership;
    }
}
