package org.letspeppol.kyc.exception;

public final class KycErrorCodes {
    // Company / registration
    public static final String COMPANY_ALREADY_REGISTERED = "company_already_registered";
    public static final String COMPANY_NOT_FOUND = "company_not_found";
    // Token / activation
    public static final String TOKEN_NOT_FOUND = "token_not_found";
    public static final String TOKEN_ALREADY_VERIFIED = "token_already_verified";
    public static final String TOKEN_EXPIRED = "token_expired";
    // Account
    public static final String ACCOUNT_NOT_FOUND = "account_not_found";
    public static final String ACCOUNT_NOT_ADMIN = "account_not_admin";
    public static final String ACCOUNT_NOT_APP = "account_not_app";
    public static final String WRONG_PASSWORD = "wrong_password";
    public static final String ACCOUNT_ALREADY_LINKED = "account_already_linked"; // restored
    public static final String REQUESTER_NOT_VERIFIED = "requester_not_verified";
    public static final String REQUESTER_NOT_VALID = "requester_not_valid";
    public static final String ONLY_ONE_ADMIN_ALLOWED = "only_one_admin_allowed";
    public static final String INVALID_ACCOUNTANT_REQUEST = "invalid_accountant_request";
    // Password reset
    public static final String PASSWORD_RESET_TOKEN_NOT_FOUND = "password_reset_token_not_found";
    public static final String PASSWORD_RESET_TOKEN_EXPIRED = "password_reset_token_expired";
    public static final String PASSWORD_RESET_TOKEN_ALREADY_USED = "password_reset_token_already_used";
    public static final String INVALID_PASSWORD = "invalid_password";
    // Proxy
    public static final String PROXY_REGISTRATION_FAILED = "proxy_registration_failed";
    public static final String PROXY_UNREGISTRATION_FAILED = "proxy_unregistration_failed";
    public static final String PROXY_FAILED = "proxy_failed";
    public static final String PROXY_REGISTRATION_CONFLICT = "proxy_registration_conflict";
    public static final String PROXY_REGISTRATION_UNAVAILABLE = "proxy_registration_unavailable";
    public static final String PROXY_REGISTRATION_INTERNAL_ERROR = "proxy_registration_internal_error";
    public static final String PROXY_REGISTRATION_SUSPENDED = "proxy_registration_suspended";
    public static final String PROXY_REGISTRATION_NOT_NEEDED = "proxy_registration_not_needed";
    public static final String PROXY_ALLOW_SERVICE_FAILED = "proxy_allow_service_failed";
    public static final String PROXY_REJECT_SERVICE_FAILED = "proxy_reject_service_failed";
    // KBO
    public static final String KBO_PARSE_ADDRESS_FAILED = "kbo_parse_address_failed";
    public static final String KBO_PARSE_DIRECTORS_FAILED = "kbo_parse_directors_failed";
    public static final String KBO_NOT_FOUND = "kbo_not_found";
    public static final String KBO_SERVICE_ERROR = "kbo_service_error";
    // Signing / certificates
    public static final String INVALID_CERTIFICATE = "invalid_certificate";
    public static final String INVALID_ACCOUNT_TYPE = "invalid_account_type";
    // Contract
    public static final String CONTRACT_NOT_FOUND = "contract_not_found";
    // Generic
    public static final String NOT_FOUND = "not_found";
    public static final String UNEXPECTED_ERROR = "unexpected_error";
    // Auth
    public static final String AUTHENTCATION_FAILED = "auth_failed";
    public static final String NO_OWNERSHIP = "no_ownership";
    public static final String NOT_ADMIN = "not_admin";

    private KycErrorCodes() {}
}
