package org.letspeppol.app.util;


import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_21.*;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.*;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

public abstract class AbstractUBLBuilder {

    protected CustomerPartyType getCustomerParty() {
        // Example customer data (keep tests/self-contained; mirrors how getSupplierParty() hardcodes values)
        String peppolScheme = "0208";
        String peppolValue = "987654321";
        String customerName = "Customer BV";

        EndpointIDType endpointID = new EndpointIDType();
        endpointID.setSchemeID(peppolScheme);
        endpointID.setValue(peppolValue);

        // Optional identification
        PartyIdentificationType partyIdentification = new PartyIdentificationType();
        {
            IDType idType = new IDType();
            idType.setSchemeID(peppolScheme);
            idType.setValue(peppolValue);
            partyIdentification.setID(idType);
        }

        NameType nameType = new NameType();
        nameType.setValue(customerName);

        PartyNameType name = new PartyNameType();
        name.setName(nameType);

        CountryType country = new CountryType();
        country.setIdentificationCode(new IdentificationCodeType("BE"));

        AddressType address = new AddressType();
        address.setStreetName("Customerstraat 1");
        address.setCityName("Brussels");
        address.setPostalZone("1000");
        address.setCountry(country);

        // Optional VAT
        PartyTaxSchemeType partyTaxScheme = null;
        String vat = "BE" + peppolValue;
        if (StringUtils.hasText(vat)) {
            CompanyIDType taxCompanyID = new CompanyIDType();
            taxCompanyID.setValue(vat);

            TaxSchemeType taxScheme = new TaxSchemeType();
            taxScheme.setID(new IDType("VAT"));

            partyTaxScheme = new PartyTaxSchemeType();
            partyTaxScheme.setCompanyID(taxCompanyID);
            partyTaxScheme.setTaxScheme(taxScheme);
        }

        PartyLegalEntityType partyLegalEntity = new PartyLegalEntityType();
        partyLegalEntity.setRegistrationName(customerName);

        PartyType party = new PartyType();
        party.setEndpointID(endpointID);
        party.setPartyIdentification(List.of(partyIdentification));
        party.setPartyName(List.of(name));
        party.setPostalAddress(address);
        if (partyTaxScheme != null) {
            party.addPartyTaxScheme(partyTaxScheme);
        }
        party.setPartyLegalEntity(List.of(partyLegalEntity));

        CustomerPartyType customer = new CustomerPartyType();
        customer.setParty(party);
        return customer;
    }

    protected SupplierPartyType getSupplierParty() {
        EndpointIDType endpointID = new EndpointIDType();
        IDType idType = new IDType();
        PartyIdentificationType partyIdentification = new PartyIdentificationType();
        NameType nameType = new NameType();
        PartyNameType name = new PartyNameType();
        CountryType country = new CountryType();
        AddressType address = new AddressType();
        CompanyIDType companyID = new CompanyIDType();
        CompanyIDType taxSchemeCompanyID = new CompanyIDType();
        PartyLegalEntityType partyLegalEntity  = new PartyLegalEntityType();
        TaxSchemeType taxScheme = new TaxSchemeType();
        taxScheme.setID(new IDType("VAT"));
        PartyTaxSchemeType partyTaxScheme = new PartyTaxSchemeType();
        partyTaxScheme.setTaxScheme(taxScheme);

        String peppolScheme = "0208";
        String peppolValue = "0535112789";
        endpointID.setSchemeID(peppolScheme);
        endpointID.setValue(peppolValue);

        idType.setSchemeID(peppolScheme);
        idType.setValue(peppolValue);

        partyIdentification.setID(idType);

        nameType.setValue("Softwareoplossing.be");
        name.setName(nameType);

        country.setIdentificationCode(new IdentificationCodeType("BE"));

        address.setStreetName("Geelstraat 4");
        address.setCityName("Hasselt");
        address.setPostalZone("3500");
        address.setCountry(country);

        taxSchemeCompanyID.setValue("BE" + peppolValue);
        partyTaxScheme.setCompanyID(taxSchemeCompanyID);

        companyID.setSchemeID(peppolScheme);
        companyID.setValue(peppolValue);
        partyLegalEntity.setRegistrationName("Softwareoplossing.be");
        partyLegalEntity.setCompanyID(companyID);


        PartyType party = new PartyType();
        party.setEndpointID(endpointID);
        party.setPartyIdentification(List.of(partyIdentification));
        party.setPartyName(List.of(name));
        party.setPostalAddress(address);
        party.addPartyTaxScheme(partyTaxScheme);
        party.setPartyLegalEntity(List.of(partyLegalEntity));

        SupplierPartyType supplier = new SupplierPartyType();
        supplier.setParty(party);
        return supplier;
    }

    protected List<TaxTotalType> getTaxTotal() {
        TaxCategoryType taxCategory = new TaxCategoryType();
        TaxSchemeType taxScheme = new TaxSchemeType();
        TaxAmountType taxAmount = new TaxAmountType();
        taxAmount.setCurrencyID("EUR");
        taxAmount.setValue(new BigDecimal("23.5"));
        taxScheme.setID(new IDType("VAT"));
        taxCategory.setID("S");
        taxCategory.setPercent(new BigDecimal("21"));
        taxCategory.setTaxScheme(taxScheme);

        TaxableAmountType taxableAmount = new TaxableAmountType();
        taxableAmount.setCurrencyID("EUR");
        taxableAmount.setValue(new BigDecimal("123.5"));

        TaxSubtotalType taxSubtotal = new TaxSubtotalType();
        taxSubtotal.setTaxableAmount(taxableAmount);
        taxSubtotal.setTaxAmount(taxAmount);
        taxSubtotal.setTaxCategory(taxCategory);

        TaxTotalType taxTotal = new TaxTotalType();
        taxTotal.setTaxAmount(taxAmount);
        taxTotal.addTaxSubtotal(taxSubtotal);
        return List.of(taxTotal);
    }

    protected MonetaryTotalType getLegalMonetaryTotal() {
        LineExtensionAmountType lineExtensionAmount = new LineExtensionAmountType();
        TaxExclusiveAmountType taxExclusiveAmount = new TaxExclusiveAmountType();
        TaxInclusiveAmountType taxInclusiveAmount = new TaxInclusiveAmountType();
        PayableAmountType payableAmount = new PayableAmountType();
        BigDecimal taxAmount = new BigDecimal("23.5");
        BigDecimal totalPrice = new BigDecimal("101");

        lineExtensionAmount.setCurrencyID("EUR");
        lineExtensionAmount.setValue(totalPrice);

        taxExclusiveAmount.setCurrencyID("EUR");
        taxExclusiveAmount.setValue(totalPrice);

        taxInclusiveAmount.setCurrencyID("EUR");
        taxInclusiveAmount.setValue(totalPrice.add(taxAmount));

        payableAmount.setCurrencyID("EUR");
        payableAmount.setValue(totalPrice.add(taxAmount));

        MonetaryTotalType monetaryTotal = new MonetaryTotalType();
        monetaryTotal.setLineExtensionAmount(lineExtensionAmount);
        monetaryTotal.setTaxExclusiveAmount(taxExclusiveAmount);
        monetaryTotal.setTaxInclusiveAmount(taxInclusiveAmount);
        monetaryTotal.setPayableAmount(payableAmount);
        return monetaryTotal;
    }

    protected FinancialAccountType getFinancialAccount() {
        FinancialAccountType financialAccountType = new FinancialAccountType();

        BranchType branchType = new BranchType();
        branchType.setID("KREDBEBB");
        financialAccountType.setID("BE23735028247703");
        financialAccountType.setName("SoftwareOplossing.be");
        financialAccountType.setFinancialInstitutionBranch(branchType);

        return financialAccountType;
    }

}
