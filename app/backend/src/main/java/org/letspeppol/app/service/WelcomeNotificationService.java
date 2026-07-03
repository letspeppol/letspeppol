package org.letspeppol.app.service;

import lombok.RequiredArgsConstructor;
import org.letspeppol.app.dto.WelcomeNotificationDto;
import org.letspeppol.app.dto.WelcomeNotificationsResponse;
import org.letspeppol.app.exception.NotFoundException;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.NotificationGroup;
import org.letspeppol.app.model.WelcomeNotification;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.WelcomeNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WelcomeNotificationService {

    private final CompanyRepository companyRepository;
    private final WelcomeNotificationRepository welcomeNotificationRepository;

    public WelcomeNotificationsResponse getNotificationsForCompany(String peppolId) {
        Company company = companyRepository.findByPeppolId(peppolId)
                .orElseThrow(() -> new NotFoundException("Company does not exist"));
        NotificationGroup companyGroup = company.getCompanyGroup() == null ? NotificationGroup.USER : company.getCompanyGroup();

        List<WelcomeNotificationDto> notifications = welcomeNotificationRepository
                .findAllByActiveTrueAndNotificationGroupInOrderByCreatedOnAscIdAsc(notificationGroupsFor(companyGroup))
                .stream()
                .map(this::toDto)
                .toList();

        return new WelcomeNotificationsResponse(peppolId, companyGroup, notifications);
    }

    private Set<NotificationGroup> notificationGroupsFor(NotificationGroup companyGroup) {
        return switch (companyGroup) {
            case USER -> Set.of(NotificationGroup.USER, NotificationGroup.ONCE);
            case SPECIAL -> Set.of(NotificationGroup.USER, NotificationGroup.SPECIAL, NotificationGroup.ONCE);
            case SPONSOR -> Set.of(NotificationGroup.USER, NotificationGroup.SPONSOR, NotificationGroup.ONCE);
            case EDITOR -> Set.of(NotificationGroup.EDITOR);
            case ONCE -> Set.of(NotificationGroup.ONCE);
        };
    }

    private WelcomeNotificationDto toDto(WelcomeNotification notification) {
        return new WelcomeNotificationDto(
                notification.getId(),
                notification.getHtmlCodeEn(),
                notification.getHtmlCodeNl(),
                notification.getHtmlCodeFr(),
                notification.getHtmlCodeDe(),
                notification.getNotificationGroup()
        );
    }
}
