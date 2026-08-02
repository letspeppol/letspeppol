package org.letspeppol.app.repository;

import org.letspeppol.app.model.NotificationGroup;
import org.letspeppol.app.model.WelcomeNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface WelcomeNotificationRepository extends JpaRepository<WelcomeNotification, Long> {

    List<WelcomeNotification> findAllByActiveTrueAndNotificationGroupInOrderByCreatedOnAscIdAsc(Collection<NotificationGroup> notificationGroups);
}
