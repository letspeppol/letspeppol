package org.letspeppol.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.dto.UblDocumentDto;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.Document;
import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.model.DocumentType;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.DocumentRepository;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceNotificationTest {

    @Test
    void notifiesOnceWhenSynchronizationCompletesAnEnabledOutgoingDocumentSuccessfully() {
        NotificationService notificationService = mock(NotificationService.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentService documentService = documentService(documentRepository, notificationService);
        Document document = document(true);
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        UblDocumentDto completedDocument = completedDocument(document, null);

        documentService.updateStatus(completedDocument);
        documentService.updateStatus(completedDocument);

        verify(notificationService, times(1)).notifyOutgoingDocument(document.getCompany(), document);
    }

    @Test
    void doesNotNotifyWhenSuccessfulOutgoingNotificationsAreDisabled() {
        NotificationService notificationService = mock(NotificationService.class);
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DocumentService documentService = documentService(documentRepository, notificationService);
        Document document = document(false);
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        documentService.updateStatus(completedDocument(document, null));

        verify(notificationService, never()).notifyOutgoingDocument(document.getCompany(), document);
    }

    private DocumentService documentService(DocumentRepository documentRepository, NotificationService notificationService) {
        return new DocumentService(
                mock(CompanyRepository.class),
                documentRepository,
                mock(ValidationService.class),
                notificationService,
                mock(JwtService.class),
                mock(UblInvoicePdfService.class),
                new ObjectMapper(),
                mock(WebClient.class),
                mock(Counter.class),
                mock(Counter.class),
                mock(Counter.class)
        );
    }

    private Document document(boolean enableEmailNotification) {
        Company company = new Company(
                "0208:BE0123456789", "0123456789", "BE0123456789", "Test Company NV", "Test User",
                "subscriber@example.com", "Brussels", "1000", "Rue de la Loi 1", "BE"
        );
        company.setEnableEmailNotification(enableEmailNotification);
        Document document = new Document(
                UUID.randomUUID(), DocumentDirection.OUTGOING, company.getPeppolId(), "0208:BE9876543210",
                Instant.now(), null, null, null, "<Invoice/>", null, null, null,
                "Customer NV", "INV-2026-0042", null, null, DocumentType.INVOICE,
                Currency.getInstance("EUR"), new BigDecimal("1234.56"), new BigDecimal("1020.30"),
                Instant.now(), Instant.now(), null, true
        );
        document.setCompany(company);
        return document;
    }

    private UblDocumentDto completedDocument(Document document, String processedStatus) {
        return new UblDocumentDto(
                document.getId(), document.getDirection(), document.getType(), document.getOwnerPeppolId(),
                document.getPartnerPeppolId(), document.getProxyOn(), document.getScheduledOn(), Instant.now(),
                processedStatus, document.getUbl()
        );
    }
}
