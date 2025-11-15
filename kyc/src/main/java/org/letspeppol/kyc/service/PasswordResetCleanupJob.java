package org.letspeppol.kyc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class PasswordResetCleanupJob {
    private final PasswordResetService passwordResetService;

    @Scheduled(cron = "0 10 * * * *")
    public void purgeExpired() {
        long removed = passwordResetService.purgeExpired();
        if (removed > 0) {
            log.info("Purged {} expired password reset tokens", removed);
        }
    }
}

