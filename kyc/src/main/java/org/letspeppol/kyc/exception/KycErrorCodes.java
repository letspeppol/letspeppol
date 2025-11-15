package org.letspeppol.kyc.exception;

public final class KycErrorCodes {
    // Company / registration
    public static final String COMPANY_ALREADY_REGISTERED = "company_already_registered";
    public static final String COMPANY_NOT_FOUND = "company_not_found";
    // Token / activation
    public static final String TOKEN_NOT_FOUND = "token_not_found";
    public static final String TOKEN_ALREADY_VERIFIED = "token_already_verified";
    public static final String TOKEN_EXPIRED = "token_expired";
    // User
    public static final String USER_NOT_FOUND = "user_not_found";
    public static final String WRONG_PASSWORD = "wrong_password";
    public static final String USER_ALREADY_LINKED = "user_already_linked"; // restored
    // Password reset
    public static final String PASSWORD_RESET_TOKEN_NOT_FOUND = "password_reset_token_not_found";
    public static final String PASSWORD_RESET_TOKEN_EXPIRED = "password_reset_token_expired";
    public static final String PASSWORD_RESET_TOKEN_ALREADY_USED = "password_reset_token_already_used";
    public static final String INVALID_PASSWORD = "invalid_password";
    // Proxy
    public static final String PROXY_REGISTRATION_FAILED = "proxy_registration_failed";
    public static final String PROXY_UNREGISTRATION_FAILED = "proxy_unregistration_failed";
    // KBO
    public static final String KBO_PARSE_ADDRESS_FAILED = "kbo_parse_address_failed";
    public static final String KBO_PARSE_DIRECTORS_FAILED = "kbo_parse_directors_failed";
    public static final String KBO_NOT_FOUND = "kbo_not_found";
    public static final String KBO_SERVICE_ERROR = "kbo_service_error";
    // Signing / certificates
    public static final String INVALID_CERTIFICATE = "invalid_certificate";
    // Contract
    public static final String CONTRACT_NOT_FOUND = "contract_not_found";
    // Generic
    public static final String NOT_FOUND = "not_found";
    public static final String UNEXPECTED_ERROR = "unexpected_error";
    // Auth
    public static final String AUTHENTCATION_FAILED = "auth_failed";
    private KycErrorCodes() {}
}
