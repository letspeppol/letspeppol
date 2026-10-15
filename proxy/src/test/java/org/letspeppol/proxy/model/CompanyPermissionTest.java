package org.letspeppol.proxy.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyPermissionTest {

    @Test
    void bitsMatchTheSharedContract() {
        Map<String, Integer> bits = Arrays.stream(CompanyPermission.values())
                .collect(Collectors.toMap(Enum::name, CompanyPermission::bit));

        assertThat(bits).containsOnly(
                Map.entry("INVOICE_READ", 1),
                Map.entry("INVOICE_DRAFT", 2),
                Map.entry("INVOICE_SEND", 4),
                Map.entry("INVOICE_STATUS", 8),
                Map.entry("INVOICE_EXPORT", 16),
                Map.entry("PARTNER_MANAGE", 32),
                Map.entry("PRODUCT_MANAGE", 64),
                Map.entry("COMPANY_SETTINGS", 128)
        );
        assertThat(CompanyPermission.ALL).isEqualTo(255);
    }

    @Test
    void inChecksTheBit() {
        assertThat(CompanyPermission.INVOICE_SEND.in(5)).isTrue();
        assertThat(CompanyPermission.INVOICE_DRAFT.in(5)).isFalse();
        assertThat(CompanyPermission.INVOICE_READ.in(0)).isFalse();
    }
}
