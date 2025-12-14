// String-based UBL XML builder for Invoice and CreditNote.
// Builds XML by string concatenation with explicit ordering, matching the
// structures used in peppol-parser tests.

import type {
    AccountingParty,
    Address,
    AdditionalDocumentReference,
    AllowanceCharge,
    ClassifiedTaxCategory,
    CommodityClassification,
    CreditNote,
    CreditNoteLine,
    CreditNotePaymentMeans,
    Delivery,
    DeliveryLocation,
    DeliveryParty,
    Identifier,
    Invoice,
    InvoiceLine,
    Item,
    MonetaryTotal,
    OrderLineReference,
    Party,
    PartyIdentification,
    PartyLegalEntity,
    PartyTaxScheme,
    PayeeFinancialAccount,
    PaymentMeans,
    PaymentMeansCode,
    PaymentTerms,
    Price,
    Quantity,
    StandardItemIdentification,
    TaxCategory,
    TaxSubtotal,
    TaxTotal,
    Amount,
} from './ubl';

// --- XML helpers -----------------------------------------------------------

function escapeXml(text: string): string {
    return text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&apos;');
}

function attr(name: string, value: string | number | boolean | undefined | null): string {
    if (value === undefined || value === null) return '';
    return ` ${name}="${escapeXml(String(value))}"`;
}

function textElement(name: string, value: string | number | boolean | undefined | null, attrs = ''): string {
    if (value === undefined || value === null) return '';
    const text = escapeXml(String(value));
    return `<${name}${attrs}>${text}</${name}>`;
}

function joinNonEmpty(parts: string[]): string {
    return parts.filter(Boolean).join('');
}

// --- Small helpers for common value-object patterns -----------------------

function buildIdentifier(tag: string, id?: Identifier): string {
    if (!id) return '';
    const attrs = attr('schemeID', id.__schemeID);
    return textElement(tag, id.value, attrs);
}

function buildQuantity(tag: string, q?: Quantity): string {
    if (!q) return '';
    const attrs = attr('unitCode', q.__unitCode);
    return textElement(tag, q.value, attrs);
}

function buildAmount(tag: string, a?: Amount): string {
    if (!a) return '';
    const attrs = attr('currencyID', a.__currencyID);
    return textElement(tag, a.value, attrs);
}

// --- Root namespace constants ---------------------------------------------

const INVOICE_NS_ATTRS =
    'xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" ' +
    'xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2" ' +
    'xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"';

const CREDIT_NOTE_NS_ATTRS =
    'xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" ' +
    'xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2" ' +
    'xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2"';

// --- Aggregate builders ----------------------------------------------------

function buildAddress(address?: Address): string {
    if (!address) return '';
    return joinNonEmpty([
        '<cac:PostalAddress>',
        textElement('cbc:StreetName', address.StreetName),
        textElement('cbc:AdditionalStreetName', address.AdditionalStreetName),
        textElement('cbc:CityName', address.CityName),
        textElement('cbc:PostalZone', address.PostalZone),
        address.Country
            ? joinNonEmpty([
                  '<cac:Country>',
                  textElement('cbc:IdentificationCode', address.Country.IdentificationCode),
                  '</cac:Country>',
              ])
            : '',
        '</cac:PostalAddress>',
    ]);
}

function buildPartyTaxScheme(p?: PartyTaxScheme): string {
    if (!p) return '';
    const companyIdRaw = (p as PartyTaxScheme).CompanyID as string | Identifier;
    const companyIdText =
        typeof companyIdRaw === 'object' && companyIdRaw && 'value' in companyIdRaw
            ? companyIdRaw.value
            : (companyIdRaw as string | number | boolean | undefined | null);
    return joinNonEmpty([
        '<cac:PartyTaxScheme>',
        textElement('cbc:CompanyID', companyIdText),
        p.TaxScheme
            ? joinNonEmpty([
                  '<cac:TaxScheme>',
                  textElement('cbc:ID', p.TaxScheme.ID),
                  '</cac:TaxScheme>',
              ])
            : '',
        '</cac:PartyTaxScheme>',
    ]);
}

function buildPartyLegalEntity(p?: PartyLegalEntity): string {
    if (!p) return '';
    return joinNonEmpty([
        '<cac:PartyLegalEntity>',
        textElement('cbc:RegistrationName', p.RegistrationName),
        p.CompanyID ? buildIdentifier('cbc:CompanyID', p.CompanyID) : '',
        '</cac:PartyLegalEntity>',
    ]);
}

function buildContact(c?: { Name?: string; Telephone?: string; ElectronicMail?: string }): string {
    if (!c) return '';
    return joinNonEmpty([
        '<cac:Contact>',
        textElement('cbc:Name', c.Name),
        textElement('cbc:Telephone', c.Telephone),
        textElement('cbc:ElectronicMail', c.ElectronicMail),
        '</cac:Contact>',
    ]);
}

function buildPartyIdentificationList(list?: PartyIdentification[]): string {
    if (!list || list.length === 0) return '';
    return joinNonEmpty(
        list.map((pi) =>
            joinNonEmpty([
                '<cac:PartyIdentification>',
                buildIdentifier('cbc:ID', pi.ID),
                '</cac:PartyIdentification>',
            ]),
        ),
    );
}

function buildParty(p: Party): string {
    if (!p) return '';
    return joinNonEmpty([
        '<cac:Party>',
        p.EndpointID ? buildIdentifier('cbc:EndpointID', p.EndpointID) : '',
        buildPartyIdentificationList(p.PartyIdentification),
        p.PartyName
            ? joinNonEmpty([
                  '<cac:PartyName>',
                  textElement('cbc:Name', p.PartyName.Name),
                  '</cac:PartyName>',
              ])
            : '',
        p.PostalAddress ? buildAddress(p.PostalAddress) : '',
        buildPartyTaxScheme(p.PartyTaxScheme),
        buildPartyLegalEntity(p.PartyLegalEntity),
        buildContact(p.Contact),
        '</cac:Party>',
    ]);
}

function buildAccountingParty(wrapperName: 'cac:AccountingSupplierParty' | 'cac:AccountingCustomerParty', ap: AccountingParty): string {
    if (!ap) return '';
    return joinNonEmpty([
        `<${wrapperName}>`,
        buildParty(ap.Party),
        `</${wrapperName}>`,
    ]);
}

function buildTaxCategory(tc?: TaxCategory): string {
    if (!tc) return '';
    return joinNonEmpty([
        '<cac:TaxCategory>',
        textElement('cbc:ID', tc.ID),
        textElement('cbc:Percent', tc.Percent),
        tc.TaxScheme
            ? joinNonEmpty([
                  '<cac:TaxScheme>',
                  textElement('cbc:ID', tc.TaxScheme.ID),
                  '</cac:TaxScheme>',
              ])
            : '',
        '</cac:TaxCategory>',
    ]);
}

function buildTaxSubtotal(ts?: TaxSubtotal[]): string {
    if (!ts || ts.length === 0) return '';
    return joinNonEmpty(
        ts.map((s) =>
            joinNonEmpty([
                '<cac:TaxSubtotal>',
                buildAmount('cbc:TaxableAmount', s.TaxableAmount),
                buildAmount('cbc:TaxAmount', s.TaxAmount),
                buildTaxCategory(s.TaxCategory),
                '</cac:TaxSubtotal>',
            ]),
        ),
    );
}

function buildTaxTotal(tt?: TaxTotal[]): string {
    if (!tt || tt.length === 0) return '';
    return joinNonEmpty(
        tt.map((t) =>
            joinNonEmpty([
                '<cac:TaxTotal>',
                buildAmount('cbc:TaxAmount', t.TaxAmount),
                buildTaxSubtotal(t.TaxSubtotal),
                '</cac:TaxTotal>',
            ]),
        ),
    );
}

function buildMonetaryTotal(mt: MonetaryTotal): string {
    return joinNonEmpty([
        '<cac:LegalMonetaryTotal>',
        buildAmount('cbc:LineExtensionAmount', mt.LineExtensionAmount),
        buildAmount('cbc:TaxExclusiveAmount', mt.TaxExclusiveAmount),
        buildAmount('cbc:TaxInclusiveAmount', mt.TaxInclusiveAmount),
        buildAmount('cbc:ChargeTotalAmount', mt.ChargeTotalAmount),
        buildAmount('cbc:PayableAmount', mt.PayableAmount),
        '</cac:LegalMonetaryTotal>',
    ]);
}

function buildOrderLineReference(olr?: OrderLineReference): string {
    if (!olr) return '';
    return joinNonEmpty([
        '<cac:OrderLineReference>',
        textElement('cbc:LineID', olr.LineID),
        '</cac:OrderLineReference>',
    ]);
}

function buildCommodityClassification(cc?: CommodityClassification): string {
    if (!cc) return '';
    const itemCode = cc.ItemClassificationCode;
    const attrs = attr('listID', itemCode.__listID);
    return joinNonEmpty([
        '<cac:CommodityClassification>',
        textElement('cbc:ItemClassificationCode', itemCode.value, attrs),
        '</cac:CommodityClassification>',
    ]);
}

function buildClassifiedTaxCategory(ctc?: ClassifiedTaxCategory): string {
    if (!ctc) return '';
    return joinNonEmpty([
        '<cac:ClassifiedTaxCategory>',
        textElement('cbc:ID', ctc.ID),
        textElement('cbc:Percent', ctc.Percent),
        '<cac:TaxScheme>',
        textElement('cbc:ID', ctc.TaxScheme.ID),
        '</cac:TaxScheme>',
        '</cac:ClassifiedTaxCategory>',
    ]);
}

function buildStandardItemIdentification(sid?: StandardItemIdentification): string {
    if (!sid) return '';
    return joinNonEmpty([
        '<cac:StandardItemIdentification>',
        buildIdentifier('cbc:ID', sid.ID),
        '</cac:StandardItemIdentification>',
    ]);
}

function buildItem(item: Item): string {
    return joinNonEmpty([
        '<cac:Item>',
        textElement('cbc:Description', item.Description),
        textElement('cbc:Name', item.Name),
        buildStandardItemIdentification(item.StandardItemIdentification),
        item.OriginCountry
            ? joinNonEmpty([
                  '<cac:OriginCountry>',
                  textElement('cbc:IdentificationCode', item.OriginCountry.IdentificationCode),
                  '</cac:OriginCountry>',
              ])
            : '',
        buildCommodityClassification(item.CommodityClassification),
        buildClassifiedTaxCategory(item.ClassifiedTaxCategory),
        '</cac:Item>',
    ]);
}

function buildPrice(price: Price): string {
    return joinNonEmpty([
        '<cac:Price>',
        buildAmount('cbc:PriceAmount', price.PriceAmount),
        buildQuantity('cbc:BaseQuantity', price.BaseQuantity),
        '</cac:Price>',
    ]);
}

function buildAllowanceCharge(list?: AllowanceCharge[]): string {
    if (!list || list.length === 0) return '';
    return joinNonEmpty(
        list.map((ac) =>
            joinNonEmpty([
                '<cac:AllowanceCharge>',
                textElement('cbc:ChargeIndicator', ac.ChargeIndicator),
                textElement('cbc:AllowanceChargeReason', ac.AllowanceChargeReason),
                buildAmount('cbc:Amount', ac.Amount),
                buildTaxCategory(ac.TaxCategory),
                '</cac:AllowanceCharge>',
            ]),
        ),
    );
}

function buildDeliveryLocation(dl?: DeliveryLocation): string {
    if (!dl) return '';
    return joinNonEmpty([
        '<cac:DeliveryLocation>',
        dl.ID ? buildIdentifier('cbc:ID', dl.ID) : '',
        dl.Address
            ? joinNonEmpty([
                  '<cac:Address>',
                  textElement('cbc:StreetName', dl.Address.StreetName),
                  textElement('cbc:AdditionalStreetName', dl.Address.AdditionalStreetName),
                  textElement('cbc:CityName', dl.Address.CityName),
                  textElement('cbc:PostalZone', dl.Address.PostalZone),
                  dl.Address.Country
                      ? joinNonEmpty([
                            '<cac:Country>',
                            textElement('cbc:IdentificationCode', dl.Address.Country.IdentificationCode),
                            '</cac:Country>',
                        ])
                      : '',
                  '</cac:Address>',
              ])
            : '',
        '</cac:DeliveryLocation>',
    ]);
}

function buildDeliveryParty(dp?: DeliveryParty): string {
    if (!dp) return '';
    return joinNonEmpty([
        '<cac:DeliveryParty>',
        dp.PartyName
            ? joinNonEmpty([
                  '<cac:PartyName>',
                  textElement('cbc:Name', dp.PartyName.Name),
                  '</cac:PartyName>',
              ])
            : '',
        '</cac:DeliveryParty>',
    ]);
}

function buildDelivery(delivery?: Delivery): string {
    if (!delivery) return '';
    return joinNonEmpty([
        '<cac:Delivery>',
        textElement('cbc:ActualDeliveryDate', delivery.ActualDeliveryDate),
        buildDeliveryLocation(delivery.DeliveryLocation),
        buildDeliveryParty(delivery.DeliveryParty),
        '</cac:Delivery>',
    ]);
}

function buildPayeeFinancialAccount(acc?: PayeeFinancialAccount): string {
    if (!acc) return '';
    return joinNonEmpty([
        '<cac:PayeeFinancialAccount>',
        textElement('cbc:ID', acc.ID),
        textElement('cbc:Name', acc.Name),
        acc.FinancialInstitutionBranch
            ? joinNonEmpty([
                  '<cac:FinancialInstitutionBranch>',
                  textElement('cbc:ID', acc.FinancialInstitutionBranch.ID),
                  '</cac:FinancialInstitutionBranch>',
              ])
            : '',
        '</cac:PayeeFinancialAccount>',
    ]);
}

function buildPaymentMeansCode(code?: PaymentMeansCode): string {
    if (!code) return '';
    const attrs = attr('name', code.__name);
    return textElement('cbc:PaymentMeansCode', code.value, attrs);
}

function buildPaymentMeans(pm?: PaymentMeans | CreditNotePaymentMeans): string {
    if (!pm) return '';
    const isCredit = (pm as CreditNotePaymentMeans).PaymentDueDate !== undefined;
    return joinNonEmpty([
        '<cac:PaymentMeans>',
        buildPaymentMeansCode(pm.PaymentMeansCode),
        isCredit ? textElement('cbc:PaymentDueDate', (pm as CreditNotePaymentMeans).PaymentDueDate) : '',
        textElement('cbc:PaymentID', pm.PaymentID),
        buildPayeeFinancialAccount(pm.PayeeFinancialAccount),
        '</cac:PaymentMeans>',
    ]);
}

function buildPaymentTerms(pt?: PaymentTerms): string {
    if (!pt) return '';
    return joinNonEmpty([
        '<cac:PaymentTerms>',
        textElement('cbc:Note', pt.Note),
        '</cac:PaymentTerms>',
    ]);
}

function buildEmbeddedDocumentBinaryObject(ed?: { __mimeCode: string; __filename: string; value: string }): string {
    if (!ed) return '';
    const attrs = attr('mimeCode', ed.__mimeCode) + attr('filename', ed.__filename);
    return textElement('cbc:EmbeddedDocumentBinaryObject', ed.value, attrs);
}

function buildAttachment(att?: { EmbeddedDocumentBinaryObject?: { __mimeCode: string; __filename: string; value: string }; ExternalReference?: { URI: string } }): string {
    if (!att) return '';
    return joinNonEmpty([
        '<cac:Attachment>',
        att.ExternalReference
            ? joinNonEmpty([
                  '<cac:ExternalReference>',
                  textElement('cbc:URI', att.ExternalReference.URI),
                  '</cac:ExternalReference>',
              ])
            : '',
        buildEmbeddedDocumentBinaryObject(att.EmbeddedDocumentBinaryObject),
        '</cac:Attachment>',
    ]);
}

function buildAdditionalDocumentReference(list?: AdditionalDocumentReference[]): string {
    if (!list || list.length === 0) return '';
    return joinNonEmpty(
        list.map((adr) =>
            joinNonEmpty([
                '<cac:AdditionalDocumentReference>',
                textElement('cbc:ID', adr.ID),
                textElement('cbc:DocumentDescription', adr.DocumentDescription),
                buildAttachment(adr.Attachment),
                '</cac:AdditionalDocumentReference>',
            ]),
        ),
    );
}

function buildInvoiceLine(line: InvoiceLine): string {
    return joinNonEmpty([
        '<cac:InvoiceLine>',
        textElement('cbc:ID', line.ID),
        buildQuantity('cbc:InvoicedQuantity', line.InvoicedQuantity),
        buildAmount('cbc:LineExtensionAmount', line.LineExtensionAmount),
        textElement('cbc:AccountingCost', line.AccountingCost),
        buildOrderLineReference(line.OrderLineReference),
        buildItem(line.Item),
        buildPrice(line.Price),
        '</cac:InvoiceLine>',
    ]);
}

function buildInvoiceLines(lines: InvoiceLine[]): string {
    if (!lines || lines.length === 0) return '';
    return joinNonEmpty(lines.map((l) => buildInvoiceLine(l)));
}

function buildCreditNoteLine(line: CreditNoteLine): string {
    return joinNonEmpty([
        '<cac:CreditNoteLine>',
        textElement('cbc:ID', line.ID),
        buildQuantity('cbc:CreditedQuantity', line.CreditedQuantity),
        buildAmount('cbc:LineExtensionAmount', line.LineExtensionAmount),
        textElement('cbc:AccountingCost', line.AccountingCost),
        buildOrderLineReference(line.OrderLineReference),
        buildItem(line.Item),
        buildPrice(line.Price),
        '</cac:CreditNoteLine>',
    ]);
}

function buildCreditNoteLines(lines: CreditNoteLine[]): string {
    if (!lines || lines.length === 0) return '';
    return joinNonEmpty(lines.map((l) => buildCreditNoteLine(l)));
}

// --- Public root builders --------------------------------------------------

export function buildInvoiceXml(invoice: Invoice): string {
    const body = joinNonEmpty([
        textElement('cbc:CustomizationID', invoice.CustomizationID),
        textElement('cbc:ProfileID', invoice.ProfileID),
        textElement('cbc:ID', invoice.ID),
        textElement('cbc:IssueDate', invoice.IssueDate),
        textElement('cbc:DueDate', invoice.DueDate),
        textElement('cbc:InvoiceTypeCode', invoice.InvoiceTypeCode),
        textElement('cbc:DocumentCurrencyCode', invoice.DocumentCurrencyCode),
        textElement('cbc:AccountingCost', invoice.AccountingCost),
        textElement('cbc:BuyerReference', invoice.BuyerReference),
        buildAdditionalDocumentReference(invoice.AdditionalDocumentReference),
        buildAccountingParty('cac:AccountingSupplierParty', invoice.AccountingSupplierParty),
        buildAccountingParty('cac:AccountingCustomerParty', invoice.AccountingCustomerParty),
        buildDelivery(invoice.Delivery),
        buildPaymentMeans(invoice.PaymentMeans as PaymentMeans),
        buildPaymentTerms(invoice.PaymentTerms),
        buildTaxTotal(invoice.TaxTotal),
        buildMonetaryTotal(invoice.LegalMonetaryTotal),
        buildInvoiceLines(invoice.InvoiceLine),
    ]);

    return `<?xml version="1.0" encoding="UTF-8"?><Invoice ${INVOICE_NS_ATTRS}>${body}</Invoice>`;
}

export function buildCreditNoteXml(creditNote: CreditNote): string {
    const body = joinNonEmpty([
        textElement('cbc:CustomizationID', creditNote.CustomizationID),
        textElement('cbc:ProfileID', creditNote.ProfileID),
        textElement('cbc:ID', creditNote.ID),
        textElement('cbc:IssueDate', creditNote.IssueDate),
        textElement('cbc:CreditNoteTypeCode', creditNote.CreditNoteTypeCode),
        textElement('cbc:DocumentCurrencyCode', creditNote.DocumentCurrencyCode),
        textElement('cbc:AccountingCost', creditNote.AccountingCost),
        textElement('cbc:BuyerReference', creditNote.BuyerReference),
        buildAccountingParty('cac:AccountingSupplierParty', creditNote.AccountingSupplierParty),
        buildAccountingParty('cac:AccountingCustomerParty', creditNote.AccountingCustomerParty),
        buildAllowanceCharge(creditNote.AllowanceCharge),
        buildTaxTotal(creditNote.TaxTotal),
        buildMonetaryTotal(creditNote.LegalMonetaryTotal),
        buildCreditNoteLines(creditNote.CreditNoteLine),
    ]);

    return `<?xml version="1.0" encoding="UTF-8"?><CreditNote ${CREDIT_NOTE_NS_ATTRS}>${body}</CreditNote>`;
}
