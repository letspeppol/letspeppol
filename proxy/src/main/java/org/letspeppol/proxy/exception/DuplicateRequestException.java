package org.letspeppol.proxy.exception;

public class DuplicateRequestException extends RuntimeException {
    private final String errorCode;

    public DuplicateRequestException(String message) {
        this("DUPLICATE_REQUEST", message);
    }

    public DuplicateRequestException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
