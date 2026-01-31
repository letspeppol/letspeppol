package org.letspeppol.app.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.service.EmailService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailJobEventListener {

    private final EmailService emailService;

    /**
     * Asynchronously triggers processing when a new job is created.
     */
    @Async
    @EventListener
    public void onEmailJobCreated(EmailJobCreatedEvent event) {
        try {
            emailService.processEmailJobs();
        } catch (Exception e) {
            log.warn("Failed to process email jobs after EmailJobCreatedEvent (jobId={}): {}", event.emailJobId(), e.getMessage());
        }
    }
}
