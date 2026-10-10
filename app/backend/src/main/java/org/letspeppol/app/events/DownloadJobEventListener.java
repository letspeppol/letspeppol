package org.letspeppol.app.events;

import lombok.RequiredArgsConstructor;
import org.letspeppol.app.service.DownloadJobProcessor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DownloadJobEventListener {

    private final DownloadJobProcessor downloadJobProcessor;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onDownloadJobCreated(DownloadJobCreatedEvent event) {
        downloadJobProcessor.process(event.downloadJobId());
    }
}
