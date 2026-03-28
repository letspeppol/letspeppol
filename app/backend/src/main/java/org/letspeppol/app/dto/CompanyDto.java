package org.letspeppol.app.dto;

public record CompanyDto(
        String peppolId,
        String identifier,
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
        boolean addPdfToSendingInvoice,
        String emailNotificationCCList,
        AddressDto registeredOffice
)
{}
