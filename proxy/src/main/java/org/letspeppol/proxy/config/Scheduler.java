package org.letspeppol.proxy.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.proxy.service.AccessPointServiceInterface;
import org.letspeppol.proxy.service.UblDocumentSchedulerService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Component
public class Scheduler {
    private final UblDocumentSchedulerService ublDocumentSchedulerService;
    private final List<AccessPointServiceInterface> accessPointServices;

    @Scheduled(fixedDelayString = "${scheduler.send.delay-ms:1000}")
    public void sendDueDocuments() {
        ublDocumentSchedulerService.sendDueOutgoing();
    }

    @Scheduled(fixedDelayString = "${scheduler.synchronize.delay-ms:60000}")
    public void synchronizeDocuments() {
        ublDocumentSchedulerService.synchronizeOutgoingDocuments();
    }

    @Scheduled(fixedDelayString = "${scheduler.receive.delay-ms:300000}")
    public void receiveNewDocuments() {
        for (AccessPointServiceInterface accessPointService : accessPointServices) {
            try {
                accessPointService.receiveDocuments();
            } catch (Exception e) {
                log.error("Failed to receive documents from access point {}", accessPointService.getType(), e);
            }
        }
    }
}
