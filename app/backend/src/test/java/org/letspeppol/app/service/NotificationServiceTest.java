package org.letspeppol.app.service;

import org.junit.jupiter.api.Test;
import org.letspeppol.app.config.SponsorProperties;
import org.letspeppol.app.dto.DocumentNotificationEmailDto;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.Document;
import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.model.DocumentType;
import org.letspeppol.app.model.EmailJob;
import org.letspeppol.app.repository.EmailJobRepository;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private final EmailJobRepository emailJobRepository = mock(EmailJobRepository.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final NotificationService notificationService = new NotificationService(
            "noreply@letspeppol.org",
            new ClassPathResource("mail/notification-email_en.txt"),
            new ClassPathResource("mail/notification-email_en.html"),
            new ClassPathResource("mail/outgoing-notification-email_en.txt"),
            new ClassPathResource("mail/outgoing-notification-email_en.html"),
            new ClassPathResource("mail/error-notification-email_en.txt"),
            new ClassPathResource("mail/error-notification-email_en.html"),
            emailJobRepository,
            objectMapper,
            mock(ApplicationEventPublisher.class),
            sponsorProperties()
    );

    @Test
    void incomingNotificationLinksToInvoiceUnderCompanyPeppolId() {
        assertLinksToInvoiceUnderCompanyPeppolId(notificationService::notifyIncomingDocument);
    }

    @Test
    void outgoingNotificationLinksToInvoiceUnderCompanyPeppolId() {
        assertLinksToInvoiceUnderCompanyPeppolId(notificationService::notifyOutgoingDocument);
    }

    @Test
    void errorNotificationLinksToInvoiceUnderCompanyPeppolId() {
        assertLinksToInvoiceUnderCompanyPeppolId(notificationService::notifyDocumentError);
    }

    private void assertLinksToInvoiceUnderCompanyPeppolId(BiConsumer<Company, Document> notify) {
        when(emailJobRepository.save(any(EmailJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Company company = new Company(
                "0208:BE0123456789", "0123456789", "BE0123456789", "Test Company NV", "Test User",
                "subscriber@example.com", "Brussels", "1000", "Rue de la Loi 1", "BE"
        );
        Document document = new Document(
                UUID.randomUUID(), DocumentDirection.OUTGOING, company.getPeppolId(), "0208:BE9876543210",
                Instant.now(), null, null, null, "<Invoice/>", null, null, null,
                "Customer NV", "INV-2026-0042", null, null, DocumentType.INVOICE,
                Currency.getInstance("EUR"), new BigDecimal("1234.56"), new BigDecimal("1020.30"),
                Instant.now(), Instant.now(), null, true
        );

        notify.accept(company, document);

        ArgumentCaptor<EmailJob> job = ArgumentCaptor.forClass(EmailJob.class);
        verify(emailJobRepository).save(job.capture());
        DocumentNotificationEmailDto email = objectMapper.readValue(job.getValue().getPayload(), DocumentNotificationEmailDto.class);
        String link = "https://be.letspeppol.org/0208:BE0123456789/invoices/" + document.getId();
        assertThat(email.getText()).contains(link);
        assertThat(email.getHtml()).contains("href=\"" + link + "\"");
    }

    private static SponsorProperties sponsorProperties() {
        SponsorProperties.Sponsor sponsor = new SponsorProperties.Sponsor();
        sponsor.setName("Sponsor");
        sponsor.setLogo("sponsor.svg");
        sponsor.setUrl("https://sponsor.example");
        SponsorProperties properties = new SponsorProperties();
        properties.setList(List.of(sponsor));
        return properties;
    }
}
