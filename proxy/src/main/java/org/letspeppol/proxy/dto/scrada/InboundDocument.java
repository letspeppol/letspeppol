package org.letspeppol.proxy.dto.scrada;

public record InboundDocument(
        String id,
        int internalNumber,
        String peppolSenderScheme,
        String peppolSenderID,
        String peppolReceiverScheme,
        String peppolReceiverID,
        String peppolC1CountryCode,
        String peppolC2Timestamp,
        String peppolC2SeatID,
        String peppolC2MessageID,
        String peppolC3IncomingUniqueID,
        String peppolC3MessageID,
        String peppolC3Timestamp,
        String peppolConversationID,
        String peppolSbdhInstanceID,
        String peppolProcessScheme,
        String peppolProcessValue,
        String peppolDocumentTypeScheme,
        String peppolDocumentTypeValue
) {}
