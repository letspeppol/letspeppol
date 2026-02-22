package org.letspeppol.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.dto.DocumentNotificationEmailDto;
import org.letspeppol.app.events.EmailJobCreatedEvent;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.Document;
import org.letspeppol.app.model.EmailJob;
import org.letspeppol.app.repository.EmailJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class NotificationService {

    private final String notificationMailFrom;
    private final EmailTemplateService emailTemplateService;
    private final EmailJobRepository emailJobRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(@Value("${notification.mail.from}") String notificationMailFrom,
                               EmailTemplateService emailTemplateService,
                               EmailJobRepository emailJobRepository,
                               ObjectMapper objectMapper,
                               ApplicationEventPublisher eventPublisher) {
        this.notificationMailFrom = notificationMailFrom;
        this.emailTemplateService = emailTemplateService;
        this.emailJobRepository = emailJobRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    public void notifyIncomingDocument(Company company, Document document) {
        try {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("supplier", document.getPartnerName());
            placeholders.put("reference", document.getInvoiceReference());
            placeholders.put("totalAmount", document.getAmount().toString());
            placeholders.put("uuid", document.getId().toString());

            String body = emailTemplateService.getTemplateWithPlaceholders(
                    EmailJob.Template.DOCUMENT_NOTIFICATION,
                    placeholders
            );

            DocumentNotificationEmailDto emailDto = new DocumentNotificationEmailDto(
                    notificationMailFrom,
                    company.getSubscriberEmail(),
                    company.getEmailNotificationCcList(),null,null,
                    "New invoice %s received from %s".formatted(document.getInvoiceReference(), document.getPartnerName()),
                    body,null,
                    company.isAddAttachmentToNotification() ? document.getId() : null
            );

            String json = objectMapper.writeValueAsString(emailDto);
            EmailJob emailJob = EmailJob.builder()
                    .toAddress(company.getSubscriberEmail())
                    .payload(json)
                    .build();
            EmailJob saved = emailJobRepository.save(emailJob);
            eventPublisher.publishEvent(new EmailJobCreatedEvent(saved.getId()));
        } catch (JsonProcessingException e) {
            log.error("Failed to convert email object to json");
        } catch (Exception e) {
            log.error("Unable to create email notification");
        }
    }
}
