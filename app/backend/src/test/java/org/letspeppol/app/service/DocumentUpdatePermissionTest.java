package org.letspeppol.app.service;

import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.Document;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.DocumentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentUpdatePermissionTest {

    private static final String PEPPOL_ID = "0208:0123456789";
    private static final String STORED_UBL = "<Invoice>stored</Invoice>";

    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentService service = new DocumentService(
            companyRepository,
            documentRepository,
            mock(ValidationService.class),
            mock(NotificationService.class),
            mock(UblInvoicePdfService.class),
            new JsonMapper(),
            mock(WebClient.class),
            mock(WebClient.class),
            mock(Counter.class),
            mock(Counter.class),
            mock(Counter.class));

    @Test
    void userWithoutSendCannotChangeADocumentThatIsQueuedAtProxy() {
        Document queued = document(null, Instant.now());

        assertThatThrownBy(() -> service.update(PEPPOL_ID, queued.getId(), "<Invoice>changed</Invoice>", true, null, "token", false))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(queued.getUbl()).isEqualTo(STORED_UBL);
        assertThat(queued.getDraftedOn()).isNull();
    }

    @Test
    void userWithoutSendCannotTurnAnUndeliveredDocumentBackIntoADraft() {
        Document undelivered = document(null, null);

        assertThatThrownBy(() -> service.update(PEPPOL_ID, undelivered.getId(), "<Invoice>changed</Invoice>", true, null, "token", false))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(undelivered.getDraftedOn()).isNull();
    }

    @Test
    void userWithoutSendIsNotRefusedOnADraft() {
        Document draft = document(Instant.now(), null);

        assertThat(catchThrowable(() -> service.update(PEPPOL_ID, draft.getId(), "<Invoice>changed</Invoice>", true, null, "token", false)))
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    void userWithSendIsNotRefusedOnAQueuedDocument() {
        Document queued = document(null, Instant.now());

        assertThat(catchThrowable(() -> service.update(PEPPOL_ID, queued.getId(), "<Invoice>changed</Invoice>", true, null, "token", true)))
                .isNotInstanceOf(AccessDeniedException.class);
    }

    private Document document(Instant draftedOn, Instant proxyOn) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setOwnerPeppolId(PEPPOL_ID);
        document.setUbl(STORED_UBL);
        document.setDraftedOn(draftedOn);
        document.setProxyOn(proxyOn);
        when(companyRepository.findByPeppolId(PEPPOL_ID)).thenReturn(Optional.of(mock(Company.class)));
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        return document;
    }
}
