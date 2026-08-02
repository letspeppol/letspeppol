package org.letspeppol.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.config.SponsorProperties;
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
import org.springframework.web.util.HtmlUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@Slf4j
public class NotificationService {

    private final String notificationMailFrom;
    private final Resource emailNotificationTemplate;
    private final Resource emailNotificationHtmlTemplate;
    private final Resource emailErrorTemplate;
    private final Resource emailErrorHtmlTemplate;
    private final EmailJobRepository emailJobRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SponsorProperties sponsorProperties;
    private final Map<String, String> cache = new HashMap<>();
    private final AtomicInteger sponsorIndex = new AtomicInteger(0);

    public NotificationService(@Value("${notification.mail.from}") String notificationMailFrom,
                               @Value("classpath:mail/notification-email_en.txt") Resource emailNotificationTemplate,
                               @Value("classpath:mail/notification-email_en.html") Resource emailNotificationHtmlTemplate,
                               @Value("classpath:mail/error-notification-email_en.txt") Resource emailErrorTemplate,
                               @Value("classpath:mail/error-notification-email_en.html") Resource emailErrorHtmlTemplate,
                               EmailJobRepository emailJobRepository,
                               ObjectMapper objectMapper,
                               ApplicationEventPublisher eventPublisher,
                               SponsorProperties sponsorProperties) {
        this.notificationMailFrom = notificationMailFrom;
        this.emailNotificationTemplate = emailNotificationTemplate;
        this.emailNotificationHtmlTemplate = emailNotificationHtmlTemplate;
        this.emailErrorTemplate = emailErrorTemplate;
        this.emailErrorHtmlTemplate = emailErrorHtmlTemplate;
        this.emailJobRepository = emailJobRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.sponsorProperties = sponsorProperties;
    }

    public void notifyIncomingDocument(Company company, Document document) {
        try {
            SponsorProperties.Sponsor sponsor = nextSponsor();

            String totalAmount = document.getAmountInclVat() != null ? document.getAmountInclVat().toString() : "";
            String text = getTemplateContents("en-txt", emailNotificationTemplate)
                    .replace("{{supplier}}", document.getPartnerName())
                    .replace("{{reference}}", document.getInvoiceReference())
                    .replace("{{totalAmount}}", totalAmount)
                    .replace("{{uuid}}", document.getId().toString());

            String html = null;
            if (sponsor != null) {
                String logoUrl = sponsorProperties.getBaseUrl() + sponsor.getLogo();
                text = text
                        .replace("{{supportedByText}}", sponsorProperties.getEmailText())
                        .replace("{{sponsorName}}", sponsor.getName())
                        .replace("{{sponsorUrl}}", sponsor.getUrl());

                html = getTemplateContents("en-html", emailNotificationHtmlTemplate)
                        .replace("{{supplier}}", document.getPartnerName())
                        .replace("{{reference}}", document.getInvoiceReference())
                        .replace("{{totalAmount}}", totalAmount)
                        .replace("{{uuid}}", document.getId().toString())
                        .replace("{{supportedByText}}", sponsorProperties.getEmailText())
                        .replace("{{sponsorName}}", sponsor.getName())
                        .replace("{{sponsorUrl}}", sponsor.getUrl())
                        .replace("{{sponsorLogoUrl}}", logoUrl);
            } else {
                // No sponsors configured — strip the placeholder lines from plain text
                text = text
                        .replace("\n--\n{{supportedByText}}: {{sponsorName}} ({{sponsorUrl}})\n", "");
            }

            DocumentNotificationEmailDto emailDto = new DocumentNotificationEmailDto(
                    notificationMailFrom,
                    company.getSubscriberEmail(),
                    company.getEmailNotificationCcList(),
                    null,
                    null,
                    "New invoice %s received from %s".formatted(document.getInvoiceReference(), document.getPartnerName()),
                    text,
                    html,
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

    /** Notify the company that one of its outgoing invoices was rejected by the Peppol AP. */
    public void notifyDocumentError(Company company, Document document) {
        try {
            SponsorProperties.Sponsor sponsor = nextSponsor();

            // Untrusted (UBL/AP) values: null-coalesced, substituted last, and HTML-escaped in the HTML body (see replaceExternalPlaceholders).
            String partner = Objects.toString(document.getPartnerName(), "");
            String reference = Objects.toString(document.getInvoiceReference(), "");
            String errorMessage = Objects.toString(document.getProcessedStatus(), "");

            String text = getTemplateContents("en-error-txt", emailErrorTemplate)
                    .replace("{{uuid}}", document.getId().toString());

            String html = null;
            if (sponsor != null) {
                String logoUrl = sponsorProperties.getBaseUrl() + sponsor.getLogo();
                text = text
                        .replace("{{supportedByText}}", sponsorProperties.getEmailText())
                        .replace("{{sponsorName}}", sponsor.getName())
                        .replace("{{sponsorUrl}}", sponsor.getUrl());

                html = getTemplateContents("en-error-html", emailErrorHtmlTemplate)
                        .replace("{{uuid}}", document.getId().toString())
                        .replace("{{supportedByText}}", sponsorProperties.getEmailText())
                        .replace("{{sponsorName}}", sponsor.getName())
                        .replace("{{sponsorUrl}}", sponsor.getUrl())
                        .replace("{{sponsorLogoUrl}}", logoUrl);
                html = replaceExternalPlaceholders(html, partner, reference, errorMessage, true);
            } else {
                // No sponsors configured — strip the placeholder lines from plain text
                text = text
                        .replace("\n--\n{{supportedByText}}: {{sponsorName}} ({{sponsorUrl}})\n", "");
            }
            text = replaceExternalPlaceholders(text, partner, reference, errorMessage, false);

            DocumentNotificationEmailDto emailDto = new DocumentNotificationEmailDto(
                    notificationMailFrom,
                    company.getSubscriberEmail(),
                    company.getEmailNotificationCcList(),
                    null,
                    null,
                    "Invoice %s could not be delivered".formatted(reference),
                    text,
                    html,
                    null //No attachment: the document failed to be delivered
            );

            String json = objectMapper.writeValueAsString(emailDto);
            EmailJob emailJob = EmailJob.builder()
                    .template(EmailJob.Template.DOCUMENT_ERROR)
                    .toAddress(company.getSubscriberEmail())
                    .payload(json)
                    .build();
            EmailJob saved = emailJobRepository.save(emailJob);
            eventPublisher.publishEvent(new EmailJobCreatedEvent(saved.getId()));
        } catch (JsonProcessingException e) {
            log.error("Failed to convert error email object to json");
        } catch (Exception e) {
            log.error("Unable to create error email notification for document {}", document.getId(), e);
        }
    }

    /** Substitutes the externally-influenced placeholders last; HTML-escapes them when {@code escape} is true. */
    private String replaceExternalPlaceholders(String content, String partner, String reference, String errorMessage, boolean escape) {
        return content
                .replace("{{partner}}", escape ? HtmlUtils.htmlEscape(partner) : partner)
                .replace("{{reference}}", escape ? HtmlUtils.htmlEscape(reference) : reference)
                .replace("{{errorMessage}}", escape ? HtmlUtils.htmlEscape(errorMessage) : errorMessage);
    }

    private SponsorProperties.Sponsor nextSponsor() {
        List<SponsorProperties.Sponsor> list = sponsorProperties.getList();
        if (list == null || list.isEmpty()) {
            return null;
        }
        int idx = sponsorIndex.getAndIncrement() % list.size();
        return list.get(idx);
    }

    private String getTemplateContents(String key, Resource resource) throws IOException {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        if (resource != null && resource.exists() && resource.isReadable()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String txt = reader.lines().collect(Collectors.joining("\n"));
                cache.put(key, txt);
                return txt;
            } catch (Exception e) {
                log.warn("Failed to read template for key {}: {}", key, e.getMessage());
                throw e;
            }
        }
        throw new IOException("Email template not found or not readable for key: " + key);
    }
}
