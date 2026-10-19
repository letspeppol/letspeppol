package org.letspeppol.app.dto;

public record PeppolSettings(Boolean receiveDocuments, String receivingSmpUrl) {
    public PeppolSettings(Boolean receiveDocuments) {
        this(receiveDocuments, null);
    }
}
