package org.letspeppol.app.service;

import com.helger.ubl21.UBL21Marshaller;
import com.helger.xml.serialize.read.DOMReader;
import com.helger.xml.serialize.read.DOMReaderSettings;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_21.AttachmentType;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_21.DocumentReferenceType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.DocumentDescriptionType;
import oasis.names.specification.ubl.schema.xsd.commonbasiccomponents_21.EmbeddedDocumentBinaryObjectType;
import oasis.names.specification.ubl.schema.xsd.creditnote_21.CreditNoteType;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import org.hibernate.service.spi.ServiceException;
import org.letspeppol.app.exception.UblException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.RequestContextFilter;
import org.w3c.dom.Document;

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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Service
public class UblInvoicePdfService {

    private final RequestContextFilter requestContextFilter;

    public enum RenderMode {
        FINAL,
        DRAFT,
        PROFORMA
    }

    private static final String INVOICE_XSLT = "pdf/ubl-invoice-to-html.xsl";
    private static final String CREDITNOTE_XSLT = "pdf/ubl-creditnote-to-html.xsl";
    private static final String GENERATED_INVOICE_ID = "generated_invoice";

    private final Templates invoiceToHtmlTemplates;
    private final Templates creditNoteToHtmlTemplates;

    public UblInvoicePdfService(RequestContextFilter requestContextFilter) {
        this.invoiceToHtmlTemplates = compileTemplates(INVOICE_XSLT);
        this.creditNoteToHtmlTemplates = compileTemplates(CREDITNOTE_XSLT);
        this.requestContextFilter = requestContextFilter;
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

    public String addRenderedPdfToUbl(String ublXml, String invoiceNumber) {
        byte[] updatedUbl = addAttachmentToUbl(ublXml, invoiceNumber);
        return new String(updatedUbl, Charset.defaultCharset());
    }

    private DocumentReferenceType createDocumentReferenceType(String invoiceNumber, byte[] pdfBytes) {
        EmbeddedDocumentBinaryObjectType embeddedDocumentBinaryObjectType = new EmbeddedDocumentBinaryObjectType();
        embeddedDocumentBinaryObjectType.setFilename(invoiceNumber + ".pdf");
        embeddedDocumentBinaryObjectType.setMimeCode("application/pdf");
        embeddedDocumentBinaryObjectType.setValue(pdfBytes);

        AttachmentType attachmentType = new AttachmentType();
        attachmentType.setEmbeddedDocumentBinaryObject(embeddedDocumentBinaryObjectType);

        DocumentReferenceType documentReferenceType = new DocumentReferenceType();
        documentReferenceType.setID(GENERATED_INVOICE_ID);
        documentReferenceType.setAttachment(attachmentType);
        return documentReferenceType;
    }

    private byte[] addAttachmentToUbl(String originalUbl, String invoiceNumber) {
        try {
            // Parse XML as invoice
            Document doc = DOMReader.readXMLDOM(
                    new ByteArrayInputStream(originalUbl.getBytes(StandardCharsets.UTF_8)),
                    new DOMReaderSettings().setSchema(UBL21Marshaller.invoice().getSchema())
            );
            if (doc != null) {
                final InvoiceType invoice = UBL21Marshaller.invoice().read(doc);
                if (invoice == null) {
                    throw new UblException("Could not parse invoice ubl");
                }
                if (!removeGeneratedPdfAttachments(invoice.getAdditionalDocumentReference())) {
                    log.warn("Ubl has no generated invoice placeholder, not adding a new one");
                    return originalUbl.getBytes(StandardCharsets.UTF_8);
                }
                byte[] pdfBytes = toPdf(originalUbl);
                DocumentReferenceType documentReferenceType = createDocumentReferenceType(invoiceNumber, pdfBytes);
                invoice.getAdditionalDocumentReference().addFirst(documentReferenceType);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                UBL21Marshaller.invoice().write(invoice, baos);
                return baos.toByteArray();
            }

            // Parse XML as credit note
            doc = DOMReader.readXMLDOM(
                    new ByteArrayInputStream(originalUbl.getBytes(StandardCharsets.UTF_8)),
                    new DOMReaderSettings().setSchema(UBL21Marshaller.creditNote().getSchema())
            );
            if (doc != null) {
                final CreditNoteType creditNote = UBL21Marshaller.creditNote().read(doc);
                if (creditNote == null) {
                    throw new UblException("Could not parse credit note ubl");
                }
                if (!removeGeneratedPdfAttachments(creditNote.getAdditionalDocumentReference())) {
                    log.warn("Ubl already has generated credit note, not adding a new one");
                    return originalUbl.getBytes(StandardCharsets.UTF_8);
                }
                byte[] pdfBytes = toPdf(originalUbl);
                DocumentReferenceType documentReferenceType = createDocumentReferenceType(invoiceNumber, pdfBytes);
                creditNote.getAdditionalDocumentReference().addFirst(documentReferenceType);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                UBL21Marshaller.creditNote().write(creditNote, baos);
                return baos.toByteArray();
            }
            throw new UblException("Unknown ubl type");
        } catch (Exception e) {
            log.error("Could not parse ubl", e);
            throw e;
        }
    }

    private boolean removeGeneratedPdfAttachments(List<DocumentReferenceType> documentReferenceTypes) {
        return documentReferenceTypes.removeIf(documentReference ->
                documentReference.getAttachment() != null &&
                        documentReference.getAttachment().getEmbeddedDocumentBinaryObject() != null &&
                        documentReference.getIDValue() != null &&
                        GENERATED_INVOICE_ID.equals(documentReference.getIDValue())
        );
    }
}
