package org.letspeppol.kyc.model;

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

    public static boolean isValid(int mask) {
        return mask >= 0 && (mask & ~ALL) == 0;
    }

    public static int normalize(int mask) {
        int normalized = mask & ALL;
        if (INVOICE_SEND.in(normalized)) {
            normalized |= INVOICE_DRAFT.bit;
        }
        if (INVOICE_DRAFT.in(normalized) || INVOICE_STATUS.in(normalized) || INVOICE_EXPORT.in(normalized)) {
            normalized |= INVOICE_READ.bit;
        }
        return normalized;
    }

    public static int effectiveMask(AccountType type, int storedMask) {
        return switch (type) {
            case USER -> normalize(storedMask);
            case USER_DRAFT -> INVOICE_READ.bit | INVOICE_DRAFT.bit;
            case USER_READ -> INVOICE_READ.bit;
            case ADMIN, AFFILIATE, APP -> ALL;
        };
    }
}
