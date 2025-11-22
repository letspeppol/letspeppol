package org.letspeppol.app.dto;

import org.letspeppol.app.model.DocumentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

public record UblDto(
        String senderPeppolId,
        String receiverPeppolId,
        String partnerName,
        String invoiceReference,
        String buyerReference,
        String orderReference,
        DocumentType type,
        Currency currency,
        BigDecimal amount,
        Instant issueDate,
        Instant dueDate,
        String paymentTerms
) {}
