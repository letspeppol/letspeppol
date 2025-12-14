import {XMLParser} from "fast-xml-parser";
import {CreditNote, Invoice} from "./ubl";
import { buildInvoiceXml, buildCreditNoteXml } from './ubl-builder';

const parser = new XMLParser({
    ignoreAttributes: false,
    attributeNamePrefix: "__",
    textNodeName: "value",
    parseTagValue: false,
    isArray: (name) => {
        return (
            name === "cac:InvoiceLine" ||
            name === "cac:AdditionalDocumentReference" ||
            false
        );
    },
    tagValueProcessor: (tagName, value) => {
        const bare = tagName.replace(/^[^:]*:/, '');
        if (numberFields.includes(bare)) {
            const n = Number(value);
            return isNaN(n) ? value : n;
        }
        if (booleanFields.includes(bare)) {
            if (value === 'true') return true;
            if (value === 'false') return false;
        }
        return value;
    }
});

// Invoice functions
export function parseInvoice(xml: string): Invoice {
    const obj = parser.parse(xml);
    const invoiceObj = stripPrefixes(obj["Invoice"]);
    normalizeArrays(invoiceObj, ["TaxTotal", "TaxSubtotal", "AllowanceCharge", "cac:InvoiceLine", "cac:AdditionalDocumentReference"]);
    return invoiceObj;
}

export function buildInvoice(invoice: Invoice): string {
    // Delegate to the string-based UBL builder to ensure deterministic ordering
    return buildInvoiceXml(invoice);
}

// CreditNote functions
export function parseCreditNote(xml: string): CreditNote {
    const obj = parser.parse(xml);
    const creditObj = stripPrefixes(obj["CreditNote"]);
    normalizeArrays(creditObj, ["TaxTotal", "TaxSubtotal", "AllowanceCharge", "CreditNoteLine", "AdditionalDocumentReference"]);
    return creditObj;
}

export function buildCreditNote(creditNote: CreditNote): string {
    // Delegate to the string-based UBL builder to ensure deterministic ordering
    return buildCreditNoteXml(creditNote);
}

function stripPrefixes(obj: unknown): unknown {
    if (obj === null || obj === undefined) return obj;

    if (Array.isArray(obj)) {
        return obj.map(stripPrefixes);
    }

    if (typeof obj === "object") {
        const input = obj as Record<string, unknown>;
        const result: Record<string, unknown> = {};
        for (const key of Object.keys(input)) {
            // Remove "cbc:" or "cac:" prefixes
            const newKey = key.replace(/^(cbc|cac):/, "");
            result[newKey] = stripPrefixes(input[key]);
        }

        // Auto-normalize PartyIdentification inside Party objects
        if (result.PartyIdentification) {
            const pi = result.PartyIdentification as any;
            if (pi.ID) {
                const ids = Array.isArray(pi.ID) ? pi.ID : [pi.ID];
                result.PartyIdentification = ids.map((id: any) => ({ ID: id }));
            }
        }

        return result;
    }

    return obj; // primitive
}

function normalizeArrays(obj: unknown, keys: string[]): void {
    if (!obj || typeof obj !== "object") return;
    const record = obj as Record<string, unknown>;

    for (const key of keys) {
        if (record[key] && !Array.isArray(record[key])) {
            record[key] = [record[key]];
        }
    }

    for (const k of Object.keys(record)) {
        normalizeArrays(record[k], keys);
    }
}

// Explicit prefix map for UBL 2.1 Invoice (kept for reference; not used at build time anymore)
const PREFIX_MAP: Record<string, "cbc" | "cac"> = {
    // CBC basic components
    "CustomizationID": "cbc",
    "ProfileID": "cbc",
    "ID": "cbc",
    "IssueDate": "cbc",
    "DueDate": "cbc",
    "InvoiceTypeCode": "cbc",
    "CreditNoteTypeCode": "cbc",
    "DocumentCurrencyCode": "cbc",
    "AccountingCost": "cbc",
    "BuyerReference": "cbc",
    "LineExtensionAmount": "cbc",
    "TaxExclusiveAmount": "cbc",
    "TaxInclusiveAmount": "cbc",
    "ChargeTotalAmount": "cbc",
    "PayableAmount": "cbc",
    "PriceAmount": "cbc",
    "InvoicedQuantity": "cbc",
    "CreditedQuantity": "cbc",
    "Percent": "cbc",
    "Note": "cbc",
    "EndpointID": "cbc",
    "PaymentMeansCode": "cbc",
    "PaymentID": "cbc",
    "Amount": "cbc",
    "TaxAmount": "cbc",
    "CompanyID": "cbc",
    "RegistrationName": "cbc",
    "Description": "cbc",
    "Name": "cbc",
    "StreetName": "cbc",
    "AdditionalStreetName": "cbc",
    "CityName": "cbc",
    "PostalZone": "cbc",
    "IdentificationCode": "cbc",
    "LineID": "cbc",
    "ItemClassificationCode": "cbc",
    "ActualDeliveryDate": "cbc",
    "Telephone": "cbc",
    "ElectronicMail": "cbc",
    "Fax": "cbc",
    "Email": "cbc",
    "TaxableAmount": "cbc",
    "ChargeIndicator": "cbc",
    "AllowanceChargeReason": "cbc",
    "EmbeddedDocumentBinaryObject": "cbc",
    "DocumentDescription": "cbc",
    "URI": "cbc"
    // Everything else is aggregate (cac)
};

const numberFields = [
    "ChargeTotalAmount",
    "CreditedQuantity",
    "InvoiceTypeCode",
    "CreditNoteTypeCode",
    "InvoicedQuantity",
    "LineExtensionAmount",
    "PayableAmount",
    "PaymentMeansCode",
    "Percent",
    "PriceAmount",
    "TaxExclusiveAmount",
    "TaxInclusiveAmount",
    "TaxAmount",
    "TaxableAmount",
    "Amount",
    "value",       // for Amounts and Quantities
];

const booleanFields = [
    "ChargeIndicator"
];
