import {describe, expect, test} from "vitest";
import {parseCreditNote, parseInvoice} from "../../src/services/peppol/ubl-parser";
import { normalizeXml, sampleInvoiceXml, sampleCreditNoteXml } from './ubl-test-utils';
import {buildCreditNoteXml, buildInvoiceXml} from "../../src/services/peppol/ubl-builder";

describe("Invoice XML round-trip", () => {
    test("parse -> build should match original XML", () => {
        // Parse XML to Invoice object
        const invoiceObj = parseInvoice(sampleInvoiceXml);

        expect(invoiceObj.CustomizationID).toBe(
            "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0"
        );
        expect(invoiceObj.ProfileID).toBe(
            "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0"
        );
        expect(invoiceObj.ID).toBe("Snippet1");
        expect(invoiceObj.IssueDate).toBe("2025-11-13");
        expect(invoiceObj.DueDate).toBe("2025-12-01");
        expect(invoiceObj.InvoiceTypeCode).toBe(380);
        expect(invoiceObj.DocumentCurrencyCode).toBe("EUR");
        expect(invoiceObj.AccountingCost).toBe("4025:123:4343");
        expect(invoiceObj.BuyerReference).toBe("0150abc");

        // --- Supplier ---
        const supplier = invoiceObj.AccountingSupplierParty.Party;
        expect(supplier.EndpointID?.value).toBe("1023290711");
        expect(supplier.PartyIdentification?.[0].ID.value).toBe("1023290711");
        expect(supplier.PartyName?.Name).toBe("SupplierTradingName Ltd.");
        expect(supplier.PostalAddress?.StreetName).toBe("Main street 1");
        expect(supplier.PostalAddress?.CityName).toBe("London");
        expect(supplier.PostalAddress?.PostalZone).toBe("GB 123 EW");
        expect(supplier.PartyTaxScheme?.CompanyID.value).toBe("GB1232434");
        expect(supplier.PartyLegalEntity?.RegistrationName).toBe("SupplierOfficialName Ltd");
        expect(supplier.PartyLegalEntity?.CompanyID.value).toBe("GB983294");

        // --- Customer ---
        const customer = invoiceObj.AccountingCustomerParty.Party;
        expect(customer.EndpointID?.value).toBe("0705969661");
        expect(customer.PartyIdentification?.[0].ID.__schemeID).toBe("0208");
        expect(customer.PartyIdentification?.[0].ID.value).toBe("0705969661");
        expect(customer.PartyName?.Name).toBe("BuyerTradingName AS");
        expect(customer.PostalAddress?.StreetName).toBe("Hovedgatan 32");
        expect(customer.PostalAddress?.CityName).toBe("Stockholm");
        expect(customer.PostalAddress?.PostalZone).toBe("456 34");
        expect(customer.PartyTaxScheme?.CompanyID.value).toBe("SE4598375937");
        expect(customer.PartyLegalEntity?.RegistrationName).toBe("Buyer Official Name");
        expect(customer.PartyLegalEntity?.CompanyID.value).toBe("39937423947");
        expect(customer.Contact?.Name).toBe("Lisa Johnson");
        expect(customer.Contact?.Telephone).toBe("23434234");
        expect(customer.Contact?.ElectronicMail).toBe("lj@buyer.se");

        // --- Delivery ---
        expect(invoiceObj.Delivery?.ActualDeliveryDate).toBe("2025-11-01");
        expect(invoiceObj.Delivery?.DeliveryLocation?.ID.value).toBe(
            "9483759475923478"
        );
        expect(invoiceObj.Delivery?.DeliveryLocation?.Address?.StreetName).toBe(
            "Delivery street 2"
        );
        expect(invoiceObj.Delivery?.DeliveryParty?.PartyName?.Name).toBe(
            "Delivery party Name"
        );

        // --- PaymentMeans ---
        expect(invoiceObj.PaymentMeans?.PaymentMeansCode?.value).toBe(30); // automatica data conversion?
        expect(invoiceObj.PaymentMeans?.PaymentMeansCode?.__name).toBe(
            "Credit transfer"
        );
        expect(invoiceObj.PaymentMeans?.PaymentID).toBe("Snippet1");
        expect(invoiceObj.PaymentMeans?.PayeeFinancialAccount?.ID).toBe(
            "IBAN32423940"
        );
        expect(invoiceObj.PaymentMeans?.PayeeFinancialAccount?.Name).toBe(
            "AccountName"
        );

        // --- PaymentTerms ---
        expect(invoiceObj.PaymentTerms?.Note).toBe(
            "Payment within 10 days, 2% discount"
        );

        // --- TaxTotal ---
        const taxTotal = invoiceObj.TaxTotal?.[0];
        expect(taxTotal?.TaxAmount.value).toBe(331.25);
        const taxSubtotal = taxTotal?.TaxSubtotal?.[0];
        expect(taxSubtotal?.TaxableAmount.value).toBe(1325);
        expect(taxSubtotal?.TaxAmount.value).toBe(331.25);
        expect(taxSubtotal?.TaxCategory?.Percent).toBe(25);
        expect(taxSubtotal?.TaxCategory?.TaxScheme?.ID).toBe("VAT");

        // --- LegalMonetaryTotal ---
        const monetaryTotal = invoiceObj.LegalMonetaryTotal;
        expect(monetaryTotal.LineExtensionAmount?.value).toBe(1300);
        expect(monetaryTotal.TaxExclusiveAmount?.value).toBe(1325);
        expect(monetaryTotal.TaxInclusiveAmount?.value).toBe(1656.25);
        expect(monetaryTotal.ChargeTotalAmount?.value).toBe(25);
        expect(monetaryTotal.PayableAmount.value).toBe(1656.25);

        // --- InvoiceLine ---
        expect(invoiceObj.InvoiceLine.length).toBe(2);
        const line = invoiceObj.InvoiceLine[0];
        expect(line.ID).toBe("1");
        expect(line.InvoicedQuantity?.value).toBe(7);
        expect(line.LineExtensionAmount.value).toBe(2800);
        expect(line.Item.Name).toBe("item name");
        expect(line.Item.Description).toBe("Description of item");
        expect(line.Item.StandardItemIdentification?.ID.value).toBe(
            "21382183120983"
        );
        expect(line.Item.OriginCountry?.IdentificationCode).toBe("NO");
        expect(line.Item.ClassifiedTaxCategory?.Percent).toBe(25);
        expect(line.Price.PriceAmount.value).toBe(400);

        // --- Attachment ---
        expect(invoiceObj.AdditionalDocumentReference.length).toBe(2);
        const additionalDocumentReference1 = invoiceObj.AdditionalDocumentReference[0];
        expect(additionalDocumentReference1.ID).toBe("LINK1");
        const additionalDocumentReference2 = invoiceObj.AdditionalDocumentReference[1];
        expect(additionalDocumentReference2.ID).toBe("ATTACHMENT-099");
        expect(additionalDocumentReference2.Attachment.EmbeddedDocumentBinaryObject.__mimeCode).toBe("application/text");
        expect(additionalDocumentReference2.Attachment.EmbeddedDocumentBinaryObject.__filename).toBe("ATTACHMENT-099.txt");
        expect(additionalDocumentReference2.Attachment.EmbeddedDocumentBinaryObject.value).toBe("RGl0IGlzIGVlbiB0ZXN0");

        // Build XML from Invoice object
        const rebuiltXml = buildInvoiceXml(invoiceObj);

        // Normalize whitespace for comparison
        const originalNormalized = `<?xml version="1.0" encoding="UTF-8"?>` + normalizeXml(sampleInvoiceXml);
        const rebuiltNormalized = normalizeXml(rebuiltXml);

        expect(rebuiltNormalized).toBe(originalNormalized);
    });
});

describe("CreditNote XML round-trip", () => {
    test("parse -> build should match original XML", () => {
        const creditNoteObj = parseCreditNote(sampleCreditNoteXml);

        expect(creditNoteObj.CustomizationID).toBe(
            "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0"
        );
        expect(creditNoteObj.ProfileID).toBe(
            "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0"
        );
        expect(creditNoteObj.ID).toBe("Snippet1");
        expect(creditNoteObj.IssueDate).toBe("2025-11-13");
        expect(creditNoteObj.CreditNoteTypeCode).toBe(381);
        expect(creditNoteObj.DocumentCurrencyCode).toBe("EUR");
        expect(creditNoteObj.AccountingCost).toBe("4025:123:4343");
        expect(creditNoteObj.BuyerReference).toBe("0150abc");

        // Supplier
        const supplier = creditNoteObj.AccountingSupplierParty.Party;
        expect(supplier.EndpointID?.value).toBe("1023290711");
        expect(supplier.PartyIdentification?.[0].ID.value).toBe("1023290711");
        expect(supplier.PartyName?.Name).toBe("SupplierTradingName Ltd.");
        expect(supplier.PostalAddress?.StreetName).toBe("Main street 1");
        expect(supplier.PostalAddress?.CityName).toBe("London");
        expect(supplier.PostalAddress?.PostalZone).toBe("GB 123 EW");
        expect(supplier.PartyTaxScheme?.CompanyID.value).toBe("GB1232434");
        expect(supplier.PartyLegalEntity?.RegistrationName).toBe("SupplierOfficialName Ltd");

        // Customer
        const customer = creditNoteObj.AccountingCustomerParty.Party;
        expect(customer.EndpointID?.__schemeID).toBe("0208");
        expect(customer.EndpointID?.value).toBe("0705969661");
        expect(customer.PartyIdentification?.[0].ID.__schemeID).toBe("0208");
        expect(customer.PartyIdentification?.[0].ID.value).toBe("0705969661");
        expect(customer.PartyName?.Name).toBe("BuyerTradingName AS");
        expect(customer.PostalAddress?.StreetName).toBe("Hovedgatan 32");
        expect(customer.PostalAddress?.CityName).toBe("Stockholm");
        expect(customer.PostalAddress?.PostalZone).toBe("456 34");
        expect(customer.PartyTaxScheme?.CompanyID.value).toBe("SE4598375937");
        expect(customer.PartyLegalEntity?.CompanyID.value).toBe("39937423947");
        expect(customer.Contact?.Name).toBe("Lisa Johnson");
        expect(customer.Contact?.Telephone).toBe("23434234");
        expect(customer.Contact?.ElectronicMail).toBe("lj@buyer.se");

        // AllowanceCharge
        const allowance = creditNoteObj.AllowanceCharge?.[0];
        expect(allowance?.ChargeIndicator).toBe(true);
        expect(allowance?.AllowanceChargeReason).toBe("Insurance");
        expect(allowance?.Amount.value).toBe(25);
        expect(allowance?.TaxCategory?.Percent).toBe(25.0);

        // TaxTotal
        const taxTotal = creditNoteObj.TaxTotal?.[0];
        expect(taxTotal?.TaxAmount.value).toBe(331.25);
        const taxSubtotal = taxTotal?.TaxSubtotal?.[0];
        expect(taxSubtotal?.TaxableAmount.value).toBe(1325);
        expect(taxSubtotal?.TaxAmount.value).toBe(331.25);
        expect(taxSubtotal?.TaxCategory?.Percent).toBe(25.0);

        // Monetary total
        const monetaryTotal = creditNoteObj.LegalMonetaryTotal;
        expect(monetaryTotal.LineExtensionAmount?.value).toBe(1300);
        expect(monetaryTotal.TaxExclusiveAmount?.value).toBe(1325);
        expect(monetaryTotal.TaxInclusiveAmount?.value).toBe(1656.25);
        expect(monetaryTotal.ChargeTotalAmount?.value).toBe(25);
        expect(monetaryTotal.PayableAmount.value).toBe(1656.25);

        // CreditNoteLine
        expect(creditNoteObj.CreditNoteLine.length).toBe(1);
        const line = creditNoteObj.CreditNoteLine[0];
        expect(line.ID).toBe("1");
        expect(line.CreditedQuantity?.value).toBe(7);
        expect(line.LineExtensionAmount.value).toBe(2800);
        expect(line.Item.Name).toBe("item name");
        expect(line.Item.Description).toBe("Description of item");
        expect(line.Item.StandardItemIdentification?.ID.value).toBe(
            "21382183120983"
        );
        expect(line.Item.OriginCountry?.IdentificationCode).toBe("NO");
        expect(line.Item.ClassifiedTaxCategory?.Percent).toBe(25.0);
        expect(line.Price.PriceAmount.value).toBe(400);

        const rebuiltXml = buildCreditNoteXml(creditNoteObj);
        const originalNormalized = `<?xml version="1.0" encoding="UTF-8"?>` + normalizeXml(sampleCreditNoteXml);
        const rebuiltNormalized = normalizeXml(rebuiltXml);
        expect(rebuiltNormalized).toBe(originalNormalized);
    });
});

