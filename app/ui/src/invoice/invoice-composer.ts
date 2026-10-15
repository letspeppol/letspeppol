import {
    AccountingParty, AdditionalDocumentReference,
    CreditNote,
    CreditNoteLine,
    Invoice,
    InvoiceLine,
    PaymentMeans,
    PaymentMeansCode,
    PaymentTerms,
    UBLBaseLine,
} from "../services/peppol/ubl";
import moment from "moment/moment";
import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {CompanyService} from "../services/app/company-service";
import {DocumentType} from "../services/app/invoice-service";
import {I18N} from "@aurelia/i18n";
import {createNotSubjectToVatCategory, createVatExemptCategory, isVatExemptRuleset} from "../services/app/vat-rules";
import {
    DEFAULT_PAYMENT_TERM,
    MAX_PAYMENT_TERM_DAYS,
    PAYMENT_TERM_BASES,
    PaymentTerm,
    calculateDueDate,
    formatPaymentTermCode,
    parsePaymentTermCode,
    preferredTerm,
} from "../services/app/payment-terms";

export const GENERATED_INVOICE = 'generated_invoice';
export const GENERATED_INVOICE_PLACEHOLDER = 'ZW1wdHk=';
export const PDF_MIME_CODE = 'application/pdf';

export function isPdfAttachment(additionalDocumentReference: AdditionalDocumentReference): boolean {
    return additionalDocumentReference?.Attachment?.EmbeddedDocumentBinaryObject?.__mimeCode === PDF_MIME_CODE;
}

export function isGeneratedInvoicePlaceholder(additionalDocumentReference: AdditionalDocumentReference): boolean {
    if (additionalDocumentReference?.ID !== GENERATED_INVOICE) {
        return false;
    }
    const value = additionalDocumentReference.Attachment?.EmbeddedDocumentBinaryObject?.value;
    return !value || value === GENERATED_INVOICE_PLACEHOLDER;
}

const PAYMENT_TERM_LOCALES = ['en', 'fr', 'nl', 'de'];

const CREDIT_NOTE_PAYMENT_TERM = 'NET_15';

const DAYS_PLACEHOLDER = '\u0001';

const PLURAL_SAMPLES = new Map<string, number[]>();

function pluralSamples(locale: string): number[] {
    let samples = PLURAL_SAMPLES.get(locale);
    if (!samples) {
        const rules = new Intl.PluralRules(locale);
        const byForm = new Map<string, number>();
        for (let days = 1; days <= MAX_PAYMENT_TERM_DAYS; days++) {
            const form = rules.select(days);
            if (!byForm.has(form)) {
                byForm.set(form, days);
            }
        }
        samples = [...byForm.values()];
        PLURAL_SAMPLES.set(locale, samples);
    }
    return samples;
}

function escapeRegExp(value: string): string {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function paymentTermKey(term: PaymentTerm): string {
    switch (term.basis) {
        case 'END_OF_MONTH':
            return term.days === 0 ? 'paymentTerms.end-of-month' : 'paymentTerms.end-of-month-days';
        case 'END_OF_NEXT_MONTH':
            return term.days === 0 ? 'paymentTerms.end-of-next-month' : 'paymentTerms.end-of-next-month-days';
        default:
            return term.days === 0 ? 'paymentTerms.on-receipt' : 'paymentTerms.net';
    }
}

@singleton()
export class InvoiceComposer {
    private i18n = resolve(I18N);
    private companyService = resolve(CompanyService);

    public paymentMeansCodes: PaymentMeansCode[] = [
        { value: 10, __name: "In Cash"},
        { value: 30, __name: "Credit Transfer"}
    ];

    public getPaymentMeansCode(code: number): PaymentMeansCode {
        return this.paymentMeansCodes.find(item => item.value === code);
    }

    public translatePaymentTerm(paymentTerm: string) {
        const term = parsePaymentTermCode(paymentTerm);
        return term ? this.translateTerm(term) : paymentTerm;
    }

    private translateTerm(term: PaymentTerm, locale?: string) {
        const options: Record<string, unknown> = {days: term.days};
        if (term.days > 0) {
            options.count = term.days;
        }
        if (locale) {
            options.lng = locale;
        }
        return this.i18n.tr(paymentTermKey(term), options);
    }

    public derivePaymentTermCode(issueDate: string, dueDate?: string, note?: string): string | undefined {
        const fromNote = this.parsePaymentTermNote(note);
        if (fromNote && (!dueDate || calculateDueDate(fromNote, issueDate) === dueDate)) {
            return formatPaymentTermCode(fromNote);
        }
        const derived = dueDate ? preferredTerm(issueDate, dueDate) : undefined;
        return derived ? formatPaymentTermCode(derived) : undefined;
    }

    private parsePaymentTermNote(note?: string): PaymentTerm | undefined {
        const trimmed = note?.trim();
        if (!trimmed) {
            return undefined;
        }
        for (const locale of PAYMENT_TERM_LOCALES) {
            for (const basis of PAYMENT_TERM_BASES) {
                if (this.translateTerm({days: 0, basis}, locale) === trimmed) {
                    return {days: 0, basis};
                }
                for (const sample of pluralSamples(locale)) {
                    const template = this.i18n.tr(paymentTermKey({days: sample, basis}), {
                        count: sample,
                        days: DAYS_PLACEHOLDER,
                        lng: locale,
                    });
                    const pattern = new RegExp(`^${escapeRegExp(template).replace(DAYS_PLACEHOLDER, '(\\d{1,3})')}$`);
                    const days = Number(pattern.exec(trimmed)?.[1]);
                    if (Number.isInteger(days) && days > 0 && days <= MAX_PAYMENT_TERM_DAYS) {
                        return {days, basis};
                    }
                }
            }
        }
        return undefined;
    }

    createInvoice(): Invoice {
        const invoice = {
            CustomizationID: "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0",
            ProfileID: "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0",
            ID: "",
            IssueDate: moment().format('YYYY-MM-DD'),
            DueDate: this.getDueDateForCompany(),
            InvoiceTypeCode: 380,
            Note: undefined,
            DocumentCurrencyCode: "EUR",
            BuyerReference: undefined,
            OrderReference: { ID: "NA" },
            AdditionalDocumentReference: this.getAdditionalDocumentReference(),
            AccountingSupplierParty: this.getAccountingSupplierParty(),
            AccountingCustomerParty: this.getAccountingCustomerParty(),
            PaymentMeans : undefined,
            PaymentTerms: undefined,
            TaxTotal: undefined,
            LegalMonetaryTotal: {
                PayableAmount: {
                    __currencyID: 'EUR',
                    value: 0
                }
            },
            InvoiceLine: []
        } as Invoice;

        invoice.PaymentMeans = this.getPaymentMeansForMyCompany(30);
        invoice.PaymentTerms = this.getPaymentTermsForMyCompany(DocumentType.INVOICE);

        return invoice;
    }

    createCreditNote(): CreditNote {
        const creditNote = {
            CustomizationID: "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0",
            ProfileID: "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0",
            ID: "",
            IssueDate: moment().format('YYYY-MM-DD'),
            CreditNoteTypeCode: 381,
            Note: this.i18n.tr('invoice.modal.credit-note-vat-note'),
            DocumentCurrencyCode: "EUR",
            BuyerReference: undefined,
            OrderReference: { ID: "NA" },
            AdditionalDocumentReference: this.getAdditionalDocumentReference(),
            AccountingSupplierParty: this.getAccountingSupplierParty(),
            AccountingCustomerParty: this.getAccountingCustomerParty(),
            PaymentMeans : undefined,
            PaymentTerms: undefined,
            TaxTotal: undefined,
            LegalMonetaryTotal: {
                PayableAmount: {
                    __currencyID: 'EUR',
                    value: 0
                }
            },
            CreditNoteLine: []
        } as CreditNote;

        creditNote.PaymentTerms = this.getPaymentTermsForMyCompany(DocumentType.CREDIT_NOTE);

        return creditNote;
    }

    getDueDateForCompany() {
        const issueDate = moment().format('YYYY-MM-DD');
        const paymentTerms = this.companyService.myCompany.paymentTerms;
        return paymentTerms
            ? this.getDueDate(paymentTerms, issueDate)
            : calculateDueDate(DEFAULT_PAYMENT_TERM, issueDate);
    }

    getDueDate(paymentTerm: string, issueDate: string) {
        const term = parsePaymentTermCode(paymentTerm);
        return (term && calculateDueDate(term, issueDate)) ?? issueDate;
    }

    getPaymentMeansForMyCompany(paymentMeansCode: number) : PaymentMeans {
        const myCompany = this.companyService.myCompany;
        const bic = myCompany.bic?.trim().toUpperCase();
        return {
            PaymentMeansCode: this.paymentMeansCodes.find(item => item.value === paymentMeansCode),
            PaymentID: undefined,
            PayeeFinancialAccount: {
                ID: myCompany.iban,
                Name: myCompany.paymentAccountName ?? myCompany.name,
                ...(bic ? {FinancialInstitutionBranch: {ID: bic}} : {}),
            }
        } as PaymentMeans;
    }

    getPaymentTermsForMyCompany(documentType: DocumentType): PaymentTerms {
        const myCompany = this.companyService.myCompany;
        if (myCompany.paymentTerms) {
            return {
                Note: this.translatePaymentTerm(myCompany.paymentTerms)
            }
        } else if (documentType === DocumentType.CREDIT_NOTE) {
            return {
                Note: this.translatePaymentTerm(CREDIT_NOTE_PAYMENT_TERM)
            }
        }
        return undefined;
    }

    getAccountingCustomerParty(): AccountingParty {
        return {
            Party :  {
                EndpointID: {
                    __schemeID: "0208",
                    value: undefined
                },
                PartyIdentification: [{ ID: {
                        __schemeID: "0208",
                        value: undefined
                    }}],
                PartyName: {
                    Name: ""
                },
                PostalAddress: {
                    StreetName: undefined,
                    AdditionalStreetName: undefined,
                    CityName: undefined,
                    PostalZone: undefined,
                    Country: {
                        IdentificationCode: "BE"
                    }
                },
                PartyTaxScheme: {
                    CompanyID: undefined,
                    TaxScheme: {
                        ID: "VAT"
                    }
                },
                PartyLegalEntity: {
                    RegistrationName: undefined,
                    CompanyID: {
                        __schemeID: null,
                        value: undefined
                    }
                }
            }
        } as AccountingParty;
    }

    getCompanyNumber() {
        if (!this.companyService.myCompany.peppolId)
            return undefined;
        const s = this.companyService.myCompany.peppolId.trim();
        const i = s.indexOf(":");
        if (i < 0)
            return undefined;
        const value = s.slice(i + 1).trim();
        return `${value}`;
    }

    getVatNumber() {
        return this.companyService.myCompany.vatNumber;
    }

    hasNoVatNumber(): boolean {
        return !this.companyService.myCompany?.vatNumber?.trim();
    }

    getAccountingSupplierParty(): AccountingParty {
        return {
            Party :  {
                EndpointID: {
                    __schemeID: "0208",
                    value: this.getCompanyNumber()
                },
                PartyIdentification: [{ID: {
                        __schemeID: "0208",
                        value: this.getCompanyNumber()
                    }}],
                PartyName: {
                    Name: this.companyService.myCompany.displayName
                },
                PostalAddress: {
                    StreetName: this.companyService.myCompany.registeredOffice.street,
                    AdditionalStreetName: this.companyService.myCompany.registeredOffice.houseNumber,
                    CityName: this.companyService.myCompany.registeredOffice.city,
                    PostalZone: this.companyService.myCompany.registeredOffice.postalCode,
                    Country: {
                        IdentificationCode: "BE"
                    }
                },
                PartyTaxScheme: {
                    CompanyID: {value: this.getVatNumber() },
                    TaxScheme: {
                        ID: "VAT"
                    }
                },
                PartyLegalEntity: {
                    RegistrationName: this.companyService.myCompany.name,
                    CompanyID: {value: this.companyService.myCompany.identifier}
                }
            }
        } as AccountingParty;
    }

    getCreditNoteLine(position: string): CreditNoteLine {
        return {
            ID: position,
            CreditedQuantity: {
                __unitCode: "C62",
                value: 0
            },
            ... this.getLine(),
        }
    }

    getInvoiceLine(position: string): InvoiceLine {
        return {
            ID: position,
            InvoicedQuantity: {
                __unitCode: "C62",
                value: 0
            },
            ... this.getLine(),
        }
    }

    private getLine(): UBLBaseLine {
        const vatRuleset = this.companyService.myCompany.vatRuleset;
        const isExempt = isVatExemptRuleset(vatRuleset);
        const hasNoVatNumber = this.hasNoVatNumber();
        return {
            LineExtensionAmount: {
                __currencyID: "EUR",
                value: 0
            },
            Item: {
                Description: undefined,
                Name: undefined,
                ClassifiedTaxCategory: hasNoVatNumber
                    ? createNotSubjectToVatCategory()
                    : isExempt
                    ? createVatExemptCategory()
                    : {
                        ID: "S",
                        Percent: 21,
                        TaxScheme: {
                            ID: 'VAT'
                        }
                    }
            },
            Price: {
                PriceAmount: {
                    __currencyID: "EUR",
                    value: 0
                }
            },
        } as UBLBaseLine;
    }

    /*
    These follow UN/CEFACT 5305 codes. Common ones in Belgium:
    S = Standard rate (e.g. 21%)
    AA = Lower rate (e.g. 6%)
    Z = Zero rated
    E = Exempt from tax
    AE = Reverse charge

    👉 The tax percentages must match Belgian VAT rules:
    21% (standard)
    12% (specific goods/services, e.g. social housing)
    6% (essentials, e.g. food, medicines)
    0% or exempt (intra-community, exports, special cases)
     */

    invoiceToCreditNote(invoice: Invoice): CreditNote {
        const vatNote = this.i18n.tr('invoice.modal.credit-note-vat-note');
        const billingReference = invoice.ID
            ? [{
                InvoiceDocumentReference: {
                    ID: invoice.ID,
                    IssueDate: invoice.IssueDate,
                }
            }]
            : undefined;
        return {
            CustomizationID: invoice.CustomizationID,
            ProfileID: invoice.ProfileID,
            ID: invoice.ID,
            IssueDate: invoice.IssueDate,
            CreditNoteTypeCode: 381,
            Note: invoice.Note && invoice.Note.trim() ? invoice.Note : vatNote,
            DocumentCurrencyCode: "EUR",
            BuyerReference: invoice.BuyerReference,
            OrderReference: invoice.OrderReference,
            BillingReference: billingReference,
            AdditionalDocumentReference: invoice.AdditionalDocumentReference,
            AccountingSupplierParty: invoice.AccountingSupplierParty,
            AccountingCustomerParty: invoice.AccountingCustomerParty,
            PaymentMeans: invoice.PaymentMeans,
            PaymentTerms: invoice.PaymentTerms,
            TaxTotal: invoice.TaxTotal,
            LegalMonetaryTotal: invoice.LegalMonetaryTotal,
            CreditNoteLine: invoice.InvoiceLine.map(line => ({
                ID: line.ID,
                CreditedQuantity: line.InvoicedQuantity,
                LineExtensionAmount: line.LineExtensionAmount,
                Item: line.Item,
                Price: line.Price,
            })),
        } as CreditNote;
    }

    creditNoteToInvoice(creditNote: CreditNote): Invoice {
        return {
            CustomizationID: creditNote.CustomizationID,
            ProfileID: creditNote.ProfileID,
            ID: creditNote.ID,
            IssueDate: creditNote.IssueDate,
            DueDate: undefined,
            InvoiceTypeCode: 380,
            Note: creditNote.Note,
            DocumentCurrencyCode: "EUR",
            BuyerReference: creditNote.BuyerReference,
            OrderReference: creditNote.OrderReference,
            AdditionalDocumentReference: creditNote.AdditionalDocumentReference,
            AccountingSupplierParty: creditNote.AccountingSupplierParty,
            AccountingCustomerParty: creditNote.AccountingCustomerParty,
            PaymentMeans: creditNote.PaymentMeans,
            PaymentTerms: creditNote.PaymentTerms,
            TaxTotal: creditNote.TaxTotal,
            LegalMonetaryTotal: creditNote.LegalMonetaryTotal,
            InvoiceLine: creditNote.CreditNoteLine.map(line => ({
                ID: line.ID,
                InvoicedQuantity: line.CreditedQuantity,
                LineExtensionAmount: line.LineExtensionAmount,
                Item: line.Item,
                Price: line.Price,
            })),
        } as Invoice;
    }

    public getGeneratedInvoiceDocumentReference(): AdditionalDocumentReference {
        return {
            ID: GENERATED_INVOICE,
            DocumentDescription: 'Generated Invoice PDF',
            Attachment: {
                EmbeddedDocumentBinaryObject: {
                    __mimeCode: PDF_MIME_CODE,
                    __filename: `${GENERATED_INVOICE}.pdf`,
                    value: GENERATED_INVOICE_PLACEHOLDER
                }
            }
        } as AdditionalDocumentReference;
    }

    public getAdditionalDocumentReference() {
        // New documents include the generated-PDF marker by default. The attachment
        // modal can still remove it per invoice before saving or sending.
        return [this.getGeneratedInvoiceDocumentReference()];
    }
}

