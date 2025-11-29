package org.letspeppol.proxy.dto.scrada;

import java.util.List;

public record RegisterRequest(
        ParticipantIdentifier participantIdentifier,
        BusinessEntity businessEntity,
        List<DocumentType> documentTypes,
        String migrationKey
) {}
