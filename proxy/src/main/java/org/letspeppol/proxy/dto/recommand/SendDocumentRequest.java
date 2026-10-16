package org.letspeppol.proxy.dto.recommand;

public record SendDocumentRequest(
        String recipient,
        String documentType,
        String document
) {}
