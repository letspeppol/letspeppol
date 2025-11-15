package org.letspeppol.kyc.exception;

public class NotFoundException extends RuntimeException {
    private final String code;

    public NotFoundException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() { return code; }
}
