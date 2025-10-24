import {AccountingParty, CreditNote, CreditNoteLine, Invoice, InvoiceLine, UBLBaseLine,} from "../services/peppol/ubl";
import moment from "moment/moment";
import {singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {CompanyService} from "../services/app/company-service";
import {PartnerDto} from "../services/app/partner-service";

@singleton()
export class InvoiceComposer {
    private companyService = resolve(CompanyService);
    private customer: PartnerDto = {
        customer: true,
        supplier: false,
        companyNumber: "0705969661",
        name: "Ponder Source",
        registeredOffice: {
            street: "Da street",
            houseNumber: "3",
            city: "Amstel",
            postalCode: "33209"
        }
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
                    StreetName: this.customer.registeredOffice.street,
                    AdditionalStreetName: this.customer.registeredOffice.houseNumber,
                    CityName: this.customer.registeredOffice.city,
                    PostalZone: this.customer.registeredOffice.postalCode,
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
                    StreetName: `${this.companyService.myCompany.registeredOffice.street} ${this.companyService.myCompany.registeredOffice.houseNumber}`,
                    CityName: this.companyService.myCompany.registeredOffice.city,
                    PostalZone: this.companyService.myCompany.registeredOffice.postalCode,
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

        /*
          <cac:BillingReference>
            <cac:InvoiceDocumentReference>
              <cbc:ID>INV-2025-007</cbc:ID>
            </cac:InvoiceDocumentReference>
          </cac:BillingReference>
         */
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
