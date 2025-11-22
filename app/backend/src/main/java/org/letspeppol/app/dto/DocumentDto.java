package org.letspeppol.app.dto;

import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.model.DocumentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

public record DocumentDto(
        UUID id,
        DocumentDirection direction,
        String ownerPeppolId,
        String partnerPeppolId,
        Instant proxyOn,
        Instant scheduledOn,
        Instant processedOn,
        String processedStatus,
        String ubl,
        Instant draftedOn,
        Instant readOn,
        Instant paidOn,
        String partnerName,
        String invoiceReference,
        DocumentType type,
        Currency currency,
        BigDecimal amount,
        Instant issueDate,
        Instant dueDate,
        String paymentTerms
) {}
