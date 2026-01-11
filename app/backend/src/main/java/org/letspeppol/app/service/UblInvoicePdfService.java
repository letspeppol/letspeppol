package org.letspeppol.app.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.letspeppol.app.exception.UblException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

@Service
public class UblInvoicePdfService {

    public enum RenderMode {
        FINAL,
        DRAFT,
        PROFORMA
    }

    private static final String INVOICE_XSLT = "pdf/ubl-invoice-to-html.xsl";
    private static final String CREDITNOTE_XSLT = "pdf/ubl-creditnote-to-html.xsl";

    private final Templates invoiceToHtmlTemplates;
    private final Templates creditNoteToHtmlTemplates;

    public UblInvoicePdfService() {
        this.invoiceToHtmlTemplates = compileTemplates(INVOICE_XSLT);
        this.creditNoteToHtmlTemplates = compileTemplates(CREDITNOTE_XSLT);
    }

    public byte[] toPdf(String ublXml) {
        return toPdf(ublXml, RenderMode.FINAL);
    }

    public byte[] toPdf(String ublXml, RenderMode mode) {
        if (ublXml == null || ublXml.isBlank()) {
            throw new UblException("Missing UBL XML content");
        }
        if (mode == null) {
            mode = RenderMode.FINAL;
        }

        Templates templates = selectTemplatesForRoot(ublXml);
        String html = transformToHtml(ublXml, templates, mode);
        return renderHtmlToPdf(html);
    }

    private Templates selectTemplatesForRoot(String xml) {
        String root = detectRootLocalName(xml);
        if ("CreditNote".equals(root)) {
            return creditNoteToHtmlTemplates;
        }
        // default Invoice
        return invoiceToHtmlTemplates;
    }

    private String detectRootLocalName(String xml) {
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

            try (ByteArrayInputStream in = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
                XMLEventReader reader = factory.createXMLEventReader(in);
                while (reader.hasNext()) {
                    XMLEvent event = reader.nextEvent();
                    if (event.isStartElement()) {
                        StartElement start = event.asStartElement();
                        QName name = start.getName();
                        return name.getLocalPart();
                    }
                }
            }
        } catch (Exception e) {
            // Fall back to invoice templates.
        }
        return "Invoice";
    }

    private String transformToHtml(String ublXml, Templates templates, RenderMode mode) {
        try {
            Transformer transformer = templates.newTransformer();

            boolean isDraft = (mode == RenderMode.DRAFT || mode == RenderMode.PROFORMA);
            transformer.setParameter("renderNumber", isDraft ? "false" : "true");
            transformer.setParameter("watermarkText", isDraft ? mode.name() : "");

            StringWriter out = new StringWriter();
            transformer.transform(
                    new StreamSource(new StringReader(ublXml)),
                    new StreamResult(out)
            );
            return out.toString();
        } catch (TransformerException e) {
            throw new UblException("Failed to transform UBL XML to HTML: " + e.getMessage());
        }
    }

    private byte[] renderHtmlToPdf(String html) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, "classpath:/");
            builder.toStream(baos);
            builder.run();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new UblException("Failed to render PDF: " + e.getMessage());
        }
    }

    private Templates compileTemplates(String classpathLocation) {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

            ClassPathResource resource = new ClassPathResource(classpathLocation);
            try (var in = resource.getInputStream()) {
                StreamSource xsl = new StreamSource(in);
                xsl.setSystemId(resource.getURL().toExternalForm());
                return tf.newTemplates(xsl);
            }
        } catch (TransformerConfigurationException e) {
            throw new IllegalStateException("Invalid XSLT template: " + classpathLocation + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load XSLT template from classpath: " + classpathLocation, e);
        }
    }
}
