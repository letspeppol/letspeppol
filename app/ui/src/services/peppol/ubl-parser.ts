import {XMLParser} from "fast-xml-parser";
import {CreditNote, Invoice} from "./ubl";

const parser = new XMLParser({
    ignoreAttributes: false,
    attributeNamePrefix: "__",
    textNodeName: "value",
    parseTagValue: false,
    removeNSPrefix: true,
    isArray: (name) => {
        return (
            name === "CreditNoteLine" ||
            name === "InvoiceLine" ||
            name === "AdditionalDocumentReference" ||
            name === "AllowanceCharge" ||
            name === "TaxSubtotal" ||
            name === "TaxTotal" ||
            name === "PartyIdentification" ||
            false
        );
    },
    tagValueProcessor: (tagName, value) => {
        const bare = tagName.replace(/^[^:]*:/, '');
        if (NUMERIC_TAGS.has(bare)) {
            const n = Number(value);
            return isNaN(n) ? value : n;
        }
        if (BOOLEAN_TAGS.has(bare)) {
            if (value === 'true') return true;
            if (value === 'false') return false;
        }
        return value;
    }
});

export function parseInvoice(xml: string): Invoice {
    return parseUblDocument<Invoice>(xml, "Invoice");
}

export function parseCreditNote(xml: string): CreditNote {
    return parseUblDocument<CreditNote>(xml, "CreditNote");
}

function parseUblDocument<T>(xml: string, rootTag: "Invoice" | "CreditNote"): T {
    const obj = parser.parse(xml);
    const root = obj[rootTag];
    const stripped = stripPrefixes(root);
    return stripped as T;
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
            result[key] = stripPrefixes(input[key]);
        }

        if (result.PartyLegalEntity) {
            const ple = result.PartyLegalEntity as Record<string, unknown>;
            const rawCompany = ple.CompanyID as unknown;
            if (typeof rawCompany === 'string') {
                ple.CompanyID = { value: rawCompany };
            }
        }

        if (result.PartyTaxScheme) {
            const pts = result.PartyTaxScheme as Record<string, unknown>;
            const rawCompany = pts.CompanyID as unknown;
            if (typeof rawCompany === 'string') {
                pts.CompanyID = { value: rawCompany };
            }
        }

        return result;
    }

    return obj; // primitive
}

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

const NUMERIC_TAGS = new Set(numberFields);
const BOOLEAN_TAGS = new Set(booleanFields);
