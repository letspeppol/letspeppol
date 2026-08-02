package org.letspeppol.app.dto;

import org.letspeppol.app.model.VatRuleset;

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
        VatRuleset vatRuleset,
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
