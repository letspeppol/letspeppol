package org.letspeppol.proxy.model;

public enum DocumentType {
    INVOICE,
    CREDIT_NOTE,
    SELF_BILLING_INVOICE,
    SELF_BILLING_CREDIT_NOTE;

    public boolean isInvoice() {
        return this == INVOICE || this == SELF_BILLING_INVOICE;
    }

    public boolean isCreditNote() {
        return this == CREDIT_NOTE || this == SELF_BILLING_CREDIT_NOTE;
    }

    public boolean isSelfBilling() {
        return this == SELF_BILLING_INVOICE || this == SELF_BILLING_CREDIT_NOTE;
    }
}
