package org.letspeppol.kyc.service.kbo;

public class KboSftpException extends RuntimeException {

    public KboSftpException(String message) {
        super(message);
    }

    public KboSftpException(String message, Throwable cause) {
        super(message, cause);
    }
}
