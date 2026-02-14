package org.letspeppol.app.dto.accountant;

import java.time.Instant;

public record CustomerDto(
        Long id,
        String customerPeppolId,
        String customerEmail,
        String customerName,
        Instant invitedOn,
        Instant verifiedOn,
        Instant lastDownloadCreatedOn,
        Instant lastDownloadIssuedOn
) {}

