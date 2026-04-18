package org.letspeppol.app.dto;

import com.sun.jdi.StringReference;

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
        String lastCreditNoteReference,
        // TODO boolean noArchive,
        boolean peppolActive,
        boolean enableEmailNotification,
        boolean addAttachmentToNotification,
        boolean addPdfToSendingInvoice,
        String emailNotificationCCList,
        AddressDto registeredOffice
)
{}
