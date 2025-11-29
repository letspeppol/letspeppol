package org.letspeppol.app.mapper;

import org.letspeppol.app.dto.DocumentDto;
import org.letspeppol.app.model.Document;

public class DocumentMapper {

    public static DocumentDto toDto(Document document) {
        if (document == null) {
            return null;
        }
        return new DocumentDto(
                document.getId(),
                document.getDirection(),
                document.getOwnerPeppolId(),
                document.getPartnerPeppolId(),
                document.getProxyOn(),
                document.getScheduledOn(),
                document.getProcessedOn(),
                document.getProcessedStatus(),
                document.getUbl(),
                document.getDraftedOn(),
                document.getReadOn(),
                document.getPaidOn(),
                document.getPartnerName(),
                document.getInvoiceReference(),
                document.getType(),
                document.getCurrency(),
                document.getAmount(),
                document.getIssueDate(),
                document.getDueDate(),
                document.getPaymentTerms()
        );
    }
}
