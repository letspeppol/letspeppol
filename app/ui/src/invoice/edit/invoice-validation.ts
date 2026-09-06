import {CreditNote, Invoice, UBLLine} from "../../services/peppol/ubl";
import {requiresDeliveryDetails} from "../../services/app/vat-rules";

export function isInvoiceValid(
    invoice: Invoice | CreditNote | undefined,
    lines: UBLLine[] | undefined,
    paymentInfoComplete: boolean | undefined,
): boolean {
    if (!invoice) {
        return false;
    }

    const customerParty = invoice.AccountingCustomerParty?.Party;
    const partyIdentification = customerParty?.PartyIdentification;
    const hasValidPartyIdentification = !partyIdentification
        || (Array.isArray(partyIdentification)
            && partyIdentification.length > 0
            && !!partyIdentification[0].ID?.value);
    const hasReference = !!(invoice.BuyerReference || invoice.OrderReference?.ID);
    const hasDueDateAlternative = !!(
        invoice.DueDate
        || invoice.PaymentTerms
        || (invoice as CreditNote).CreditNoteTypeCode
    );
    const hasRequiredDeliveryDetails = !lines?.some(line =>
        requiresDeliveryDetails(line.Item?.ClassifiedTaxCategory?.ID),
    ) || !!(
        invoice.Delivery?.ActualDeliveryDate
        && invoice.Delivery.DeliveryLocation?.Address?.Country?.IdentificationCode
    );

    return hasReference
        && !!invoice.IssueDate
        && hasDueDateAlternative
        && !!customerParty
        && hasValidPartyIdentification
        && !!customerParty.PartyLegalEntity?.RegistrationName
        && invoice.LegalMonetaryTotal?.LineExtensionAmount?.value > 0
        && hasRequiredDeliveryDetails
        && !!paymentInfoComplete;
}

export function isPaymentInfoComplete(
    paymentMeans: Invoice["PaymentMeans"] | CreditNote["PaymentMeans"] | undefined,
): boolean {
    return !paymentMeans
        || paymentMeans.PaymentMeansCode?.value !== 30
        || !!paymentMeans.PayeeFinancialAccount?.ID;
}
