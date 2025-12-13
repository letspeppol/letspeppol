package org.letspeppol.proxy.config;

import lombok.RequiredArgsConstructor;
import org.letspeppol.proxy.service.ScradaService;
import org.letspeppol.proxy.service.UblDocumentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class Scheduler {
    private final UblDocumentService ublDocumentService;
    private final ScradaService scradaService;

    @Scheduled(fixedDelayString = "${scheduler.send.delay-ms:1000}")
    public void sendDueDocuments() {
        ublDocumentService.sendDueOutgoing();
    }

    @Scheduled(fixedDelayString = "${scheduler.synchronize.delay-ms:60000}")
    public void synchronizeDocuments() {
        ublDocumentService.synchronizeOutgoingDocuments();
    }

    @Scheduled(fixedDelayString = "${scheduler.receive.delay-ms:300000}")
    public void receiveNewDocumentsFromScrada() {
        scradaService.receiveDocuments();
    }
}
