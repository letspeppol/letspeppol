package org.letspeppol.proxy.exception;

public class AlreadyRegisteredException extends RuntimeException {
    private final String provide;

    public AlreadyRegisteredException(String provide) {
        super(provide);
        this.provide = provide;
    }

    public String getProvider() {
        return provide;
    }
}
