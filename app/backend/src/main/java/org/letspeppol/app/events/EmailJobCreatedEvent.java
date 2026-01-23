package org.letspeppol.app.events;

/**
 * Published after an EmailJob is persisted.
 */
public record EmailJobCreatedEvent(Long emailJobId) {
}
