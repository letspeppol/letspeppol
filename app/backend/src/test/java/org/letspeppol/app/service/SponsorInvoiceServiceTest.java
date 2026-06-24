package org.letspeppol.app.service;

import org.junit.jupiter.api.Test;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.SponsorInvoice;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;

class SponsorInvoiceServiceTest {

    private final SponsorInvoiceService service = new SponsorInvoiceService(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
    );

    @Test
    void buildInvoiceXmlChargesBelgianVatForBelgianCustomer() throws Exception {
        Company customer = new Company(
                "0208:0123456789",
                "0123456789",
                "BE0123456789",
                "Belgian Customer",
                "Director",
                "director@example.test",
                "Brussels",
                "1000",
                "Main Street 1",
                "BE"
        );

        String xml = buildInvoiceXml(customer);

        assertThat(xml).contains("<cbc:TaxAmount currencyID=\"EUR\">21.00</cbc:TaxAmount>");
        assertThat(xml).contains("<cbc:TaxInclusiveAmount currencyID=\"EUR\">121.00</cbc:TaxInclusiveAmount>");
        assertThat(xml).contains("<cbc:ID>S</cbc:ID>");
        assertThat(xml).contains("<cbc:Percent>21</cbc:Percent>");
        assertThat(xml).doesNotContain("Reverse charge");
        assertThat(xml).doesNotContain("VATEX-EU-AE");
    }

    @Test
    void buildInvoiceXmlAppliesReverseChargeForEuCustomerOutsideBelgium() throws Exception {
        Company customer = new Company(
                "9925:NL123456789B01",
                "123456789-B01",
                "NL123456789B01",
                "Dutch Customer",
                "Director",
                "director@example.test",
                "Amsterdam",
                "1000 AA",
                "Main Street 1",
                "NL"
        );

        String xml = buildInvoiceXml(customer);

        assertThat(xml).contains("<cbc:TaxAmount currencyID=\"EUR\">0.00</cbc:TaxAmount>");
        assertThat(xml).contains("<cbc:TaxInclusiveAmount currencyID=\"EUR\">100.00</cbc:TaxInclusiveAmount>");
        assertThat(xml).contains("<cbc:PayableAmount currencyID=\"EUR\">100.00</cbc:PayableAmount>");
        assertThat(xml).contains("<cbc:ID>AE</cbc:ID>");
        assertThat(xml).contains("<cbc:Percent>0</cbc:Percent>");
        assertThat(xml).contains("<cbc:TaxExemptionReasonCode>VATEX-EU-AE</cbc:TaxExemptionReasonCode>");
        assertThat(xml).contains("<cbc:TaxExemptionReason>Reverse charge</cbc:TaxExemptionReason>");
        assertThat(xml).doesNotContain("VAT due by recipient");
    }

    private String buildInvoiceXml(Company customer) throws Exception {
        SponsorInvoice sponsorInvoice = new SponsorInvoice(
                customer,
                Instant.parse("2026-05-29T12:00:00Z"),
                new BigDecimal("100.00"),
                Currency.getInstance("EUR"),
                "Sponsor",
                "Thanks",
                "IN_2026_0001"
        );

        Method method = SponsorInvoiceService.class.getDeclaredMethod("buildInvoiceXml", Company.class, SponsorInvoice.class);
        method.setAccessible(true);
        return (String) method.invoke(service, customer, sponsorInvoice);
    }
}
