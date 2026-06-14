package org.letspeppol.app.dto;

public record SponsorInvoiceResponse(
        String status,
        String message,
        UblDocumentDto document
) {}
