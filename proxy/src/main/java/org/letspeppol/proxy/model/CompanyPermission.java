package org.letspeppol.proxy.model;

import java.util.Arrays;

public enum CompanyPermission {
    INVOICE_READ(1),
    INVOICE_DRAFT(2),
    INVOICE_SEND(4),
    INVOICE_STATUS(8),
    INVOICE_EXPORT(16),
    PARTNER_MANAGE(32),
    PRODUCT_MANAGE(64),
    COMPANY_SETTINGS(128);

    public static final int ALL = Arrays.stream(values()).mapToInt(CompanyPermission::bit).reduce(0, (mask, bit) -> mask | bit);

    private final int bit;

    CompanyPermission(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    public boolean in(int mask) {
        return (mask & bit) != 0;
    }
}
