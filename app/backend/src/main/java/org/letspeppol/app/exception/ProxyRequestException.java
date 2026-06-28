package org.letspeppol.app.exception;

import org.springframework.http.HttpStatusCode;

public class ProxyRequestException extends RuntimeException {

    private final HttpStatusCode statusCode;
    private final String errorCode;

    public ProxyRequestException(HttpStatusCode statusCode, String message) {
        this(statusCode, null, message);
    }

    public ProxyRequestException(HttpStatusCode statusCode, String errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
