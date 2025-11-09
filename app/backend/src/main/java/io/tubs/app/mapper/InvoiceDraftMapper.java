package io.tubs.app.mapper;

import io.tubs.app.dto.InvoiceDraftDto;
import io.tubs.app.model.InvoiceDraft;

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
