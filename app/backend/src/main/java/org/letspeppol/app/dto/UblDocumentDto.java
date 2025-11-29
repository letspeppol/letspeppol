package org.letspeppol.app.dto;

import org.letspeppol.app.model.DocumentDirection;
import java.time.Instant;
import java.util.UUID;

public record UblDocumentDto(
        UUID id,
        DocumentDirection direction,
        String ownerPeppolId,
        String partnerPeppolId,
        Instant createdOn,
        Instant scheduledOn,
        Instant processedOn,
        String processedStatus,
        String ubl
) {}
