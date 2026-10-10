package org.letspeppol.proxy.config;

import org.junit.jupiter.api.Test;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.service.AccessPointServiceInterface;
import org.letspeppol.proxy.service.UblDocumentSchedulerService;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulerTest {

    @Test
    void receivesDocumentsFromEveryActiveAccessPoint() {
        UblDocumentSchedulerService documentSchedulerService = mock(UblDocumentSchedulerService.class);
        AccessPointServiceInterface recommandService = mock(AccessPointServiceInterface.class);
        AccessPointServiceInterface scradaService = mock(AccessPointServiceInterface.class);

        Scheduler scheduler = new Scheduler(documentSchedulerService, List.of(recommandService, scradaService));
        scheduler.receiveNewDocuments();

        verify(recommandService).receiveDocuments();
        verify(scradaService).receiveDocuments();
    }

    @Test
    void continuesReceivingWhenOneAccessPointFails() {
        UblDocumentSchedulerService documentSchedulerService = mock(UblDocumentSchedulerService.class);
        AccessPointServiceInterface recommandService = mock(AccessPointServiceInterface.class);
        AccessPointServiceInterface scradaService = mock(AccessPointServiceInterface.class);
        doThrow(new RuntimeException("Recommand unavailable"))
                .when(recommandService).receiveDocuments();
        when(recommandService.getType()).thenReturn(AccessPoint.RECOMMAND);

        Scheduler scheduler = new Scheduler(documentSchedulerService, List.of(recommandService, scradaService));
        scheduler.receiveNewDocuments();

        verify(recommandService).receiveDocuments();
        verify(scradaService).receiveDocuments();
    }
}
