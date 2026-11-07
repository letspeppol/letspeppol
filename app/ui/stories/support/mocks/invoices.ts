import moment from 'moment';
import { AppTask, IAppRoot, IContainer } from 'aurelia';
import { I18N } from '@aurelia/i18n';
import type { IRegistration, IRegistry } from '@aurelia/kernel';
import type { CompanyDto } from '../../../src/services/app/company-service';
import {
  DocumentDirection,
  DocumentType,
  InvoiceService,
  isCreditNoteType,
  type DocumentDetailsDto,
  type DocumentDto,
  type DocumentPageDto,
  type DocumentQuery,
  type ValidationResultDto,
} from '../../../src/services/app/invoice-service';
import { PartnerService, type PartnerDto } from '../../../src/services/app/partner-service';
import { ProductService, type ProductDto } from '../../../src/services/app/product-service';
import { CompanySearchService } from '../../../src/services/kyc/company-search-service';
import type { KycCompanyResponse } from '../../../src/services/kyc/registration-service';
import { StatisticsService, type DonationStatsDto } from '../../../src/services/app/statistics-service';
import { SponsorService } from '../../../src/services/app/sponsor-service';
import { InvoiceCalculator } from '../../../src/invoice/invoice-calculator';
import { InvoiceContext } from '../../../src/invoice/invoice-context';
import { GENERATED_INVOICE, GENERATED_INVOICE_PLACEHOLDER, PDF_MIME_CODE } from '../../../src/invoice/invoice-composer';
import { createZeroVatCategory } from '../../../src/services/app/vat-rules';
import { buildCreditNoteXml, buildInvoiceXml } from '../../../src/services/peppol/ubl-builder';
import { detectUblDocumentKind, parseCreditNote, parseInvoice } from '../../../src/services/peppol/ubl-parser';
import type {
  AdditionalDocumentReference,
  AllowanceCharge,
  ClassifiedTaxCategory,
  CreditNote,
  CreditNoteLine,
  Delivery,
  Invoice,
  InvoiceLine,
  Party,
  UBLDoc,
} from '../../../src/services/peppol/ubl';
import { bakery, partners as sharedPartners, products as sharedProducts } from './fixtures';
import { sessionMocks, type SessionOptions } from './session';
import { pending, provide, resolved, stub } from './stub';

const calculator = new InvoiceCalculator();
const today = moment().startOf('day');

export function day(offset: number): string {
  return today.clone().add(offset, 'day').format('YYYY-MM-DD');
}

export function at(offset: number, time = '09:30'): string {
  return moment(`${day(offset)}T${time}`).toISOString();
}

export const seller: CompanyDto = { ...bakery, paymentTerms: 'NET_30' };

const paymentTermsByPartner: Record<number, string> = { 1: 'NET_30', 2: 'NET_14', 4: 'EOM_30' };

export const invoicePartners: PartnerDto[] = sharedPartners.map((partner) => ({
  ...partner,
  paymentTerms: paymentTermsByPartner[partner.id] ?? partner.paymentTerms,
}));

const [anker, mosan, vandewiele, mokabon, zwaan] = invoicePartners;

export const invoiceProducts: ProductDto[] = [
  ...sharedProducts,
  { id: 6, name: 'Croissant amandel', reference: 'PA-015', costPrice: 0.55, salePrice: 1.95, taxPercentage: 6, stockQuantity: 60, categoryId: 2 },
  { id: 7, name: 'Croque monsieur', reference: 'CA-210', salePrice: 6.5, taxPercentage: 12, stockQuantity: 0, categoryId: 3 },
];

export const kycCompanies: KycCompanyResponse[] = [
  { id: 11, peppolId: '0208:0477112233', identifier: '0477112233', vatNumber: 'BE0477112233', name: 'Bistro Plezier BV', street: 'Veldstraat 21', city: 'Gent', postalCode: '9000' },
  { id: 12, peppolId: '0208:0655443322', identifier: '0655443322', vatNumber: 'BE0655443322', name: 'Bistro Pleinzicht VOF', street: 'Grote Markt 3', city: 'Leuven', postalCode: '3000' },
];

export function structuredCommunication(base: number): string {
  const digits = base.toString().padStart(10, '0');
  const check = (base % 97 || 97).toString().padStart(2, '0');
  const all = `${digits}${check}`;
  return `+++${all.slice(0, 3)}/${all.slice(3, 7)}/${all.slice(7)}+++`;
}

function companyParty(company: CompanyDto): Party {
  return {
    EndpointID: { __schemeID: '0208', value: company.identifier },
    PartyIdentification: [{ ID: { __schemeID: '0208', value: company.identifier } }],
    PartyName: { Name: company.displayName ?? company.name },
    PostalAddress: {
      StreetName: company.registeredOffice.street,
      AdditionalStreetName: company.registeredOffice.houseNumber,
      CityName: company.registeredOffice.city,
      PostalZone: company.registeredOffice.postalCode,
      Country: { IdentificationCode: 'BE' },
    },
    PartyTaxScheme: { CompanyID: { value: company.vatNumber }, TaxScheme: { ID: 'VAT' } },
    PartyLegalEntity: { RegistrationName: company.name, CompanyID: { value: company.identifier } },
  };
}

function partnerParty(partner: PartnerDto): Party {
  const [scheme, identifier] = partner.peppolId.split(':');
  return {
    EndpointID: { __schemeID: scheme, value: identifier },
    PartyIdentification: [{ ID: { __schemeID: scheme, value: identifier } }],
    PartyName: { Name: partner.name },
    PostalAddress: {
      StreetName: partner.registeredOffice?.street,
      AdditionalStreetName: partner.registeredOffice?.houseNumber,
      CityName: partner.registeredOffice?.city,
      PostalZone: partner.registeredOffice?.postalCode,
      Country: { IdentificationCode: partner.registeredOffice?.countryCode ?? 'BE' },
    },
    PartyTaxScheme: { CompanyID: { value: partner.vatNumber }, TaxScheme: { ID: 'VAT' } },
    PartyLegalEntity: { RegistrationName: partner.name, CompanyID: { value: partner.identifier } },
  };
}

export const sellerParty = companyParty(seller);

export const eggFarmParty: Party = {
  EndpointID: { __schemeID: '0208', value: '0812345670' },
  PartyIdentification: [{ ID: { __schemeID: '0208', value: '0812345670' } }],
  PartyName: { Name: 'Hoeve Hendrickx' },
  PostalAddress: { StreetName: 'Kapelweg', AdditionalStreetName: '7', CityName: 'Zele', PostalZone: '9240', Country: { IdentificationCode: 'BE' } },
  PartyTaxScheme: { CompanyID: { value: 'BE0812345670' }, TaxScheme: { ID: 'VAT' } },
  PartyLegalEntity: { RegistrationName: 'Hoeve Hendrickx', CompanyID: { value: '0812345670' } },
};

export const brusselsHotelParty: Party = {
  EndpointID: { __schemeID: '9925', value: 'BE0599888777' },
  PartyName: { Name: 'Hôtel des Galeries Royales – Banqueting & Évènements' },
  PostalAddress: { StreetName: 'Galerie du Roi', AdditionalStreetName: '28', CityName: 'Bruxelles', PostalZone: '1000', Country: { IdentificationCode: 'BE' } },
  PartyTaxScheme: { CompanyID: { value: 'BE0599888777' }, TaxScheme: { ID: 'VAT' } },
  PartyLegalEntity: { RegistrationName: 'Hôtel des Galeries Royales SA', CompanyID: { value: '0599888777' } },
};

export const partyOf = {
  anker: partnerParty(anker),
  mosan: partnerParty(mosan),
  vandewiele: partnerParty(vandewiele),
  mokabon: partnerParty(mokabon),
  zwaan: partnerParty(zwaan),
};

export interface LineSpec {
  name: string;
  description?: string;
  quantity: number;
  price: number;
  vat?: number;
  category?: ClassifiedTaxCategory;
  discount?: { percent: number; reason?: string };
  charge?: { amount: number; reason?: string };
}

function taxCategory(spec: LineSpec): ClassifiedTaxCategory {
  if (spec.category) {
    return structuredClone(spec.category);
  }
  const percent = spec.vat ?? 21;
  return { ID: percent === 0 ? 'Z' : 'S', Percent: percent, TaxScheme: { ID: 'VAT' } };
}

function lineAllowanceCharges(spec: LineSpec): AllowanceCharge[] | undefined {
  const items: AllowanceCharge[] = [];
  if (spec.discount) {
    items.push({
      ChargeIndicator: false,
      AllowanceChargeReason: spec.discount.reason ?? 'Volume discount',
      MultiplierFactorNumeric: spec.discount.percent,
      Amount: { __currencyID: 'EUR', value: 0 },
    });
  }
  if (spec.charge) {
    items.push({
      ChargeIndicator: true,
      AllowanceChargeReason: spec.charge.reason ?? 'Packaging',
      Amount: { __currencyID: 'EUR', value: spec.charge.amount },
    });
  }
  return items.length ? items : undefined;
}

function baseLine(spec: LineSpec, index: number) {
  return {
    ID: String(index + 1),
    LineExtensionAmount: { __currencyID: 'EUR', value: 0 },
    AllowanceCharge: lineAllowanceCharges(spec),
    Item: { Name: spec.name, Description: spec.description, ClassifiedTaxCategory: taxCategory(spec) },
    Price: { PriceAmount: { __currencyID: 'EUR', value: spec.price } },
  };
}

export function invoiceLines(specs: LineSpec[]): InvoiceLine[] {
  return specs.map((spec, index) => {
    const line: InvoiceLine = { ...baseLine(spec, index), InvoicedQuantity: { __unitCode: 'C62', value: spec.quantity } };
    calculator.recalculateLineExtensionAmount(line);
    return line;
  });
}

export function creditNoteLines(specs: LineSpec[]): CreditNoteLine[] {
  return specs.map((spec, index) => {
    const line: CreditNoteLine = { ...baseLine(spec, index), CreditedQuantity: { __unitCode: 'C62', value: spec.quantity } };
    calculator.recalculateLineExtensionAmount(line);
    return line;
  });
}

export const generatedPdf: AdditionalDocumentReference = {
  ID: GENERATED_INVOICE,
  DocumentDescription: 'Generated Invoice PDF',
  Attachment: {
    EmbeddedDocumentBinaryObject: { __mimeCode: PDF_MIME_CODE, __filename: `${GENERATED_INVOICE}.pdf`, value: GENERATED_INVOICE_PLACEHOLDER },
  },
};

export const deliveryNotePdf: AdditionalDocumentReference = {
  ID: 'LB-2026-1029',
  DocumentDescription: 'Signed delivery note',
  Attachment: {
    EmbeddedDocumentBinaryObject: { __mimeCode: PDF_MIME_CODE, __filename: 'leveringsbon-oktober-brasserie-het-anker.pdf', value: 'JVBERi0xLjQKJcfsj6IKJSVFT0YK' },
  },
};

export const orderSpreadsheet: AdditionalDocumentReference = {
  ID: 'BST-118',
  DocumentDescription: 'Order lines',
  Attachment: {
    EmbeddedDocumentBinaryObject: {
      __mimeCode: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      __filename: 'bestelling-118.xlsx',
      value: 'UEsDBBQAAAAIAA==',
    },
  },
};

export const priceListLink: AdditionalDocumentReference = {
  ID: 'PRICES-2026',
  DocumentDescription: 'Price list 2026',
  Attachment: { ExternalReference: { URI: 'https://www.bakkerijdesmet.be/prijslijst-2026.pdf' } },
};

export const photoAttachment: AdditionalDocumentReference = {
  ID: 'IMG-0412',
  DocumentDescription: 'Photo of the wedding cake',
  Attachment: { EmbeddedDocumentBinaryObject: { __mimeCode: 'image/jpeg', __filename: 'bruidstaart.jpg', value: '/9j/4AAQSkZJRgABAQ==' } },
};

export interface DocumentSpec {
  id?: string;
  issueDate: string;
  dueDate?: string;
  supplier?: Party;
  customer: Party;
  lines: LineSpec[];
  buyerReference?: string;
  orderReference?: string;
  note?: string;
  paymentId?: string;
  paymentTerms?: string;
  paymentMeans?: Invoice['PaymentMeans'] | null;
  attachments?: AdditionalDocumentReference[];
  allowanceCharges?: AllowanceCharge[];
  delivery?: Delivery;
  billingReference?: { id: string; issueDate: string };
}

const CUSTOMIZATION_ID = 'urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0';
const PROFILE_ID = 'urn:fdc:peppol.eu:2017:poacc:billing:01:1.0';

function payeeOf(supplier: Party): Invoice['PaymentMeans'] {
  if (supplier === sellerParty) {
    return {
      PaymentMeansCode: { value: 30, __name: 'Credit Transfer' },
      PayeeFinancialAccount: { ID: seller.iban.replace(/\s/g, ''), Name: seller.paymentAccountName, FinancialInstitutionBranch: { ID: seller.bic } },
    };
  }
  if (supplier === partyOf.vandewiele) {
    return {
      PaymentMeansCode: { value: 30, __name: 'Credit Transfer' },
      PayeeFinancialAccount: { ID: 'BE43068244417101', Name: 'Molens Vandewiele BV', FinancialInstitutionBranch: { ID: 'GKCCBEBB' } },
    };
  }
  if (supplier === partyOf.mokabon) {
    return {
      PaymentMeansCode: { value: 30, __name: 'Credit Transfer' },
      PayeeFinancialAccount: { ID: 'BE62510007547061', Name: 'Koffiebranderij Mokabon NV' },
    };
  }
  return {
    PaymentMeansCode: { value: 30, __name: 'Credit Transfer' },
    PayeeFinancialAccount: { ID: 'BE09363184927057', Name: supplier.PartyName?.Name },
  };
}

export function composeInvoice(spec: DocumentSpec): Invoice {
  const supplier = spec.supplier ?? sellerParty;
  const paymentMeans = spec.paymentMeans === null ? undefined : structuredClone(spec.paymentMeans ?? payeeOf(supplier));
  if (paymentMeans && spec.paymentId) {
    paymentMeans.PaymentID = spec.paymentId;
  }
  const invoice: Invoice = {
    CustomizationID: CUSTOMIZATION_ID,
    ProfileID: PROFILE_ID,
    ID: spec.id ?? '',
    IssueDate: spec.issueDate,
    DueDate: spec.dueDate,
    InvoiceTypeCode: 380,
    Note: spec.note,
    DocumentCurrencyCode: 'EUR',
    BuyerReference: spec.buyerReference,
    OrderReference: spec.orderReference ? { ID: spec.orderReference } : undefined,
    BillingReference: undefined,
    AdditionalDocumentReference: structuredClone(spec.attachments ?? [generatedPdf]),
    AccountingSupplierParty: { Party: structuredClone(supplier) },
    AccountingCustomerParty: { Party: structuredClone(spec.customer) },
    Delivery: spec.delivery,
    PaymentMeans: paymentMeans,
    PaymentTerms: spec.paymentTerms ? { Note: spec.paymentTerms } : undefined,
    AllowanceCharge: structuredClone(spec.allowanceCharges),
    LegalMonetaryTotal: { PayableAmount: { __currencyID: 'EUR', value: 0 } },
    InvoiceLine: invoiceLines(spec.lines),
  };
  calculator.calculateTaxAndTotals(invoice);
  return invoice;
}

export function composeCreditNote(spec: DocumentSpec): CreditNote {
  const creditNote: CreditNote = {
    CustomizationID: CUSTOMIZATION_ID,
    ProfileID: PROFILE_ID,
    ID: spec.id ?? '',
    IssueDate: spec.issueDate,
    CreditNoteTypeCode: 381,
    Note: spec.note ?? 'VAT to be refunded to the State to the extent that it was originally deducted',
    DocumentCurrencyCode: 'EUR',
    BuyerReference: spec.buyerReference,
    OrderReference: spec.orderReference ? { ID: spec.orderReference } : undefined,
    BillingReference: spec.billingReference
      ? [{ InvoiceDocumentReference: { ID: spec.billingReference.id, IssueDate: spec.billingReference.issueDate } }]
      : undefined,
    AdditionalDocumentReference: structuredClone(spec.attachments ?? [generatedPdf]),
    AccountingSupplierParty: { Party: structuredClone(spec.supplier ?? sellerParty) },
    AccountingCustomerParty: { Party: structuredClone(spec.customer) },
    Delivery: spec.delivery,
    AllowanceCharge: structuredClone(spec.allowanceCharges),
    LegalMonetaryTotal: { PayableAmount: { __currencyID: 'EUR', value: 0 } },
    CreditNoteLine: creditNoteLines(spec.lines),
  };
  calculator.calculateTaxAndTotals(creditNote);
  return creditNote;
}

export interface DocumentState {
  id: string;
  type?: DocumentType;
  direction?: DocumentDirection;
  draftedOn?: string;
  scheduledOn?: string;
  proxyOn?: string;
  processedOn?: string;
  processedStatus?: string;
  paidOn?: string;
  readOn?: string;
  errorSeenOn?: string;
  createdExternally?: boolean;
}

function partnerOf(doc: UBLDoc, direction: DocumentDirection, type: DocumentType): Party {
  const selfBilling = type === DocumentType.SELF_BILLING_INVOICE || type === DocumentType.SELF_BILLING_CREDIT_NOTE;
  const partnerIsSupplier = (direction === DocumentDirection.INCOMING) !== selfBilling;
  return partnerIsSupplier ? doc.AccountingSupplierParty.Party : doc.AccountingCustomerParty.Party;
}

export function documentDto(doc: UBLDoc, state: DocumentState): DocumentDto {
  const type = state.type ?? ('CreditNoteTypeCode' in doc ? DocumentType.CREDIT_NOTE : DocumentType.INVOICE);
  const direction = state.direction ?? DocumentDirection.OUTGOING;
  const partner = partnerOf(doc, direction, type);
  const totals = doc.LegalMonetaryTotal;
  return {
    id: state.id,
    direction,
    type,
    ownerPeppolId: seller.peppolId,
    partnerPeppolId: `${partner.EndpointID?.__schemeID}:${partner.EndpointID?.value}`,
    partnerName: partner.PartyName?.Name,
    invoiceReference: doc.ID || undefined,
    currency: 'EUR',
    amountExclVat: totals.TaxExclusiveAmount?.value,
    amountInclVat: totals.TaxInclusiveAmount?.value,
    issueDate: doc.IssueDate,
    dueDate: doc.DueDate,
    ubl: isCreditNoteType(type) ? buildCreditNoteXml(doc as CreditNote) : buildInvoiceXml(doc as Invoice),
    draftedOn: state.draftedOn,
    scheduledOn: state.scheduledOn,
    proxyOn: state.proxyOn ?? (state.processedOn && direction === DocumentDirection.OUTGOING ? state.processedOn : undefined),
    processedOn: state.processedOn,
    processedStatus: state.processedStatus,
    paidOn: state.paidOn,
    readOn: state.readOn,
    errorSeenOn: state.errorSeenOn,
    createdExternally: state.createdExternally,
  };
}

const bakeryOrder: LineSpec[] = [
  { name: 'Desembrood volkoren', description: 'Daily delivery, 800 g', quantity: 40, price: 3.6, vat: 6 },
  { name: 'Croissant roomboter', quantity: 120, price: 1.45, vat: 6 },
  { name: 'Levering en opstelling', quantity: 1, price: 45, vat: 21 },
];

const cateringOrder: LineSpec[] = [
  { name: 'Broodjeslunch per persoon', description: 'Drie belegde broodjes, soep en fruit', quantity: 24, price: 14.5, vat: 12 },
  { name: 'Taart van de week (12 personen)', description: 'Wisselend seizoensfruit op frangipane', quantity: 2, price: 32, vat: 6 },
  { name: 'Levering en opstelling', quantity: 1, price: 45, vat: 21 },
];

export const sentInvoice = composeInvoice({
  id: 'INV-2026-0139',
  issueDate: day(-12),
  dueDate: moment(day(-12)).endOf('month').add(30, 'day').format('YYYY-MM-DD'),
  customer: partyOf.mokabon,
  buyerReference: 'MOK-PO-55871',
  paymentTerms: '30 days after end of month',
  paymentId: structuredCommunication(2026013900),
  lines: [
    { name: 'Croissant roomboter', quantity: 300, price: 1.45, vat: 6, discount: { percent: 10, reason: 'Volume discount' } },
    { name: 'Croissant amandel', quantity: 150, price: 1.95, vat: 6 },
    { name: 'Levering en opstelling', quantity: 3, price: 45, vat: 21 },
  ],
  attachments: [generatedPdf, deliveryNotePdf],
});

export const overdueInvoice = composeInvoice({
  id: 'INV-2026-0136',
  issueDate: day(-45),
  dueDate: day(-15),
  customer: partyOf.anker,
  buyerReference: 'ANKER-2026-104',
  paymentTerms: '30 days',
  paymentId: structuredCommunication(2026013600),
  lines: bakeryOrder,
});

export const paidInvoice = composeInvoice({
  id: 'INV-2026-0138',
  issueDate: day(-20),
  dueDate: day(10),
  customer: partyOf.zwaan,
  orderReference: 'Zwaan wk 43',
  paymentTerms: '30 days',
  lines: [
    { name: 'Taart van de week (12 personen)', quantity: 3, price: 32, vat: 6 },
    { name: 'Desembrood volkoren', quantity: 14, price: 3.6, vat: 6 },
  ],
});

export const scheduledInvoice = composeInvoice({
  id: 'INV-2026-0141',
  issueDate: day(0),
  dueDate: day(30),
  customer: partyOf.anker,
  buyerReference: 'ANKER-2026-118',
  paymentTerms: '30 days',
  paymentId: structuredCommunication(2026014100),
  lines: bakeryOrder,
});

export const sendingInvoice = composeInvoice({
  id: 'INV-2026-0140',
  issueDate: day(0),
  dueDate: day(14),
  customer: partyOf.mosan,
  buyerReference: 'MOSAN-BQT-2211',
  paymentTerms: '14 days',
  lines: cateringOrder,
});

export const erroredInvoice = composeInvoice({
  id: 'INV-2026-0137',
  issueDate: day(-8),
  dueDate: day(6),
  customer: partyOf.mosan,
  buyerReference: 'MOSAN-BQT-2187',
  paymentTerms: '14 days',
  lines: cateringOrder,
});

export const seenErrorInvoice = composeInvoice({
  id: 'INV-2026-0135',
  issueDate: day(-33),
  dueDate: day(-19),
  customer: partyOf.mosan,
  buyerReference: 'MOSAN-BQT-2101',
  lines: [{ name: 'Broodjeslunch per persoon', quantity: 40, price: 14.5, vat: 12 }],
});

export const sentCreditNote = composeCreditNote({
  id: 'CN-2026-0007',
  issueDate: day(-10),
  customer: partyOf.anker,
  buyerReference: 'ANKER-2026-104',
  billingReference: { id: 'INV-2026-0136', issueDate: day(-45) },
  lines: [{ name: 'Desembrood volkoren', description: 'Returned: burnt crust', quantity: 6, price: 3.6, vat: 6 }],
});

export const selfBilledInvoice = composeInvoice({
  id: 'SB-2026-031',
  issueDate: day(-5),
  dueDate: day(25),
  supplier: eggFarmParty,
  customer: sellerParty,
  orderReference: 'Eieren oktober',
  note: 'Self-billing: invoice issued by the customer on behalf of the supplier.',
  lines: [
    { name: 'Scharreleieren (tray of 30)', quantity: 48, price: 7.8, vat: 6 },
    { name: 'Eendeneieren (tray of 6)', quantity: 10, price: 4.2, vat: 6 },
  ],
});

export const uploadedInvoice = composeInvoice({
  id: 'INV-2026-0134',
  issueDate: day(-26),
  dueDate: day(4),
  customer: partyOf.mokabon,
  buyerReference: 'MOK-PO-55790',
  lines: [{ name: 'Koffiekoeken assortiment', quantity: 200, price: 1.6, vat: 6 }],
  attachments: [deliveryNotePdf],
});

export const receivedInvoice = composeInvoice({
  id: 'MV-26-1182',
  issueDate: day(-3),
  dueDate: day(27),
  supplier: partyOf.vandewiele,
  customer: sellerParty,
  buyerReference: 'Bakkerij De Smet',
  paymentTerms: '30 days',
  paymentId: structuredCommunication(2611820001),
  lines: [
    { name: 'Tarwebloem T65 (25 kg)', quantity: 20, price: 21.4, vat: 6 },
    { name: 'Volkorenmeel steengemalen (25 kg)', quantity: 8, price: 26.9, vat: 6 },
    { name: 'Palletvergoeding', quantity: 1, price: 12.5, vat: 21 },
  ],
  attachments: [{ ...deliveryNotePdf, ID: 'MV-LB-88213', Attachment: { EmbeddedDocumentBinaryObject: { __mimeCode: PDF_MIME_CODE, __filename: 'MV-26-1182.pdf', value: 'JVBERi0xLjQKJcfsj6IKJSVFT0YK' } } }],
});

export const overdueReceivedInvoice = composeInvoice({
  id: 'MV-26-1104',
  issueDate: day(-40),
  dueDate: day(-10),
  supplier: partyOf.vandewiele,
  customer: sellerParty,
  buyerReference: 'Bakkerij De Smet',
  lines: [{ name: 'Tarwebloem T65 (25 kg)', quantity: 24, price: 21.4, vat: 6 }],
});

export const paidReceivedInvoice = composeInvoice({
  id: 'MOK-2026-5521',
  issueDate: day(-18),
  dueDate: day(12),
  supplier: partyOf.mokabon,
  customer: sellerParty,
  buyerReference: 'DESMET-KOFFIE',
  lines: [
    { name: 'Espressobonen Huisblend (1 kg)', quantity: 12, price: 24.5, vat: 6 },
    { name: 'Onderhoud espressomachine', quantity: 1, price: 95, vat: 21 },
  ],
});

export const receivedCreditNote = composeCreditNote({
  id: 'MV-CN-26-019',
  issueDate: day(-2),
  supplier: partyOf.vandewiele,
  customer: sellerParty,
  buyerReference: 'Bakkerij De Smet',
  billingReference: { id: 'MV-26-1104', issueDate: day(-40) },
  lines: [{ name: 'Tarwebloem T65 (25 kg)', description: 'Damaged bags', quantity: 2, price: 21.4, vat: 6 }],
});

export const draftInvoice = composeInvoice({
  issueDate: day(0),
  dueDate: day(30),
  customer: partyOf.anker,
  buyerReference: 'ANKER-2026-121',
  paymentTerms: '30 days',
  paymentId: structuredCommunication(2026014200),
  lines: [
    ...bakeryOrder,
    { name: 'Taart van de week (12 personen)', description: 'Verjaardag chef, met kaarsjes', quantity: 1, price: 32, vat: 6, charge: { amount: 4.5, reason: 'Gift box' } },
  ],
  allowanceCharges: [
    { ChargeIndicator: false, AllowanceChargeReason: 'Loyal customer', MultiplierFactorNumeric: 5, Amount: { __currencyID: 'EUR', value: 0 }, TaxCategory: { ID: 'S', Percent: 6, TaxScheme: { ID: 'VAT' } } },
  ],
  attachments: [generatedPdf, orderSpreadsheet, priceListLink],
});

export const draftCreditNote = composeCreditNote({
  issueDate: day(0),
  customer: partyOf.anker,
  buyerReference: 'ANKER-2026-104',
  billingReference: { id: 'INV-2026-0136', issueDate: day(-45) },
  lines: [{ name: 'Croissant roomboter', description: 'Delivered after opening time on Sunday', quantity: 40, price: 1.45, vat: 6 }],
});

export const incompleteDraft = composeInvoice({
  issueDate: day(0),
  dueDate: day(-3),
  customer: { ...brusselsHotelParty, PartyLegalEntity: { CompanyID: { value: '0599888777' } } },
  paymentMeans: { PaymentMeansCode: { value: 30, __name: 'Credit Transfer' }, PayeeFinancialAccount: { Name: seller.paymentAccountName } },
  lines: [
    { name: 'Bruidstaart drie verdiepingen', description: 'Framboos en witte chocolade', quantity: 1, price: 0, vat: 6 },
    { name: '', quantity: 0, price: 0, vat: 21 },
  ],
  attachments: [generatedPdf, photoAttachment],
});

function withExemptionReason<T extends UBLDoc>(doc: T, categoryId: string, reason: string): T {
  for (const subtotal of doc.TaxTotal?.[0]?.TaxSubtotal ?? []) {
    if (subtotal.TaxCategory?.ID === categoryId) {
      subtotal.TaxCategory.TaxExemptionReason = reason;
    }
  }
  return doc;
}

export const intraCommunityDraft = withExemptionReason(composeInvoice({
  issueDate: day(0),
  dueDate: day(30),
  customer: {
    ...brusselsHotelParty,
    EndpointID: { __schemeID: '9930', value: 'DE811907980' },
    PartyName: { Name: 'Bäckerei Schmitz GmbH' },
    PostalAddress: { StreetName: 'Aachener Straße', AdditionalStreetName: '12', CityName: 'Köln', PostalZone: '50674', Country: { IdentificationCode: 'DE' } },
    PartyTaxScheme: { CompanyID: { value: 'DE811907980' }, TaxScheme: { ID: 'VAT' } },
    PartyLegalEntity: { RegistrationName: 'Bäckerei Schmitz GmbH', CompanyID: { value: 'HRB 12345' } },
  },
  buyerReference: 'SCHMITZ-4471',
  paymentTerms: '30 days',
  lines: [
    { name: 'Desemstarter (1 kg)', description: 'Levend zuurdesem, gekoeld verzonden', quantity: 10, price: 18, category: createZeroVatCategory('K') },
    { name: 'Verzending gekoeld', quantity: 1, price: 35, category: createZeroVatCategory('K') },
  ],
  delivery: { ActualDeliveryDate: day(-1), DeliveryLocation: { Address: { Country: { IdentificationCode: 'DE' } } } },
}), 'K', 'Intra-community supply, article 39bis of the Belgian VAT Code');

export const docs = {
  scheduled: documentDto(scheduledInvoice, { id: 'b1f0c2d4-0141', proxyOn: at(0, '21:40'), scheduledOn: at(1, '07:00') }),
  sending: documentDto(sendingInvoice, { id: 'b1f0c2d4-0140', proxyOn: at(0, '08:12') }),
  sent: documentDto(sentInvoice, { id: 'b1f0c2d4-0139', processedOn: at(-12, '10:04') }),
  paid: documentDto(paidInvoice, { id: 'b1f0c2d4-0138', processedOn: at(-20, '16:41'), paidOn: at(-3, '11:00') }),
  errored: documentDto(erroredInvoice, {
    id: 'b1f0c2d4-0137',
    processedOn: at(-8, '09:15'),
    processedStatus: 'Receiver 0208:0459876543 rejected the document: the buyer reference MOSAN-BQT-2187 is unknown.',
  }),
  overdue: documentDto(overdueInvoice, { id: 'b1f0c2d4-0136', processedOn: at(-45, '14:22') }),
  seenError: documentDto(seenErrorInvoice, {
    id: 'b1f0c2d4-0135',
    processedOn: at(-33, '09:00'),
    processedStatus: 'No Peppol endpoint found for 0208:0459876543 with document type Invoice.',
    errorSeenOn: at(-30, '13:20'),
  }),
  uploaded: documentDto(uploadedInvoice, { id: 'b1f0c2d4-0134', processedOn: at(-26, '12:00'), paidOn: at(-6, '09:00'), createdExternally: true }),
  creditNote: documentDto(sentCreditNote, { id: 'c7a1e9f3-0007', processedOn: at(-10, '15:30') }),
  selfBilled: documentDto(selfBilledInvoice, { id: 'd2c4a8b0-0031', type: DocumentType.SELF_BILLING_INVOICE, processedOn: at(-5, '10:10'), createdExternally: true }),
  received: documentDto(receivedInvoice, { id: 'e9b3f1a7-1182', direction: DocumentDirection.INCOMING, processedOn: at(-3, '06:48') }),
  receivedOverdue: documentDto(overdueReceivedInvoice, { id: 'e9b3f1a7-1104', direction: DocumentDirection.INCOMING, processedOn: at(-40, '06:51'), readOn: at(-39, '08:00') }),
  receivedPaid: documentDto(paidReceivedInvoice, { id: 'e9b3f1a7-5521', direction: DocumentDirection.INCOMING, processedOn: at(-18, '17:30'), paidOn: at(-9, '10:00'), readOn: at(-17, '08:00') }),
  receivedCreditNote: documentDto(receivedCreditNote, { id: 'e9b3f1a7-c019', direction: DocumentDirection.INCOMING, processedOn: at(-2, '07:05') }),
  draft: documentDto(draftInvoice, { id: 'f4d2b6c8-d001', draftedOn: at(0, '08:40') }),
  draftCreditNote: documentDto(draftCreditNote, { id: 'f4d2b6c8-d002', draftedOn: at(-1, '17:05') }),
  incompleteDraft: documentDto(incompleteDraft, { id: 'f4d2b6c8-d003', draftedOn: at(-2, '12:30') }),
  intraCommunityDraft: documentDto(intraCommunityDraft, { id: 'f4d2b6c8-d004', draftedOn: at(0, '07:55') }),
};

export const overviewDocuments: DocumentDto[] = [
  docs.scheduled,
  docs.sending,
  docs.received,
  docs.receivedCreditNote,
  docs.sent,
  docs.selfBilled,
  docs.paid,
  docs.creditNote,
  docs.errored,
  docs.receivedPaid,
  docs.uploaded,
  docs.seenError,
  docs.overdue,
  docs.receivedOverdue,
  docs.draft,
  docs.draftCreditNote,
  docs.incompleteDraft,
  docs.intraCommunityDraft,
];

const archiveCustomers = [partyOf.anker, partyOf.mosan, partyOf.mokabon, partyOf.zwaan];

export const archiveDocuments: DocumentDto[] = [
  ...overviewDocuments,
  ...Array.from({ length: 40 }, (_, index) => {
    const number = 133 - index;
    const issued = -50 - index * 4;
    const invoice = composeInvoice({
      id: `INV-2026-${String(number).padStart(4, '0')}`,
      issueDate: day(issued),
      dueDate: day(issued + 30),
      customer: archiveCustomers[index % archiveCustomers.length],
      buyerReference: `PO-${7400 + number}`,
      lines: [
        { name: 'Desembrood volkoren', quantity: 20 + (index % 7) * 5, price: 3.6, vat: 6 },
        { name: 'Croissant roomboter', quantity: 60 + (index % 5) * 20, price: 1.45, vat: 6 },
      ],
    });
    return documentDto(invoice, {
      id: `a0a0a0a0-${number}`,
      processedOn: at(issued, '10:00'),
      paidOn: index % 6 === 3 ? undefined : at(issued + 20, '09:00'),
    });
  }),
];

export const deliveryDetails: DocumentDetailsDto = {
  id: '6f1c2b9e-4d7a-4e0b-9a51-0e2f7c3d8b14',
  invoiceReference: 'INV-2026-0139',
  ownerPeppolId: seller.peppolId,
  partnerPeppolId: '0208:0412345678',
  accessPoint: 'Let’s Peppol',
  accessPointId: '0b8e1d4c-73f2-4a6e-b1d9-5c2a7e9f4038@letspeppol.org',
  processedOn: at(-12, '10:04'),
  partnerPeppolAccessPoint: 'Kanaal AP (PBE000412)',
  partnerPeppolMessageId: 'a4c9e271-58b0-4f3d-9e6a-2d81c0f7b5e3@kanaal-ap.be',
  partnerPeppolMessageOn: at(-12, '10:04'),
};

export const rejectedDeliveryDetails: DocumentDetailsDto = {
  id: '91d4e7a2-3b6c-4f85-a0e9-7c2d5b8f1a63',
  invoiceReference: 'INV-2026-0137',
  ownerPeppolId: seller.peppolId,
  partnerPeppolId: '0208:0459876543',
  accessPoint: 'Let’s Peppol',
  accessPointId: '3e7a9c1b-02d4-4f6e-8b5a-9d1c7e2f4a80@letspeppol.org',
  processedOn: at(-8, '09:15'),
  processedStatus: 'Receiver 0208:0459876543 rejected the document: the buyer reference MOSAN-BQT-2187 is unknown.',
};

export const validResult: ValidationResultDto = {
  isValid: true,
  errorCount: 0,
  errors: [],
  vesId: 'eu.peppol.bis3:invoice:2025.5',
  detectedVESIDRaw: 'urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0',
};

export const failedValidation: ValidationResultDto = {
  isValid: false,
  errorCount: 3,
  vesId: 'eu.peppol.bis3:invoice:2025.5',
  detectedVESIDRaw: 'urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0',
  errors: [
    {
      line: 1,
      column: 1,
      severity: 'ERROR',
      message: '[BR-CO-15]-Invoice total amount with VAT (BT-112) = Invoice total amount without VAT (BT-109) + Invoice total VAT amount (BT-110).',
    },
    {
      line: 1,
      column: 1,
      severity: 'ERROR',
      message: '[BR-S-08]-For each different value of VAT category rate (BT-119) where the VAT category code (BT-118) is "Standard rated", the VAT category taxable amount (BT-116) in a VAT breakdown (BG-23) shall equal the sum of Invoice line net amounts (BT-131) plus the sum of document level charge amounts (BT-99) minus the sum of document level allowance amounts (BT-92) where the VAT category code (BT-151, BT-102, BT-95) is "Standard rated" and the VAT rate (BT-152, BT-103, BT-96) equals the VAT category rate (BT-119).',
    },
    {
      line: 1,
      column: 1,
      severity: 'ERROR',
      message: '[PEPPOL-EN16931-R120]-Invoice line net amount MUST equal (Invoiced quantity * (Item net price/item price base quantity) + Sum of invoice line charge amount - sum of invoice line allowance amount',
    },
  ],
};

export const uploadValidation: ValidationResultDto = {
  isValid: false,
  errorCount: 2,
  vesId: 'eu.peppol.bis3:invoice:2025.5',
  detectedVESIDRaw: 'urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0',
  errors: [
    { line: 14, column: 38, severity: 'ERROR', message: '[PEPPOL-EN16931-R003]-A buyer reference or purchase order reference MUST be provided.' },
    {
      line: 52,
      column: 7,
      severity: 'ERROR',
      message: '[BR-61]-If the Payment means type code (BT-81) means SEPA credit transfer, Local credit transfer or Non-SEPA international credit transfer, the Payment account identifier (BT-84) shall be present.',
    },
  ],
};

export const donationStats: DonationStatsDto = {
  totalContributions: 4180,
  totalProcessed: 128_412,
  processedToday: 1_874,
  maxProcessedLastWeek: 2_310,
  invoicesRemaining: 38_000,
  activeCompanies: 6_215,
  transactions: [],
  contributions: [
    { name: 'Boekhoudkantoor Peeters', message: 'Thanks for keeping this free', amount: 50, currency: 'EUR', date: at(-2) },
    { name: 'Loodgieter Janssens', message: '', amount: 20, currency: 'EUR', date: at(-4) },
  ],
};

function matches(doc: DocumentDto, query: DocumentQuery): boolean {
  const now = moment();
  if (!!query.draft !== !!doc.draftedOn) return false;
  if (query.direction && doc.direction !== query.direction) return false;
  if (query.type && doc.type !== query.type) return false;
  if (query.paid !== undefined && !!doc.paidOn !== query.paid) return false;
  if (query.errored && !doc.processedStatus) return false;
  if (query.overdue && (doc.paidOn || !doc.dueDate || !now.isAfter(moment(doc.dueDate)) || doc.processedStatus)) return false;
  if (query.partnerPeppolId && doc.partnerPeppolId !== query.partnerPeppolId) return false;
  if (query.partnerName && !doc.partnerName?.toLowerCase().includes(query.partnerName.toLowerCase())) return false;
  if (query.invoiceReference && !doc.invoiceReference?.toLowerCase().includes(query.invoiceReference.toLowerCase())) return false;
  return true;
}

export function documentPage(documents: DocumentDto[], query: DocumentQuery): DocumentPageDto {
  const filtered = documents.filter((doc) => matches(doc, query));
  const sort = query.pageable.sort?.[0];
  if (sort) {
    const key = sort.property as keyof DocumentDto;
    const factor = sort.direction === 'asc' ? 1 : -1;
    filtered.sort((left, right) => String(left[key] ?? '').localeCompare(String(right[key] ?? ''), 'en', { numeric: true }) * factor);
  }
  const { page, size } = query.pageable;
  const totalPages = Math.ceil(filtered.length / size);
  return {
    content: structuredClone(filtered.slice(page * size, (page + 1) * size)),
    page,
    size,
    totalElements: filtered.length,
    totalPages,
    last: page >= totalPages - 1,
  };
}

function newDocumentFromXml(xml: string, draft: boolean, createdExternally: boolean): DocumentDto {
  const kind = detectUblDocumentKind(xml);
  const doc = kind === 'CreditNote' ? parseCreditNote(xml) : parseInvoice(xml);
  const dto = documentDto(doc, {
    id: `new-${Date.now()}`,
    draftedOn: draft ? moment().toISOString() : undefined,
    proxyOn: draft ? undefined : moment().toISOString(),
    createdExternally,
  });
  return { ...dto, ubl: xml };
}

export interface InvoiceServiceOptions {
  documents?: DocumentDto[];
  getDocuments?: (query: DocumentQuery) => Promise<DocumentPageDto>;
  validation?: ValidationResultDto;
  details?: DocumentDetailsDto;
  delayMs?: number;
}

export function fakeInvoiceService(options: InvoiceServiceOptions = {}): InvoiceService {
  const store = structuredClone(options.documents ?? overviewDocuments);
  const delay = options.delayMs ?? 120;
  const later = <T>(value: T) => new Promise<T>((done) => setTimeout(() => done(structuredClone(value)), delay));
  const find = (id: string) => store.find((doc) => doc.id === id);
  return stub<InvoiceService>('InvoiceService', {
    getDocuments: options.getDocuments ?? ((query: DocumentQuery) => later(documentPage(store, query))),
    getDocument: (id: string) => later(find(id)),
    getDocumentDetails: (id: string) => later(options.details ?? (find(id)?.processedStatus ? rejectedDeliveryDetails : deliveryDetails)),
    validate: () => later(options.validation ?? validResult),
    createDocument: async (xml: string, draft = false, createdExternally = false) => later(newDocumentFromXml(xml, draft, createdExternally)),
    updateDocument: async (id: string, xml: string, draft = false) => later({
      ...find(id),
      ubl: xml,
      draftedOn: draft ? moment().toISOString() : undefined,
      proxyOn: draft ? undefined : moment().toISOString(),
    }),
    sendDocument: (id: string) => later({ ...find(id), draftedOn: undefined, proxyOn: moment().toISOString() }),
    recordVatReasonSelections: async () => undefined,
    markReadDocument: (id: string) => later({ ...find(id), readOn: moment().toISOString() }),
    togglePaidDocument: (id: string) => later({ ...find(id), paidOn: find(id)?.paidOn ? undefined : moment().toISOString() }),
    markErrorSeenDocument: (id: string) => later({ ...find(id), errorSeenOn: moment().toISOString() }),
    deleteDocument: async () => new Response(null, { status: 204 }),
    deleteInvoiceDraft: async () => new Response(null, { status: 204 }),
    downloadPdf: async () => new Response(new Blob(['%PDF-1.4'], { type: 'application/pdf' })),
  });
}

export function fakePartnerService(partners: PartnerDto[] = invoicePartners): PartnerService {
  return stub<PartnerService>('PartnerService', {
    getPartners: resolved(partners, 80),
    searchPartners: async ({ peppolId }: { peppolId: string }) => structuredClone(partners.filter((partner) => partner.peppolId === peppolId)),
    createPartner: async (partner: PartnerDto) => ({ ...partner, id: 99 }),
  });
}

export interface InvoiceStoryOptions extends InvoiceServiceOptions {
  session?: SessionOptions;
  partners?: PartnerDto[];
  products?: ProductDto[];
  setup?: (context: InvoiceContext) => void | Promise<void>;
}

export function invoiceMocks(options: InvoiceStoryOptions = {}): Array<IRegistration | IRegistry> {
  return [
    ...sessionMocks({ company: seller, path: `/${seller.peppolId}/invoices`, ...options.session }),
    provide(InvoiceService, fakeInvoiceService(options)),
    provide(PartnerService, fakePartnerService(options.partners)),
    provide(ProductService, stub<ProductService>('ProductService', { getProducts: resolved(options.products ?? invoiceProducts, 60) })),
    provide(CompanySearchService, stub<CompanySearchService>('CompanySearchService', {
      searchCompany: async (params: { companyName?: string; peppolId?: string; vatNumber?: string; identifier?: string }) =>
        structuredClone(kycCompanies.filter((company) =>
          (params.companyName && company.name.toLowerCase().includes(params.companyName.toLowerCase()))
          || (params.peppolId && company.peppolId === params.peppolId)
          || (params.vatNumber && company.vatNumber === params.vatNumber)
          || (params.identifier && company.identifier === params.identifier))),
    })),
    provide(StatisticsService, stub<StatisticsService>('StatisticsService', { getDonationStats: resolved(donationStats) })),
    provide(SponsorService, stub<SponsorService>('SponsorService', { getSponsors: resolved({ sponsors: [] }) })),
    AppTask.activating(IContainer, async (container) => {
      await container.get(I18N).initPromise;
      const context = container.get(InvoiceContext);
      await options.setup?.(context);
      Object.assign(container.get(IAppRoot).controller.viewModel, { invoiceContext: context });
    }),
  ];
}

export function openDocument(dto: DocumentDto) {
  return (context: InvoiceContext) => {
    context.selectInvoice(structuredClone(dto));
  };
}

export function startNewDocument(type: DocumentType = DocumentType.INVOICE, fill?: (doc: UBLDoc) => void) {
  return (context: InvoiceContext) => {
    context.newUBLDocument(type);
    context.getLastInvoiceReference();
    if (fill) {
      const doc = context.selectedInvoice;
      fill(doc);
      calculator.calculateTaxAndTotals(doc);
      context.selectedInvoice = undefined;
      context.selectedInvoice = doc;
    }
  };
}

export function withDocument(doc: UBLDoc, type?: DocumentType) {
  return (context: InvoiceContext) => {
    context.selectedDocumentType = type ?? ('CreditNoteTypeCode' in doc ? DocumentType.CREDIT_NOTE : DocumentType.INVOICE);
    context.readOnly = false;
    context.selectedInvoice = structuredClone(doc);
    context.getLastInvoiceReference();
  };
}

export const loadingDocuments = pending<DocumentPageDto>();

export const adjustedInvoice = composeInvoice({
  issueDate: day(0),
  dueDate: day(30),
  customer: partyOf.mosan,
  buyerReference: 'MOSAN-BQT-2240',
  paymentTerms: '14 days',
  lines: cateringOrder,
  allowanceCharges: [
    { ChargeIndicator: false, AllowanceChargeReason: 'Loyal customer', MultiplierFactorNumeric: 5, Amount: { __currencyID: 'EUR', value: 0 }, TaxCategory: { ID: 'S', Percent: 12, TaxScheme: { ID: 'VAT' } } },
    { ChargeIndicator: true, AllowanceChargeReason: 'Sunday delivery', Amount: { __currencyID: 'EUR', value: 15 }, TaxCategory: { ID: 'S', Percent: 21, TaxScheme: { ID: 'VAT' } } },
    { ChargeIndicator: false, Amount: { __currencyID: 'EUR', value: 10 }, TaxCategory: { ID: 'S', Percent: 6, TaxScheme: { ID: 'VAT' } } },
  ],
});
