package org.letspeppol.proxy.config;

import lombok.RequiredArgsConstructor;
import org.letspeppol.proxy.service.ScradaService;
import org.letspeppol.proxy.service.UblDocumentSchedulerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class Scheduler {
    private final UblDocumentSchedulerService ublDocumentSchedulerService;
    private final ScradaService scradaService;

    @Scheduled(fixedDelayString = "${scheduler.send.delay-ms:1000}")
    public void sendDueDocuments() {
        ublDocumentSchedulerService.sendDueOutgoing();
    }

    @Scheduled(fixedDelayString = "${scheduler.synchronize.delay-ms:60000}")
    public void synchronizeDocuments() {
        ublDocumentSchedulerService.synchronizeOutgoingDocuments();
    }

    @Scheduled(fixedDelayString = "${scheduler.receive.delay-ms:300000}")
    public void receiveNewDocumentsFromScrada() {
        scradaService.receiveDocuments();
    }
}
