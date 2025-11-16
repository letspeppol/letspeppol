package org.letspeppol.proxy.dto;

import org.letspeppol.proxy.model.Direction;
import java.time.Instant;
import java.util.UUID;

public record UblDocumentDto(
    UUID id,
    Direction direction,
    String ownerPeppolId,
    String partnerPeppolId,
    Instant createdOn,
    Instant scheduledOn,
    Instant processedOn,
    String processedStatus,
    String ubl
) {}
