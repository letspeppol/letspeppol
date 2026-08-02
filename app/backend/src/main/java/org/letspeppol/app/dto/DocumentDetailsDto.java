package org.letspeppol.app.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentDetailsDto(
        UUID id,
        String invoiceReference,
        String ownerPeppolId,
        String partnerPeppolId,
        String accessPoint,
        String accessPointId,
        Instant processedOn,
        String processedStatus,
        String partnerPeppolAccessPoint,
        String partnerPeppolMessageId,
        Instant partnerPeppolMessageOn
) {}
