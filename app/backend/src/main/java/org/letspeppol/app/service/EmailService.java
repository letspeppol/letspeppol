package org.letspeppol.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helger.ubl21.UBL21Marshaller;
import com.helger.xml.serialize.read.DOMReader;
import com.helger.xml.serialize.read.DOMReaderSettings;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import lombok.extern.slf4j.Slf4j;
import oasis.names.specification.ubl.schema.xsd.commonaggregatecomponents_21.DocumentReferenceType;
import oasis.names.specification.ubl.schema.xsd.creditnote_21.CreditNoteType;
import oasis.names.specification.ubl.schema.xsd.invoice_21.InvoiceType;
import org.letspeppol.app.dto.DocumentNotificationEmailDto;
import org.letspeppol.app.dto.DocumentNotificationEmailDto;
import org.letspeppol.app.dto.EmailDto;
import org.letspeppol.app.exception.NotFoundException;
import org.letspeppol.app.model.Document;
import org.letspeppol.app.model.DocumentType;
import org.letspeppol.app.model.EmailJob;
import org.letspeppol.app.repository.DocumentRepository;
import org.letspeppol.app.repository.EmailJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class EmailService {

    private final EmailJobRepository emailJobRepository;
    private final DocumentRepository documentRepository;
    private final UblInvoicePdfService ublInvoicePdfService;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;
    private final Duration minDelayBetweenEmails;

    private volatile long lastSentNanos = 0L;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EmailService(EmailJobRepository emailJobRepository,
                        DocumentRepository documentRepository,
                        UblInvoicePdfService ublInvoicePdfService,
                        JavaMailSender mailSender,
                        ObjectMapper objectMapper,
                        @Value("${email.jobs.rate-limit:PT1S}") Duration minDelayBetweenEmails) {
        this.emailJobRepository = emailJobRepository;
        this.documentRepository = documentRepository;
        this.ublInvoicePdfService = ublInvoicePdfService;
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
        this.minDelayBetweenEmails = minDelayBetweenEmails;
    }

    @Scheduled(fixedDelayString = "${email.jobs.poll-delay:PT5S}")
    public void processEmailJobs() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            List<EmailJob> jobs = emailJobRepository.findAllByStatusOrderByCreatedOnAsc(EmailJob.Status.PENDING);
            if (jobs.isEmpty()) return;

            for (EmailJob job : jobs) {
                try {
                    rateLimit();
                    send(job);
                    job.setStatus(EmailJob.Status.SENT);
                    job.setSentAt(Instant.now());
                    emailJobRepository.save(job);
                } catch (Exception e) {
                    job.setStatus(EmailJob.Status.FAILED);
                    emailJobRepository.save(job);
                    log.warn("Failed to send email job {}: {}", job.getId(), e.getMessage());
                }
            }
        } finally {
            running.set(false);
        }
    }

    private void send(EmailJob job) throws Exception {
        if (job.getPayload() == null || job.getPayload().isBlank()) {
            throw new IllegalArgumentException("Email job payload is empty");
        }
        log.info("Sending email notification to {}", job.getToAddress());

        // Parse payload based on template type
        EmailDto dto;
        if (job.getTemplate() == EmailJob.Template.DOCUMENT_NOTIFICATION) {
            dto = objectMapper.readValue(job.getPayload(), DocumentNotificationEmailDto.class);
        } else {
            dto = objectMapper.readValue(job.getPayload(), EmailDto.class);
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

        if (dto.getFrom() != null && !dto.getFrom().isBlank()) {
            helper.setFrom(dto.getFrom());
        }

        helper.setTo(dto.getTo());
        setOptionalRecipients(helper, dto.getCc(), true);
        setOptionalRecipients(helper, dto.getBcc(), false);

        if (dto.getReplyTo() != null && !dto.getReplyTo().isBlank()) {
            helper.setReplyTo(dto.getReplyTo());
        }

        helper.setSubject(dto.getSubject() == null ? "" : dto.getSubject());

        String htmlContent = (dto.getHtml() != null && !dto.getHtml().isBlank()) ? dto.getHtml() : null;
        String textContent = (dto.getText() != null && !dto.getText().isBlank()) ? dto.getText() : "";

        if (htmlContent != null) {
            helper.setText(textContent, htmlContent);
        } else {
            helper.setText(textContent, false);
        }

        // Only attach document if this is a DocumentNotificationEmailDto with a documentId
        if (dto instanceof DocumentNotificationEmailDto documentDto && documentDto.getDocumentId() != null) {
            Document document = documentRepository.findById(documentDto.getDocumentId()).orElseThrow(() -> new NotFoundException("Document does not exist"));
            helper.addAttachment(
                    document.getInvoiceReference() + ".xml",
                    new ByteArrayDataSource(document.getUbl().getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_XML_VALUE)
            );

            addPdfAttachment(document, documentDto, helper);
        }

        mailSender.send(message);
    }

    private void addPdfAttachment(Document document, DocumentNotificationEmailDto dto, MimeMessageHelper helper) throws MessagingException {
        boolean pdfAttachmentAdded = false;
        if (document.getType().equals(DocumentType.INVOICE)) {
            org.w3c.dom.Document doc = DOMReader.readXMLDOM(
                    new ByteArrayInputStream(document.getUbl().getBytes(StandardCharsets.UTF_8)),
                    new DOMReaderSettings().setSchema(UBL21Marshaller.invoice().getSchema())
            );

            if (doc != null) {
                final InvoiceType invoice = UBL21Marshaller.invoice().read(doc);
                if (invoice == null) {
                    log.error("Could not parse invoice UBL {}", dto.getDocumentId());
                } else {

                    for (DocumentReferenceType documentReferenceType : invoice.getAdditionalDocumentReference()) {
                        if (documentReferenceType.getAttachment() != null
                                && documentReferenceType.getAttachment().getEmbeddedDocumentBinaryObject() != null
                                && MediaType.APPLICATION_PDF_VALUE.equals(documentReferenceType.getAttachment().getEmbeddedDocumentBinaryObject().getMimeCode())) {
                            String filename = document.getInvoiceReference();
                            if (documentReferenceType.getAttachment().getEmbeddedDocumentBinaryObject().getFilename() != null) {
                                filename = documentReferenceType.getAttachment().getEmbeddedDocumentBinaryObject().getFilename();
                            }
                            helper.addAttachment(
                                    filename,
                                    new ByteArrayDataSource(documentReferenceType.getAttachment().getEmbeddedDocumentBinaryObject().getValue(), MediaType.APPLICATION_PDF_VALUE)
                            );
                            pdfAttachmentAdded = true;
                        }
                    }
                }
            }
        } else if (document.getType().equals(DocumentType.CREDIT_NOTE)) {
            org.w3c.dom.Document doc = DOMReader.readXMLDOM(
                    new ByteArrayInputStream(document.getUbl().getBytes(StandardCharsets.UTF_8)),
                    new DOMReaderSettings().setSchema(UBL21Marshaller.creditNote().getSchema())
            );

            if (doc != null) {
                final CreditNoteType creditNote = UBL21Marshaller.creditNote().read(doc);
                if (creditNote == null) {
                    log.error("Could not parse invoice UBL {}", dto.getDocumentId());
                } else {

                    for (DocumentReferenceType documentReferenceType : creditNote.getAdditionalDocumentReference()) {
                        if (documentReferenceType.getAttachment() != null
                                && documentReferenceType.getAttachment().getEmbeddedDocumentBinaryObject() != null
                                && MediaType.APPLICATION_PDF_VALUE.equals(documentReferenceType.getAttachment().getEmbeddedDocumentBinaryObject().getMimeCode())) {
                            String filename = document.getInvoiceReference();
                            if (documentReferenceType.getAttachment().getEmbeddedDocumentBinaryObject().getFilename() != null) {
                                filename = documentReferenceType.getAttachment().getEmbeddedDocumentBinaryObject().getFilename();
                            }
                            helper.addAttachment(
                                    filename,
                                    new ByteArrayDataSource(documentReferenceType.getAttachment().getEmbeddedDocumentBinaryObject().getValue(), MediaType.APPLICATION_PDF_VALUE)
                            );
                            pdfAttachmentAdded = true;
                        }
                    }
                }
            }
        }
        if (!pdfAttachmentAdded) {
            byte[] pdf = ublInvoicePdfService.toPdf(document.getUbl());
            helper.addAttachment(
                    document.getInvoiceReference() + ".pdf",
                    new ByteArrayDataSource(pdf, MediaType.APPLICATION_PDF_VALUE)
            );
        }
    }

    private void setOptionalRecipients(MimeMessageHelper helper, String recipients, boolean cc) throws Exception {
        if (recipients == null || recipients.isBlank()) return;

        String[] parts = Arrays.stream(recipients.split("[;,]"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);

        if (parts.length == 0) return;

        if (cc) {
            helper.setCc(parts);
        } else {
            helper.setBcc(parts);
        }
    }

    private synchronized void rateLimit() {
        if (minDelayBetweenEmails == null || minDelayBetweenEmails.isZero() || minDelayBetweenEmails.isNegative()) {
            return;
        }

        long minNanos = minDelayBetweenEmails.toNanos();
        long now = System.nanoTime();

        if (lastSentNanos != 0L) {
            long elapsed = now - lastSentNanos;
            long remaining = minNanos - elapsed;
            if (remaining > 0) {
                try {
                    Thread.sleep(Duration.ofNanos(remaining).toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        lastSentNanos = System.nanoTime();
    }
}
