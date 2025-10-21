// mapper-ns.ts
import {XMLBuilder, XMLParser} from "fast-xml-parser";
import {CreditNote, Invoice} from "./ubl";

const parser = new XMLParser({
    ignoreAttributes: false,
    attributeNamePrefix: "__",
    textNodeName: "value",
    parseTagValue: false,
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

export const builder = new XMLBuilder({
    ignoreAttributes: false,
    attributeNamePrefix: "__",
    format: true,
    suppressEmptyNode: true,
    textNodeName: "value",
    oneListGroup: true
    // tagValueProcessor: (a) => a, // prevent automatic type coercion
    // attributeValueProcessor: (a) => a,
});

// PEPPOL namespaces
const UBL_NS = {
    "__xmlns:cac": "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
    "__xmlns:cbc": "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
    "__xmlns": "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2",
};

// Separate namespace for CreditNote root element
const CREDIT_NOTE_NS = {
    "__xmlns:cac": "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2",
    "__xmlns:cbc": "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2",
    "__xmlns": "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2",
};

// Helper to add prefixes recursively

function addPrefixes(obj: any): any {
    if (typeof obj !== "object" || obj === null) return obj;

    // Arrays handled recursively without renaming the element
    if (Array.isArray(obj)) {
        return obj.map(addPrefixes);
    }

    const result: any = {};
    for (const key of Object.keys(obj)) {
        let newKey = key;

        if (!key.startsWith("__") && key !== "Invoice" && key !== "CreditNote" && key !== "UBLVersionID" && key !== "value") {
            const prefix = PREFIX_MAP[key] || "cac";
            newKey = `${prefix}:${key}`;
        }

        result[newKey] = addPrefixes(obj[key]);
    }

    return result;
}

// Invoice functions
export function parseInvoice(xml: string): Invoice {
    const obj = parser.parse(xml);
    const invoiceObj = stripPrefixes(obj["Invoice"]);
    normalizeArrays(invoiceObj, ["TaxTotal", "TaxSubtotal", "AllowanceCharge", "InvoiceLine"]);
    return invoiceObj;
}

export function buildInvoice(invoice: Invoice): string {
    invoice = removeEmpty(invoice);
    const invoiceWithNS = { ...UBL_NS, ...addPrefixes(invoice), };
    const cleaned = removeEmptyNodes(invoiceWithNS);
    return `<?xml version="1.0" encoding="UTF-8"?>` + builder.build({Invoice: cleaned});
}

// CreditNote functions
export function parseCreditNote(xml: string): CreditNote {
    const obj = parser.parse(xml);
    const creditObj = stripPrefixes(obj["CreditNote"]);
    normalizeArrays(creditObj, ["TaxTotal", "TaxSubtotal", "AllowanceCharge", "CreditNoteLine"]);
    return creditObj;
}

export function buildCreditNote(creditNote: CreditNote): string {
    creditNote = removeEmpty(creditNote);
    const creditWithNS = { ...CREDIT_NOTE_NS, ...addPrefixes(creditNote) };
    const cleaned = removeEmptyNodes(creditWithNS);
    return `<?xml version="1.0" encoding="UTF-8"?>` + builder.build({ CreditNote: cleaned });
}

function removeEmptyNodes(obj: any): any {
    if (obj === null || obj === undefined) return undefined;
    if (Array.isArray(obj)) {
        const filtered = obj
            .map(removeEmptyNodes)
            .filter((item) => item !== undefined);
        return filtered.length ? filtered : undefined;
    }
    if (typeof obj === "object") {
        const result: any = {};
        for (const key of Object.keys(obj)) {
            const value = removeEmptyNodes(obj[key]);
            if (value !== undefined) {
                result[key] = value;
            }
        }
        return Object.keys(result).length ? result : undefined;
    }
    return obj; // primitive value
}

function stripPrefixes(obj: any): any {
    if (obj === null || obj === undefined) return obj;

    if (Array.isArray(obj)) {
        return obj.map(stripPrefixes);
    }

    if (typeof obj === "object") {
        const result: any = {};
        for (const key of Object.keys(obj)) {
            // Remove "cbc:" or "cac:" prefixes
            const newKey = key.replace(/^(cbc|cac):/, "");
            result[newKey] = stripPrefixes(obj[key]);
        }

        // Auto-normalize PartyIdentification inside Party objects
        if (result.PartyIdentification) {
            const pi = result.PartyIdentification;
            if (pi.ID) {
                const ids = Array.isArray(pi.ID) ? pi.ID : [pi.ID];
                result.PartyIdentification = ids.map(id => ({ ID: id }));
            }
        }

        // Normalize PartyTaxScheme.CompanyID to Identifier
        if (result.PartyTaxScheme && result.PartyTaxScheme.CompanyID) {
            const cid = result.PartyTaxScheme.CompanyID;
            if (typeof cid === "string") {
                result.PartyTaxScheme.CompanyID = { value: cid };
            }
        }

        return result;
    }

    return obj; // primitive
}

function normalizeArrays(obj: any, keys: string[]) {
    if (!obj || typeof obj !== "object") return;
    for (const key of keys) {
        if (obj[key] && !Array.isArray(obj[key])) {
            obj[key] = [obj[key]];
        }
    }
    for (const k of Object.keys(obj)) {
        normalizeArrays(obj[k], keys);
    }
}

function removeEmpty<T>(value: T): T | undefined {
    if (value == null) return undefined; // null or undefined

    if (typeof value === 'string') {
        const trimmed = value.trim();
        return trimmed === '' ? undefined : (trimmed as T);
    }

    if (Array.isArray(value)) {
        const cleaned = value
            .map(removeEmpty)
            .filter((v): v is Exclude<typeof v, undefined> => v !== undefined);

        return cleaned.length > 0 ? (cleaned as T) : undefined;
    }

    if (typeof value === 'object') {
        const entries = Object.entries(value)
            .map(([k, v]) => [k, removeEmpty(v)] as const)
            .filter(([_, v]) => v !== undefined);

        if (entries.length === 0) return undefined;
        return Object.fromEntries(entries) as T;
    }

    // numbers, booleans, etc.
    return value;
}

// Explicit prefix map for UBL 2.1 Invoice
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
    "Fax": "cbc", // if used
    "Email": "cbc",
    "TaxableAmount": "cbc",
    "ChargeIndicator": "cbc",
    "AllowanceChargeReason": "cbc",

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
