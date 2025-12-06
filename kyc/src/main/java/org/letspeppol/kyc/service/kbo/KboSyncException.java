package org.letspeppol.kyc.service.kbo;

public class KboSyncException extends RuntimeException {
    public KboSyncException(String message) {
        super(message);
    }

    public KboSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}

