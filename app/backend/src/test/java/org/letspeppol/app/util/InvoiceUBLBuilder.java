package org.letspeppol.app.util;

import com.helger.ubl21.UBL21Marshaller;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_21.*;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.*;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class InvoiceUBLBuilder extends AbstractUBLBuilder {

    public InvoiceType buildInvoice() {
        final InvoiceType inv = new InvoiceType();

        // Peppol BIS Billing 3.0 / EN16931
        inv.setCustomizationID("urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0");
        inv.setProfileID("urn:fdc:peppol.eu:2017:poacc:billing:01:1.0");

        inv.setAccountingSupplierParty(getSupplierParty());
        inv.setAccountingCustomerParty(getCustomerParty());

        inv.setID("INV202600023");
        inv.setIssueDate(LocalDate.now());
        inv.setDueDate(LocalDate.now().plusDays(30));
        inv.setInvoiceTypeCode("380");
        inv.setDocumentCurrencyCode("EUR");

        // Example references
        OrderReferenceType orderReference = new OrderReferenceType();
        orderReference.setID("PO-2026-0001");
        inv.setOrderReference(orderReference);

        BuyerReferenceType buyerReference = new BuyerReferenceType();
        buyerReference.setValue("BUYER-REF-001");
        inv.setBuyerReference(buyerReference);

        // Payment means + terms (example)
        inv.setPaymentMeans(examplePaymentMeans());
        inv.setPaymentTerms(examplePaymentTerms());

        // Lines + totals (example)
        inv.setInvoiceLine(exampleInvoiceLines());
        inv.setTaxTotal(getTaxTotal());
        inv.setLegalMonetaryTotal(getLegalMonetaryTotal());

        inv.setNote(List.of(new NoteType("""
                This is just a quite long note that could span multiple lines. We could tell here that the sky is blue
                but this is not always true. Hey that rhymes ^^. What comes after monday? It may be tuesday, wednesday, thursday and so on. 
                Will Half-Life 3 ever come out? I sure look forward to it.
                """)));

        return inv;
    }

    /**
     * Convenience: build the invoice and marshal to XML bytes.
     */
    public byte[] buildInvoiceXmlBytes() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        UBL21Marshaller.invoice().write(buildInvoice(), byteArrayOutputStream);
        return byteArrayOutputStream.toByteArray();
    }

    private List<InvoiceLineType> exampleInvoiceLines() {
        // Multiple lines for more realistic rendering/tests.
        return List.of(
                invoiceLine("1", "Consulting services", "Creating diverse services", new BigDecimal("1"), "H87", new BigDecimal("101.00")),
                invoiceLine("2", "Support retainer", "Support calls and mails", new BigDecimal("2"), "H87", new BigDecimal("50.00")),
                invoiceLine("3", "Hosting", "3 HPC servers", new BigDecimal("1"), "C62", new BigDecimal("10.00"))
        );
    }

    private static InvoiceLineType invoiceLine(String id, String name, String description, BigDecimal quantity, String unitCode, BigDecimal unitPrice) {
        InvoiceLineType line = new InvoiceLineType();

        line.setID(new IDType(id));

        InvoicedQuantityType qty = new InvoicedQuantityType();
        qty.setUnitCode(unitCode);
        qty.setValue(quantity);
        line.setInvoicedQuantity(qty);

        BigDecimal lineTotal = unitPrice.multiply(quantity);
        LineExtensionAmountType lineExt = new LineExtensionAmountType();
        lineExt.setCurrencyID("EUR");
        lineExt.setValue(lineTotal);
        line.setLineExtensionAmount(lineExt);

        TaxSchemeType taxSchemeType = new TaxSchemeType();
        taxSchemeType.setID("VAT");
        TaxCategoryType taxCategory = new TaxCategoryType();
        taxCategory.setID("S");
        taxCategory.setPercent(new BigDecimal("21"));
        taxCategory.setTaxScheme(taxSchemeType);

        ItemIdentificationType itemIdentificationType = new ItemIdentificationType();
        itemIdentificationType.setID(new IDType("9873242"));

        DescriptionType desc = new DescriptionType(description);
        ItemType item = new ItemType();
        item.setName(name);
        item.setDescription(List.of(desc));
        item.setClassifiedTaxCategory(List.of(taxCategory));
        item.setSellersItemIdentification(itemIdentificationType);
        line.setItem(item);

        ItemPropertyType itemPropertyType1 = new ItemPropertyType();
        itemPropertyType1.setName("Color");
        itemPropertyType1.setValue("black");
        ItemPropertyType itemPropertyType2 = new ItemPropertyType();
        itemPropertyType2.setName("Size");
        itemPropertyType2.setValue("xl");
        item.setAdditionalItemProperty(List.of(itemPropertyType1, itemPropertyType2));

        PriceAmountType priceAmount = new PriceAmountType();
        priceAmount.setCurrencyID("EUR");
        priceAmount.setValue(unitPrice);

        PriceType price = new PriceType();
        price.setPriceAmount(priceAmount);
        line.setPrice(price);

        return line;
    }

    private List<PaymentTermsType> examplePaymentTerms() {
        NoteType note = new NoteType("30 days");
        PaymentTermsType paymentTermsType = new PaymentTermsType();
        paymentTermsType.setNote(List.of(note));
        return List.of(paymentTermsType);
    }

    private List<PaymentMeansType> examplePaymentMeans() {
        PaymentMeansCodeType paymentMeansCode = new PaymentMeansCodeType();
        paymentMeansCode.setName("Credit transfer");
        paymentMeansCode.setValue("30");

        PaymentIDType paymentId = new PaymentIDType();
        paymentId.setValue("INV202600023");

        PaymentMeansType paymentMeans = new PaymentMeansType();
        paymentMeans.setPaymentMeansCode(paymentMeansCode);
        paymentMeans.setPaymentID(List.of(paymentId));
        paymentMeans.setPayeeFinancialAccount(getFinancialAccount());

        return List.of(paymentMeans);
    }
}
