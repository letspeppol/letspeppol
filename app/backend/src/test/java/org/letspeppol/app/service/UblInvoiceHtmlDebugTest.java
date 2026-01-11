package org.letspeppol.app.service;

import com.helger.ubl21.UBL21Marshaller;
import lombok.SneakyThrows;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.util.InvoiceUBLBuilder;

import javax.xml.XMLConstants;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Debug helper test:
 * - Generates a sample UBL invoice using {@link InvoiceUBLBuilder}
 * - Transforms it with the same XSLT used for PDF rendering
 * - Writes the XHTML to build/debug/ so you can open it in a browser
 */
class UblInvoiceHtmlDebugTest {

    @SneakyThrows
    @Test
    void writeGeneratedInvoiceHtmlToBuildFolder() {
        InvoiceType invoiceType = new InvoiceUBLBuilder().buildInvoice();

        ByteArrayOutputStream xmlOut = new ByteArrayOutputStream();
        UBL21Marshaller.invoice().write(invoiceType, xmlOut);
        String ublXml = xmlOut.toString(StandardCharsets.UTF_8);

        String xhtml = transformToHtml(ublXml);

        Path dir = Path.of("build", "debug");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("invoice.xhtml"), xhtml, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("invoice.xml"), ublXml, StandardCharsets.UTF_8);
    }

    private String transformToHtml(String ublInvoiceXml) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

        Templates templates;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("pdf/ubl-invoice-to-html.xsl")) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource pdf/ubl-invoice-to-html.xsl");
            }
            templates = tf.newTemplates(new StreamSource(in));
        }

        Transformer transformer = templates.newTransformer();
        transformer.setParameter("watermarkText", UblInvoicePdfService.RenderMode.PROFORMA);
        StringWriter out = new StringWriter();
        transformer.transform(new StreamSource(new StringReader(ublInvoiceXml)), new StreamResult(out));
        return out.toString();
    }
}
