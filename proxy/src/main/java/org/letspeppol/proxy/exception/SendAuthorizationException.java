package org.letspeppol.proxy.exception;

/** A rejected send that the scheduler can retry without rolling back its retry state. */
public class SendAuthorizationException extends RuntimeException {
    private final boolean companyMissing;

    public SendAuthorizationException(boolean companyMissing, Throwable cause) {
        super("Access point rejected sending authorization (HTTP 401)", cause);
        this.companyMissing = companyMissing;
    }

    public boolean isCompanyMissing() {
        return companyMissing;
    }
}
