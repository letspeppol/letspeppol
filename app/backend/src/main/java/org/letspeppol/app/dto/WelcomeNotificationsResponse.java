package org.letspeppol.app.dto;

import org.letspeppol.app.model.NotificationGroup;

import java.util.List;

public record WelcomeNotificationsResponse(
        String peppolId,
        NotificationGroup companyGroup,
        List<WelcomeNotificationDto> notifications
) {
}
