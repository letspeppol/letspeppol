package org.letspeppol.app.util;

import com.helger.ubl21.UBL21Marshaller;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_21.*;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.*;
import oasis.names.specification.ubl.schema.xsd.creditnote_21.CreditNoteType;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class CreditNoteUBLBuilder extends AbstractUBLBuilder {

    public CreditNoteType buildCreditNote() {
        CreditNoteType cn = new CreditNoteType();

        cn.setCustomizationID("urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0");
        cn.setProfileID("urn:fdc:peppol.eu:2017:poacc:billing:01:1.0");

        cn.setAccountingSupplierParty(getSupplierParty());
        cn.setAccountingCustomerParty(getCustomerParty());

        cn.setID("CN202600001");
        cn.setIssueDate(LocalDate.now());
        cn.setCreditNoteTypeCode("381");
        cn.setDocumentCurrencyCode("EUR");

        OrderReferenceType orderReference = new OrderReferenceType();
        orderReference.setID("PO-2026-0001");
        cn.setOrderReference(orderReference);

        BuyerReferenceType buyerReference = new BuyerReferenceType();
        buyerReference.setValue("BUYER-REF-001");
        cn.setBuyerReference(buyerReference);

        // Reference the original invoice (example)
        BillingReferenceType billingRef = new BillingReferenceType();
        DocumentReferenceType invRef = new DocumentReferenceType();
        invRef.setID("INV202600023");
        billingRef.setInvoiceDocumentReference(invRef);
        cn.setBillingReference(List.of(billingRef));

        cn.setCreditNoteLine(exampleLines());
        cn.setTaxTotal(getTaxTotal());
        cn.setLegalMonetaryTotal(getLegalMonetaryTotal());

        cn.setNote(List.of(new NoteType("""
                This is just a quite long note that could span multiple lines. We could tell here that the sky is blue
                but this is not always true. Hey that rhymes ^^. What comes after monday? It may be tuesday, wednesday, thursday and so on. 
                Will Half-Life 3 ever come out? I sure look forward to it.
                """)));

        return cn;
    }

    public byte[] buildCreditNoteXmlBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        UBL21Marshaller.creditNote().write(buildCreditNote(), baos);
        return baos.toByteArray();
    }

    private List<CreditNoteLineType> exampleLines() {
        return List.of(
                creditNoteLine("1", "Discount", "Volume discount", new BigDecimal("1"), "C62", new BigDecimal("-10.00")),
                creditNoteLine("2", "Correction", "Overbilled hours correction", new BigDecimal("1"), "H87", new BigDecimal("-25.00"))
        );
    }

    private static CreditNoteLineType creditNoteLine(String id, String name, String description, BigDecimal quantity, String unitCode, BigDecimal unitPrice) {
        CreditNoteLineType line = new CreditNoteLineType();
        line.setID(new IDType(id));

        CreditedQuantityType qty = new CreditedQuantityType();
        qty.setUnitCode(unitCode);
        qty.setValue(quantity);
        line.setCreditedQuantity(qty);

        BigDecimal lineTotal = unitPrice.multiply(quantity);
        LineExtensionAmountType lineExt = new LineExtensionAmountType();
        lineExt.setCurrencyID("EUR");
        lineExt.setValue(lineTotal);
        line.setLineExtensionAmount(lineExt);

        ItemIdentificationType itemIdentificationType = new ItemIdentificationType();
        itemIdentificationType.setID(new IDType("9873242"));

        DescriptionType desc = new DescriptionType(description);
        ItemType item = new ItemType();
        item.setName(name);
        item.setDescription(List.of(desc));
        item.setSellersItemIdentification(itemIdentificationType);
        line.setItem(item);

        PriceAmountType priceAmount = new PriceAmountType();
        priceAmount.setCurrencyID("EUR");
        priceAmount.setValue(unitPrice);

        PriceType price = new PriceType();
        price.setPriceAmount(priceAmount);
        line.setPrice(price);

        return line;
    }
}

