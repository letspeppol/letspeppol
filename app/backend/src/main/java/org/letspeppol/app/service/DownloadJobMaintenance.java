package org.letspeppol.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.events.DownloadJobCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DownloadJobMaintenance {

    private final DownloadJobService downloadJobService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(cron = "0 * * * * *")
    public void expireDownloadJobs() {
        try {
            downloadJobService.expireReadyJobs();
        } catch (Exception e) {
            log.error("Failed to expire UBL archive download jobs", e);
        }
    }

    @Scheduled(cron = "30 * * * * *")
    public void recoverDownloadJobs() {
        try {
            downloadJobService.recoverAndFindPendingJobs()
                    .forEach(id -> eventPublisher.publishEvent(new DownloadJobCreatedEvent(id)));
        } catch (Exception e) {
            log.error("Failed to recover UBL archive download jobs", e);
        }
    }
}
