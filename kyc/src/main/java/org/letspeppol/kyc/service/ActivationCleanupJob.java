package org.letspeppol.kyc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ActivationCleanupJob {
    private final ActivationService activationService;

    @Scheduled(cron = "0 5 * * * *")
    public void purgeExpiredToken() {
        long removed = activationService.purgeExpired();
        if (removed > 0) {
            log.info("Purged {} expired activation tokens", removed);
        }
    }
}
