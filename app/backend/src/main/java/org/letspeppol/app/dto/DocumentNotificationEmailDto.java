package org.letspeppol.app.dto;

import java.util.UUID;

public record DocumentNotificationEmailDto(
        String from,
        String to,
        String cc,
        String bcc,
        String replyTo,
        String subject,
        String text,
        String html,
        UUID documentId
) {}
