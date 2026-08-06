package org.letspeppol.app.dto;

import org.letspeppol.app.model.NotificationGroup;

public record WelcomeNotificationDto(
        Long id,
        String htmlCodeEn,
        String htmlCodeNl,
        String htmlCodeFr,
        String htmlCodeDe,
        NotificationGroup notificationGroup
) {
}
