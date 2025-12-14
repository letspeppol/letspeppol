package org.letspeppol.proxy.mapper;

import org.letspeppol.proxy.dto.UblDocumentDto;
import org.letspeppol.proxy.model.UblDocument;

public class UblDocumentMapper {
    public static UblDocumentDto toDto(UblDocument ublDocument) {
        if (ublDocument == null) {
            return null;
        }
        return new UblDocumentDto(
                ublDocument.getId(),
                ublDocument.getDirection(),
                ublDocument.getType(),
                ublDocument.getOwnerPeppolId(),
                ublDocument.getPartnerPeppolId(),
                ublDocument.getCreatedOn(),
                ublDocument.getScheduledOn(),
                ublDocument.getProcessedOn(),
                ublDocument.getProcessedStatus(),
                ublDocument.getUbl()
        );
    }
}
