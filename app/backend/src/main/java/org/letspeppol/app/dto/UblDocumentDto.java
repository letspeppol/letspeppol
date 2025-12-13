package org.letspeppol.app.dto;

import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.model.DocumentType;
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
