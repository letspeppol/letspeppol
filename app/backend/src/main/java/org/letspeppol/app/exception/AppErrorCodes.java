package org.letspeppol.app.exception;

public final class AppErrorCodes {
    private AppErrorCodes() {}

    public static final String PEPPOL_ID_MISMATCH = "PEPPOL_ID_MISMATCH";
    public static final String PEPPOL_ID_NOT_PRESENT = "PEPPOL_ID_NOT_PRESENT";
    public static final String JWT_UID_NOT_PRESENT = "JWT_UID_NOT_PRESENT";
    public static final String PEPPOL_ACTIVE_NOT_PRESENT = "PEPPOL_ACTIVE_NOT_PRESENT";
    public static final String PEPPOL_ID_INVALID = "PEPPOL_ID_INVALID";
    public static final String PEPPOL_DIR_400_ERROR = "PEPPOL_DIR_400_ERROR";
    public static final String PEPPOL_DIR_500_ERROR = "PEPPOL_DIR_500_ERROR";
    public static final String PEPPOL_DIR_RATE_LIMIT_ERROR = "PEPPOL_DIR_RATE_LIMIT_ERROR";
    public static final String INVOICE_NUMBER_ALREADY_USED = "INVOICE_NUMBER_ALREADY_USED";
    public static final String KYC_REST_ERROR = "KYC_REST_ERROR";
}
