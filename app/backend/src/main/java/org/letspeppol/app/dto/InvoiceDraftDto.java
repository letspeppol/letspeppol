package org.letspeppol.app.dto;

import java.math.BigDecimal;

public record InvoiceDraftDto(
        Long id,
        String docType,
        String docId,
        String counterPartyName,
        String createdAt,
        String dueDate,
        BigDecimal amount,
        String xml
) {
}
