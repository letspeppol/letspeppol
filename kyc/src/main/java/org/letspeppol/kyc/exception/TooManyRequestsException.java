package org.letspeppol.kyc.exception;

public class TooManyRequestsException extends RuntimeException {
    private final String code;

    public TooManyRequestsException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() { return code; }
}
