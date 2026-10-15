package org.letspeppol.kyc.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyPermissionTest {

    @Test
    void bitsAreAppendOnlyAndSharedWithTheResourceServers() {
        Map<String, Integer> bits = Arrays.stream(CompanyPermission.values())
                .collect(Collectors.toMap(Enum::name, CompanyPermission::bit));

        assertThat(bits).containsExactlyInAnyOrderEntriesOf(Map.of(
                "INVOICE_READ", 1,
                "INVOICE_DRAFT", 2,
                "INVOICE_SEND", 4,
                "INVOICE_STATUS", 8,
                "INVOICE_EXPORT", 16,
                "PARTNER_MANAGE", 32,
                "PRODUCT_MANAGE", 64,
                "COMPANY_SETTINGS", 128));
        assertThat(CompanyPermission.ALL).isEqualTo(255);
    }

    @Test
    void membershipIsCheckedBitwise() {
        assertThat(CompanyPermission.INVOICE_SEND.in(4)).isTrue();
        assertThat(CompanyPermission.INVOICE_SEND.in(7)).isTrue();
        assertThat(CompanyPermission.INVOICE_SEND.in(3)).isFalse();
        assertThat(CompanyPermission.COMPANY_SETTINGS.in(0)).isFalse();
    }

    @Test
    void normalizeAddsImpliedBits() {
        assertThat(CompanyPermission.normalize(0)).isZero();
        assertThat(CompanyPermission.normalize(1)).isEqualTo(1);
        assertThat(CompanyPermission.normalize(2)).isEqualTo(3);
        assertThat(CompanyPermission.normalize(4)).isEqualTo(7);
        assertThat(CompanyPermission.normalize(8)).isEqualTo(9);
        assertThat(CompanyPermission.normalize(16)).isEqualTo(17);
        assertThat(CompanyPermission.normalize(32)).isEqualTo(32);
        assertThat(CompanyPermission.normalize(64)).isEqualTo(64);
        assertThat(CompanyPermission.normalize(128)).isEqualTo(128);
        assertThat(CompanyPermission.normalize(224)).isEqualTo(224);
        assertThat(CompanyPermission.normalize(255)).isEqualTo(255);
    }

    @Test
    void unknownBitsAndNegativeMasksAreInvalid() {
        assertThat(CompanyPermission.isValid(0)).isTrue();
        assertThat(CompanyPermission.isValid(255)).isTrue();
        assertThat(CompanyPermission.isValid(256)).isFalse();
        assertThat(CompanyPermission.isValid(511)).isFalse();
        assertThat(CompanyPermission.isValid(-1)).isFalse();
    }

    @Test
    void usersAndAffiliatesUseTheirStoredMask() {
        assertThat(CompanyPermission.effectiveMask(AccountType.USER, 0)).isZero();
        assertThat(CompanyPermission.effectiveMask(AccountType.USER, 4)).isEqualTo(7);
        assertThat(CompanyPermission.effectiveMask(AccountType.USER, 255)).isEqualTo(255);
        assertThat(CompanyPermission.effectiveMask(AccountType.AFFILIATE, 0)).isZero();
        assertThat(CompanyPermission.effectiveMask(AccountType.AFFILIATE, 16)).isEqualTo(17);
    }

    @Test
    void adminsAndAppsAlwaysGetEveryPermission() {
        assertThat(CompanyPermission.effectiveMask(AccountType.ADMIN, 0)).isEqualTo(255);
        assertThat(CompanyPermission.effectiveMask(AccountType.APP, 0)).isEqualTo(255);
    }
}
