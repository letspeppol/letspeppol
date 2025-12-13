package org.letspeppol.proxy.dto;

import org.letspeppol.proxy.model.DocumentDirection;
import org.letspeppol.proxy.model.DocumentType;
import java.time.Instant;
import java.util.UUID;

public record UblDocumentDto(
        UUID id,
        DocumentDirection direction,
        DocumentType type,
        String ownerPeppolId,
        String partnerPeppolId,
        Instant createdOn,
        Instant scheduledOn,
        Instant processedOn,
        String processedStatus,
        String ubl
) {}
