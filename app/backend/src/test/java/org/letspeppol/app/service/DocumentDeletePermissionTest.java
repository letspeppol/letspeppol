package org.letspeppol.app.service;

import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.DocumentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentDeletePermissionTest {

    private static final String PEPPOL_ID = "0208:0123456789";

    private final DocumentRepository documentRepository = mock(DocumentRepository.class);
    private final DocumentService service = new DocumentService(
            mock(CompanyRepository.class),
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
    void nonAdminCannotDeleteADocumentThatIsNoLongerADraft() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsByIdAndOwnerPeppolIdAndDraftedOnIsNull(id, PEPPOL_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(PEPPOL_ID, id, false)).isInstanceOf(AccessDeniedException.class);
        verify(documentRepository, never()).deleteByIdAndOwnerPeppolId(id, PEPPOL_ID);
    }

    @Test
    void nonAdminDeletesADraft() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsByIdAndOwnerPeppolIdAndDraftedOnIsNull(id, PEPPOL_ID)).thenReturn(false);

        service.delete(PEPPOL_ID, id, false);

        verify(documentRepository).deleteByIdAndOwnerPeppolId(id, PEPPOL_ID);
    }

    @Test
    void adminDeletesASentDocument() {
        UUID id = UUID.randomUUID();
        when(documentRepository.existsByIdAndOwnerPeppolIdAndDraftedOnIsNull(id, PEPPOL_ID)).thenReturn(true);

        service.delete(PEPPOL_ID, id, true);

        verify(documentRepository).deleteByIdAndOwnerPeppolId(id, PEPPOL_ID);
    }
}
