import {
    CreditNote,
    CreditNoteLine,
    Invoice,
    InvoiceLine,
    AccountingParty, UBLBaseLine,
} from "../peppol/ubl";
import moment from "moment/moment";
import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {CompanyResponse, CompanyService} from "../services/company-service";
import { omit } from 'lodash';

@singleton()
export class InvoiceComposer {
    private companyService = resolve(CompanyService);
    private customer: CompanyResponse = {
        id: 1,
        companyNumber: "0705969661",
        name: "Ponder Source",
        street: "Da street",
        city: "Amstel",
        postalCode: "33209"
    };

    createInvoice(): Invoice {
        return {
            CustomizationID: "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0",
            ProfileID: "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0",
            ID: "",
            IssueDate: moment().format('YYYY-MM-DD'),
            DueDate: moment().add(30, 'day').format('YYYY-MM-DD'),
            InvoiceTypeCode: 380,
            DocumentCurrencyCode: "EUR",
            BuyerReference: undefined,
            AccountingSupplierParty: this.getAccountingSupplierParty(),
            AccountingCustomerParty: this.getAccountingCustomerParty(),
            PaymentMeans : {
                PaymentMeansCode: {
                    __name: "Credit transfer",
                    value: 30
                },
                PayeeFinancialAccount: {
                    ID: undefined,
                    Name: undefined,
                    FinancialInstitutionBranch: {
                        ID: undefined
                    }
                },
            },
            PaymentTerms: {
                Note: "Payment within 10 days, 2% discount"
            },
            TaxTotal: undefined,
            LegalMonetaryTotal: {
                PayableAmount: {
                    __currencyID: 'EUR',
                    value: 0
                }
            },
            InvoiceLine: []
        } as Invoice;
    }

    createCreditNote(): CreditNote {
        return {
            CustomizationID: "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0",
            ProfileID: "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0",
            ID: "",
            IssueDate: moment().format('YYYY-MM-DD'),
            DueDate: moment().add(30, 'day').format('YYYY-MM-DD'),
            CreditNoteTypeCode: 381,
            DocumentCurrencyCode: "EUR",
            AccountingSupplierParty: this.getAccountingSupplierParty(),
            AccountingCustomerParty: this.getAccountingCustomerParty(),
            LegalMonetaryTotal: {
                PayableAmount: {
                    __currencyID: 'EUR',
                    value: 0
                }
            },
            CreditNoteLine: []

        } as CreditNote;
    }

    getAccountingCustomerParty(): AccountingParty {
        return {
            Party :  {
                EndpointID: {
                    __schemeID: "0208",
                    value: this.customer.companyNumber
                },
                PartyIdentification: [{ ID: {
                        __schemeID: "0208",
                        value: this.customer.companyNumber
                    }}],
                PartyName: {
                    Name: ""
                },
                PostalAddress: {
                    StreetName: this.customer.street,
                    CityName: this.customer.city,
                    PostalZone: this.customer.postalCode,
                    Country: {
                        IdentificationCode: "BE"
                    }
                },
                PartyTaxScheme: {
                    CompanyID: {
                        value: `BE${this.customer.companyNumber}`
                    },
                    TaxScheme: {
                        ID: "VAT"
                    }
                },
                PartyLegalEntity: {
                    RegistrationName: this.customer.name
                }
            }
        };
    }

    getCompanyNumber() {
        return `${this.companyService.myCompany.companyNumber}`; // TODO BE prefix?
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
                    StreetName: this.companyService.myCompany.street,
                    CityName: this.companyService.myCompany.city,
                    PostalZone: this.companyService.myCompany.postalCode,
                    Country: {
                        IdentificationCode: "BE"
                    }
                },
                PartyTaxScheme: {
                    CompanyID: {
                        value: `BE${this.getCompanyNumber()}`
                    },
                    TaxScheme: {
                        ID: "VAT"
                    }
                },
                PartyLegalEntity: {
                    RegistrationName: this.companyService.myCompany.name
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
                Name: "VAT",
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
            ...omit(invoice,  ["InvoiceTypeCode" , "InvoiceLine"]),
            CreditNoteTypeCode: 381,
            CreditNoteLine: invoice.InvoiceLine.map(line => ({
                ...omit(line, ["InvoicedQuantity"]),
                CreditedQuantity: line.InvoicedQuantity,
            })),
        } as CreditNote;
    }

    creditNoteToInvoice(creditNote: CreditNote): Invoice {
        return {
            ...omit(creditNote, ["CreditNoteTypeCode", "CreditNoteLine"]),
            InvoiceTypeCode: 380,
            InvoiceLine: creditNote.CreditNoteLine.map(line => ({
                ...omit(line, ["CreditedQuantity"]),
                InvoicedQuantity: line.CreditedQuantity,
                CreditedQuantity: undefined
            })),
        } as Invoice;
    }
}