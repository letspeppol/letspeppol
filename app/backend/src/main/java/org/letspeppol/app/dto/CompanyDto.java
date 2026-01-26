package org.letspeppol.app.dto;

public record CompanyDto(
        String peppolId,
        String vatNumber,
        String name,
        String displayName,
        String subscriber,
        String subscriberEmail,
        String paymentTerms,
        String iban,
        String paymentAccountName,
        String lastInvoiceReference,
        // TODO boolean noArchive,
        boolean peppolActive,
        boolean enableEmailNotification,
        boolean addAttachmentToNotification,
        String emailNotificationCCList,
        AddressDto registeredOffice
)
{}
