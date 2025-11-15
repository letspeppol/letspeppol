package org.letspeppol.app.exception;

public class AppException extends RuntimeException {

    private final String code;

    public AppException(String code) {
        super(code);
        this.code = code;
    }

    public AppException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AppException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

}

