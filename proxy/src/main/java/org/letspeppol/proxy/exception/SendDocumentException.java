package org.letspeppol.proxy.exception;

/** A send failure that the scheduler records in the document's processed status. */
public class SendDocumentException extends RuntimeException {
    public SendDocumentException(String message, Throwable cause) {
        super(message, cause);
    }
}
