import {describe, expect, test} from "vitest";
import {InvoiceCustomerModal} from "../../src/invoice/edit/components/modals/invoice-customer-modal";
import {InvoiceContext} from "../../src/invoice/invoice-context";
import {DocumentDirection, DocumentDto, DocumentType} from "../../src/services/app/invoice-service";
import {PartnerDto} from "../../src/services/app/partner-service";
import {KycCompanyResponse} from "../../src/services/kyc/registration-service";
import {Party} from "../../src/services/peppol/ubl";

describe("timesheet customer lookup races", () => {
    test("ignores a draft Partner lookup after the invoice customer changes", async () => {
        const partnersRequest = deferred<PartnerDto[]>();
        const context = Object.create(InvoiceContext.prototype) as InvoiceContext;
        const draft = outgoingInvoiceDraft();
        Object.assign(context, {
            partnerService: {searchPartners: () => partnersRequest.promise},
            readOnly: false,
            selectedDocument: draft,
            timesheetRequired: false,
            timesheetRequirementLookupGeneration: 0,
        });

        const restoreTimesheetRequirement = Reflect.get(context, "restoreTimesheetRequirement") as
            (item: DocumentDto) => void;
        restoreTimesheetRequirement.call(context, draft);

        context.setTimesheetRequirement(true);
        partnersRequest.resolve([partner(false)]);
        await partnersRequest.promise;
        await Promise.resolve();

        expect(context.timesheetRequired).toBe(true);
    });

    test("allows draft restoration to finish after the unchanged customer is confirmed", async () => {
        const partnersRequest = deferred<PartnerDto[]>();
        const context = Object.create(InvoiceContext.prototype) as InvoiceContext;
        const draft = outgoingInvoiceDraft();
        Object.assign(context, {
            partnerService: {searchPartners: () => partnersRequest.promise},
            readOnly: false,
            selectedDocument: draft,
            selectedInvoice: {
                AccountingCustomerParty: {Party: completeCustomer()},
                BillingReference: [],
            },
            timesheetRequired: false,
            timesheetRequirementLookupGeneration: 0,
        });
        const modal = Object.create(InvoiceCustomerModal.prototype) as InvoiceCustomerModal;
        Object.assign(modal, {
            customerInfoLookupGeneration: 0,
            customerSearch: {resetSearch: () => undefined, focusInput: () => undefined},
            invoiceContext: context,
            pendingTimesheetRequirementOverridden: false,
        });

        const restoreTimesheetRequirement = Reflect.get(context, "restoreTimesheetRequirement") as
            (item: DocumentDto) => void;
        restoreTimesheetRequirement.call(context, draft);
        modal.showModal(() => undefined);
        modal.saveCustomer();
        partnersRequest.resolve([partner(true)]);
        await partnersRequest.promise;
        await Promise.resolve();

        expect(context.timesheetRequired).toBe(true);
    });

    test("applies a company selected from name search", () => {
        const modal = Object.create(InvoiceCustomerModal.prototype) as InvoiceCustomerModal;
        const customer = emptyCustomer();
        customer.PartyName.Name = "Typed query";
        customer.PartyLegalEntity.RegistrationName = "Typed query";
        Object.assign(modal, {
            customer,
            customerInfoLookupGeneration: 0,
            pendingTimesheetRequired: true,
            pendingTimesheetRequirementOverridden: false,
            peppolId: undefined,
        });

        modal.selectDirectoryCompany(directoryCompany());

        expect(modal.customer.PartyName.Name).toBe("Directory Company");
        expect(modal.customer.PartyLegalEntity.RegistrationName).toBe("Directory Company");
        expect(modal.peppolId).toBe("0208:0123456789");
        expect(modal.pendingTimesheetRequired).toBe(false);
    });

    test.each([
        ["Peppol", (modal: InvoiceCustomerModal) => modal.peppolIdChanged()],
        ["VAT", (modal: InvoiceCustomerModal) => modal.vatNumberChanged()],
    ])("ignores a stale %s directory response after saved Partner selection", async (_lookup, startLookup) => {
        const companyRequest = deferred<KycCompanyResponse[]>();
        const modal = Object.create(InvoiceCustomerModal.prototype) as InvoiceCustomerModal;
        Object.assign(modal, {
            companySearchService: {searchCompany: () => companyRequest.promise},
            customer: emptyCustomer(),
            customerInfoLookupGeneration: 0,
            peppolId: "0208:0123456789",
            pendingTimesheetRequired: false,
        });

        startLookup(modal);
        modal.selectCustomer(partner(true));
        companyRequest.resolve([directoryCompany()]);
        await companyRequest.promise;
        await Promise.resolve();

        expect(modal.pendingTimesheetRequired).toBe(true);
        expect(modal.customer.PartyName.Name).toBe("Saved Partner");
    });
});

function outgoingInvoiceDraft(): DocumentDto {
    return {
        id: "draft-id",
        direction: DocumentDirection.OUTGOING,
        ownerPeppolId: "0208:owner",
        partnerPeppolId: "0208:old-customer",
        type: DocumentType.INVOICE,
        ubl: "",
    };
}

function partner(timesheet: boolean): PartnerDto {
    return {
        name: "Saved Partner",
        peppolId: "0208:0987654321",
        vatNumber: "BE0987654321",
        customer: true,
        supplier: false,
        timesheet,
        registeredOffice: {
            street: "Partner Street 1",
            city: "Brussels",
            postalCode: "1000",
            countryCode: "BE",
        },
    };
}

function emptyCustomer(): Party {
    return {
        EndpointID: {__schemeID: "0208", value: "0123456789"},
        PartyName: {Name: ""},
        PostalAddress: {StreetName: "", CityName: "", PostalZone: "", Country: {IdentificationCode: "BE"}},
        PartyTaxScheme: {CompanyID: {value: "BE0123456789"}, TaxScheme: {ID: "VAT"}},
        PartyLegalEntity: {RegistrationName: "", CompanyID: {value: ""}},
    };
}

function completeCustomer(): Party {
    return {
        EndpointID: {__schemeID: "0208", value: "0123456789"},
        PartyName: {Name: "Existing Customer"},
        PostalAddress: {
            StreetName: "Existing Street 1",
            CityName: "Brussels",
            PostalZone: "1000",
            Country: {IdentificationCode: "BE"},
        },
        PartyTaxScheme: {CompanyID: {value: "BE0123456789"}, TaxScheme: {ID: "VAT"}},
        PartyLegalEntity: {RegistrationName: "Existing Customer", CompanyID: {value: "BE0123456789"}},
    };
}

function directoryCompany(): KycCompanyResponse {
    return {
        id: 1,
        peppolId: "0208:0123456789",
        vatNumber: "BE0123456789",
        name: "Directory Company",
        street: "Directory Street 1",
        city: "Brussels",
        postalCode: "1000",
    };
}

function deferred<T>() {
    let resolve: (value: T) => void;
    const promise = new Promise<T>((resolvePromise) => {
        resolve = resolvePromise;
    });
    return {promise, resolve: resolve!};
}
