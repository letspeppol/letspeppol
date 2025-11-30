import {
    AccountingParty,
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
import {I18N} from "@aurelia/i18n";
import {DocumentType} from "./invoice-context";

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
        return this.i18n.tr(`paymentTerms.${paymentTerm}`)
    }

    createInvoice(): Invoice {
        const invoice = {
            CustomizationID: "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0",
            ProfileID: "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0",
            ID: "",
            IssueDate: moment().format('YYYY-MM-DD'),
            DueDate: moment().add(30, 'day').format('YYYY-MM-DD'),
            InvoiceTypeCode: 380,
            Note: undefined,
            DocumentCurrencyCode: "EUR",
            BuyerReference: undefined,
            AdditionalDocumentReference: undefined,
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
        invoice.PaymentTerms = this.getPaymentTermsForMyCompany(DocumentType.Invoice);

        return invoice;
    }

    getDueDate(paymentTerm: string, issueDate: string) {
        const date = moment(issueDate);
        switch (paymentTerm) {
            case '15_DAYS':
                date.add(15, 'day');
                break;
            case '30_DAYS':
                date.add(30, 'day');
                break;
            case '60_DAYS':
                date.add(60, 'day');
                break;
            case 'END_OF_NEXT_MONTH':
                date.add(1, 'month').endOf('month');
                break;
        }
        return date.format('YYYY-MM-DD');
    }

    getPaymentMeansForMyCompany(paymentMeansCode: number) : PaymentMeans {
        const myCompany = this.companyService.myCompany;
        return {
            PaymentMeansCode: this.paymentMeansCodes.find(item => item.value === paymentMeansCode),
            PaymentID: undefined,
            PayeeFinancialAccount: {
                ID: myCompany.iban,
                Name: myCompany.paymentAccountName ?? myCompany.name,
            }
        } as PaymentMeans;
    }

    getPaymentTermsForMyCompany(documentType: DocumentType): PaymentTerms {
        const myCompany = this.companyService.myCompany;
        if (myCompany.paymentTerms) {
            return {
                Note: this.translatePaymentTerm(myCompany.paymentTerms)
            }
        } else if (documentType === DocumentType.CreditNote) {
            return {
                Note: this.translatePaymentTerm('15_DAYS')
            }
        }
        return undefined;
    }

    createCreditNote(): CreditNote {
        const creditNote = {
            CustomizationID: "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0",
            ProfileID: "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0",
            ID: "",
            IssueDate: moment().format('YYYY-MM-DD'),
            CreditNoteTypeCode: 381,
            Note: undefined,
            DocumentCurrencyCode: "EUR",
            BuyerReference: undefined,
            AdditionalDocumentReference: undefined,
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

        creditNote.PaymentTerms = this.getPaymentTermsForMyCompany(DocumentType.CreditNote);

        return creditNote;
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
                    CompanyID: {
                        value: undefined
                    },
                    TaxScheme: {
                        ID: "VAT"
                    }
                },
                PartyLegalEntity: {
                    RegistrationName: undefined
                }
            }
        };
    }

    getCompanyNumber() {
        if (!this.companyService.myCompany.peppolId)
            return undefined;
        const s = this.companyService.myCompany.peppolId.trim();
        const i = s.indexOf(":");
        if (i < 0)
            return undefined;
        let value = s.slice(i + 1).trim();
        return `${value}`;
    }

    getVatNumber() {
        return this.companyService.myCompany.vatNumber;
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
                    Name: this.companyService.myCompany.name
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
                    CompanyID: {
                        value: this.getVatNumber()
                    },
                    TaxScheme: {
                        ID: "VAT"
                    }
                },
                PartyLegalEntity: {
                    RegistrationName: this.companyService.myCompany.name,
                }
            }
        };
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
        return {
            LineExtensionAmount: {
                __currencyID: "EUR",
                value: 0
            },
            Item: {
                Description: undefined,
                Name: undefined,
                ClassifiedTaxCategory: {
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
        return {
            CustomizationID: invoice.CustomizationID,
            ProfileID: invoice.ProfileID,
            ID: invoice.ID,
            IssueDate: invoice.IssueDate,
            CreditNoteTypeCode: 381,
            DocumentCurrencyCode: "EUR",
            BuyerReference: invoice.BuyerReference, // or OrderReference
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
            DocumentCurrencyCode: "EUR",
            BuyerReference: creditNote.BuyerReference,
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
}
