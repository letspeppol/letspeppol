package org.letspeppol.app.mapper;

import org.letspeppol.app.dto.InvoiceDraftDto;
import org.letspeppol.app.model.InvoiceDraft;

public class InvoiceDraftMapper {

    public static InvoiceDraftDto toDto(InvoiceDraft invoiceDraft) {
        return new InvoiceDraftDto(
                invoiceDraft.getId(),
                invoiceDraft.getDocType(),
                invoiceDraft.getDocId(),
                invoiceDraft.getCounterPartyName(),
                invoiceDraft.getCreatedAt(),
                invoiceDraft.getDueDate(),
                invoiceDraft.getAmount(),
                invoiceDraft.getXml()
        );
    }
}
