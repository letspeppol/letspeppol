package org.letspeppol.kyc.exception;

public class ForbiddenException extends RuntimeException {
    private final String code;

    public ForbiddenException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() { return code; }
}
