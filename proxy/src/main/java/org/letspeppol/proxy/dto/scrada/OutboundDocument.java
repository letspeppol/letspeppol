package org.letspeppol.proxy.dto.scrada;

public record OutboundDocument(
        String id,
        String createdOn,
        String externalReference,
        String peppolSenderID,
        String peppolReceiverID,
        String peppolC1CountryCode,
        String peppolC2Timestamp,
        String peppolC2SeatID,
        String peppolC2MessageID,
        String peppolC3MessageID,
        String peppolC3Timestamp,
        String peppolC3SeatID,
        String peppolConversationID,
        String peppolSbdhInstanceID,
        String peppolDocumentTypeScheme,
        String peppolDocumentTypeValue,
        String peppolProcessScheme,
        String peppolProcessValue,
        String salesInvoiceID,
        String status,
        int attempt,
        String errorMessage
) {}
