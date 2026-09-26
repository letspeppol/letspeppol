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
            name === "BillingReference" ||
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
    const stripped = normalizeParsedValue(root, rootTag);
    return stripped as T;
}

/**
 * fast-xml-parser represents a leaf element with attributes as an object, while
 * the UI model uses objects only for identifiers, amounts, quantities and a few
 * other value types. Normalize both attributed and non-attributed leaves to the
 * model's stable shape so optional UBL metadata does not change runtime types.
 */
function normalizeParsedValue(obj: unknown, key?: string, parentKey?: string): unknown {
    if (obj === null || obj === undefined) return obj;

    if (Array.isArray(obj)) {
        return obj.map(item => normalizeParsedValue(item, key, parentKey));
    }

    if (typeof obj === "object") {
        const input = obj as Record<string, unknown>;
        const result: Record<string, unknown> = {};
        for (const childKey of Object.keys(input)) {
            if (childKey === '__proto__' || childKey === 'constructor' || childKey === 'prototype') {
                continue;
            }
            result[childKey] = normalizeParsedValue(input[childKey], childKey, key);
        }

        if (isAttributedLeaf(result) && !usesValueObject(key, parentKey)) {
            return result.value;
        }

        return result;
    }

    if (usesValueObject(key, parentKey)) {
        return {value: obj};
    }

    return obj; // primitive
}

function isAttributedLeaf(value: Record<string, unknown>): boolean {
    return Object.hasOwn(value, 'value')
        && Object.keys(value).every(key => key === 'value' || key.startsWith('__'));
}

function usesValueObject(key?: string, parentKey?: string): boolean {
    if (!key) return false;

    if (VALUE_OBJECT_FIELDS.has(key)) {
        return true;
    }

    return key === 'ID' && IDENTIFIER_ID_PARENTS.has(parentKey ?? '');
}

const VALUE_OBJECT_FIELDS = new Set([
    // Identifiers
    'EndpointID',
    'CompanyID',
    'ItemClassificationCode',

    // Amounts
    'Amount',
    'ChargeTotalAmount',
    'LineExtensionAmount',
    'PayableAmount',
    'PriceAmount',
    'TaxAmount',
    'TaxableAmount',
    'TaxExclusiveAmount',
    'TaxInclusiveAmount',

    // Quantities and other modeled value objects
    'BaseQuantity',
    'CreditedQuantity',
    'InvoicedQuantity',
    'PaymentMeansCode',
    'EmbeddedDocumentBinaryObject',
]);

const IDENTIFIER_ID_PARENTS = new Set([
    'DeliveryLocation',
    'PartyIdentification',
    'StandardItemIdentification',
]);

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
