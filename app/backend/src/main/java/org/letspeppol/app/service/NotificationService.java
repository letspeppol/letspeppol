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
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NotificationService {

    private final String notificationMailFrom;
    private final Resource emailNotificationTemplate;
    private final EmailJobRepository emailJobRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, String> cache = new HashMap<>();

    public NotificationService(@Value("${notification.mail.from}") String notificationMailFrom,
                               @Value("classpath:mail/notification-email_en.txt") Resource emailNotificationTemplate,
                               EmailJobRepository emailJobRepository,
                               ObjectMapper objectMapper,
                               ApplicationEventPublisher eventPublisher) {
        this.notificationMailFrom = notificationMailFrom;
        this.emailNotificationTemplate = emailNotificationTemplate;
        this.emailJobRepository = emailJobRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    public void notifyIncomingDocument(Company company, Document document) {
        try {
            String body = getEmailNotificationTemplateContents()
                    .replace("{{supplier}}", document.getPartnerName())
                    .replace("{{reference}}", document.getInvoiceReference())
                    .replace("{{total_amount}}", document.getAmount().toString())
                    .replace("{{uuid}}", document.getId().toString());

            DocumentNotificationEmailDto emailDto = new DocumentNotificationEmailDto(
                    notificationMailFrom,
                    company.getSubscriberEmail(),
                    company.getEmailNotificationCcList(),
                    null,
                    null,
                    "New invoice received",
                    body,
                    null,
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

    private String getEmailNotificationTemplateContents() throws IOException {
        String key = "en";
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        if (emailNotificationTemplate != null && emailNotificationTemplate.exists() && emailNotificationTemplate.isReadable()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(emailNotificationTemplate.getInputStream(), StandardCharsets.UTF_8))) {
                String txt = reader.lines().collect(Collectors.joining("\n"));
                cache.put(key, txt);
                return txt;
            } catch (Exception e) {
                log.warn("Failed to read activation template for key {}: {}", key, e.getMessage());
                throw e;
            }
        }
        throw new IOException("Email notification template not found or not readable");
    }
}
