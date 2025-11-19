package org.letspeppol.kyc.exception;

public class KycException extends RuntimeException {
    private final String code;

    public KycException(String code) {
        super(code); // message equals code for easier logging
        this.code = code;
    }

    public String getCode() { return code; }
}
