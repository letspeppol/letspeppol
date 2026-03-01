package org.letspeppol.app.dto.accountant;

public record LinkCustomerDto(
        String customerPeppolId,
        String customerEmail,
        String customerName
) {}
