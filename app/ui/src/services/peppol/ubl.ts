export type UBLLine = InvoiceLine | CreditNoteLine;
export type UBLDoc = Invoice | CreditNote;

export function getLines(doc: UBLDoc): UBLLine[] {
    if (!doc) {
        return undefined;
    }
    if ("InvoiceTypeCode" in doc) {
        return doc.InvoiceLine
    } else {
        return doc.CreditNoteLine;
    }
}

export function getAmount(line: UBLLine): Quantity {
    if (!line) {
        return undefined;
    }
    if ("InvoicedQuantity" in line) {
        return line.InvoicedQuantity;
    } else if ("CreditedQuantity" in line) {
        return line.CreditedQuantity;
    }
}

export interface InvoiceLine extends UBLBaseLine {
    InvoicedQuantity?: Quantity;
}

export interface Invoice extends UBLBase {
    InvoiceTypeCode: number;
    PaymentMeans?: PaymentMeans;
    InvoiceLine: InvoiceLine[];
    AdditionalDocumentReference: AdditionalDocumentReference[];
}

export interface CreditNoteLine extends UBLBaseLine {
    CreditedQuantity?: Quantity;
}

export interface CreditNote extends UBLBase {
    CreditNoteTypeCode: number;
    PaymentMeans?: CreditNotePaymentMeans;
    CreditNoteLine: CreditNoteLine[];
    AdditionalDocumentReference: AdditionalDocumentReference[];
}

export interface UBLBase {
    CustomizationID: string;
    ProfileID: string;
    ID: string;
    IssueDate: string;
    DueDate?: string;
    Note?: string;
    DocumentCurrencyCode?: string;
    AccountingCost?: string;
    BuyerReference?: string;
    BillingReference: BillingReference[];
    AccountingSupplierParty: AccountingParty;
    AccountingCustomerParty: AccountingParty;
    Delivery?: Delivery;
    PaymentTerms?: PaymentTerms;
    AllowanceCharge?: AllowanceCharge[];
    TaxTotal?: TaxTotal[];
    LegalMonetaryTotal: MonetaryTotal;
}

export interface UBLBaseLine {
    ID: string;
    LineExtensionAmount: Amount;
    AccountingCost?: string;
    OrderLineReference?: OrderLineReference;
    Item: Item;
    Price: Price;
}

export interface Identifier {
    __schemeID?: string;
    value: string;
}

export interface Quantity {
    __unitCode?: string;
    value: number;
}

export interface Amount {
    __currencyID?: string;
    value: number;
}

export interface Address {
    StreetName?: string;
    AdditionalStreetName?: string;
    CityName?: string;
    PostalZone?: string;
    Country?: { IdentificationCode: string };
}

export interface AdditionalDocumentReference {
    ID: string;
    DocumentDescription?: string;
    Attachment?: Attachment;
}

export interface Attachment {
    EmbeddedDocumentBinaryObject?: EmbeddedDocumentBinaryObject;
    ExternalReference?: ExternalReference;
}

export interface ExternalReference {
    URI: string
}

export interface EmbeddedDocumentBinaryObject {
    __mimeCode: string;
    __filename: string;
    value: string;
}

export interface PartyTaxScheme {
    CompanyID: Identifier
    TaxScheme?: { ID: string };
}

export interface PartyLegalEntity {
    RegistrationName?: string;
    CompanyID?: Identifier;
}

export interface Contact {
    Name?: string;
    Telephone?: string;
    ElectronicMail?: string;
}

export interface PartyIdentification {
    ID: Identifier
}

export interface Party {
    EndpointID?: Identifier;
    PartyIdentification?: PartyIdentification[];
    PartyName?: { Name: string };
    PostalAddress?: Address;
    PartyTaxScheme?: PartyTaxScheme;
    PartyLegalEntity?: PartyLegalEntity;
    Contact?: Contact;
}

export interface AccountingParty {
    Party: Party;
}

export interface TaxCategory {
    ID?: string;
    Percent?: number;
    TaxScheme?: { ID: string };
}

export interface TaxSubtotal {
    TaxableAmount: Amount;
    TaxAmount: Amount;
    TaxCategory?: TaxCategory;
}

export interface TaxTotal {
    TaxAmount: Amount;
    TaxSubtotal?: TaxSubtotal[];
}

export interface AllowanceCharge {
    ChargeIndicator: boolean;
    AllowanceChargeReason?: string;
    Amount: Amount;
    TaxCategory?: TaxCategory;
}

export interface MonetaryTotal {
    LineExtensionAmount?: Amount;
    TaxExclusiveAmount?: Amount;
    TaxInclusiveAmount?: Amount;
    ChargeTotalAmount?: Amount;
    PayableAmount: Amount;
}

export interface OrderLineReference {
    LineID: string;
}

export interface StandardItemIdentification {
    ID: Identifier;
}

export interface CommodityClassification {
    ItemClassificationCode: { __listID?: string; "value": string };
}

export interface ClassifiedTaxCategory {
    ID: string;
    Percent: number;
    TaxScheme: { ID: string };
}

export interface Item {
    Description?: string;
    Name: string;
    StandardItemIdentification?: StandardItemIdentification;
    OriginCountry?: { IdentificationCode: string };
    CommodityClassification?: CommodityClassification;
    ClassifiedTaxCategory?: ClassifiedTaxCategory;
}

export interface Price {
    PriceAmount: Amount;
    BaseQuantity?: Quantity;
}

export interface DeliveryLocation {
    ID?: Identifier;
    Address?: Address;
}

export interface DeliveryParty {
    PartyName?: { Name: string };
}

export interface Delivery {
    ActualDeliveryDate?: string;
    DeliveryLocation?: DeliveryLocation;
    DeliveryParty?: DeliveryParty;
}

export interface PayeeFinancialAccount {
    ID?: string;
    Name?: string;
    FinancialInstitutionBranch?: { ID?: string };
}

export interface PaymentMeansCode {
    __name?: string;
    value: number;
}

export interface PaymentMeans {
    PaymentMeansCode?: PaymentMeansCode;
    PaymentID?: string;
    PayeeFinancialAccount?: PayeeFinancialAccount;
}

export interface CreditNotePaymentMeans {
    PaymentMeansCode?: PaymentMeansCode;
    PaymentDueDate?: string;
    PaymentID?: string;
    PayeeFinancialAccount?: PayeeFinancialAccount;
}

export interface PaymentTerms {
    Note?: string;
}

export interface InvoiceDocumentReference {
    ID: string;
}

export interface BillingReference {
    InvoiceDocumentReference: InvoiceDocumentReference;
}
