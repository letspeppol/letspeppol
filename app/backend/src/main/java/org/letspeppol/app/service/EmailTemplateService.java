package org.letspeppol.app.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.model.EmailJob;
import org.springframework.beans.factory.annotation.Value;
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
public class EmailTemplateService {

    private final Resource documentNotificationTemplate;
    private final Resource accountantCustomerLinkTemplate;
    private final Map<EmailJob.Template, String> templateCache = new HashMap<>();

    public EmailTemplateService(
            @Value("classpath:mail/notification-email_en.txt") Resource documentNotificationTemplate,
            @Value("classpath:mail/accountant-customer-link-email_en.txt") Resource accountantCustomerLinkTemplate) {
        this.documentNotificationTemplate = documentNotificationTemplate;
        this.accountantCustomerLinkTemplate = accountantCustomerLinkTemplate;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing email template cache...");
        try {
            loadTemplate(EmailJob.Template.DOCUMENT_NOTIFICATION, documentNotificationTemplate);
            loadTemplate(EmailJob.Template.ACCOUNTANT_CUSTOMER_LINK, accountantCustomerLinkTemplate);
            log.info("Email template cache initialized successfully with {} templates", templateCache.size());
        } catch (Exception e) {
            log.error("Failed to initialize email template cache", e);
        }
    }

    private void loadTemplate(EmailJob.Template templateType, Resource resource) {
        try {
            if (resource != null && resource.exists() && resource.isReadable()) {
                String content = readResourceContent(resource);
                templateCache.put(templateType, content);
                log.debug("Loaded template: {}", templateType);
            } else {
                log.warn("Template resource not found or not readable for: {}", templateType);
            }
        } catch (Exception e) {
            log.error("Failed to load template {}: {}", templateType, e.getMessage(), e);
        }
    }

    private String readResourceContent(Resource resource) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    /**
     * Retrieves a cached email template by template type.
     *
     * @param templateType the template type to retrieve
     * @return the template content
     * @throws IllegalArgumentException if the template is not found in cache
     */
    public String getTemplate(EmailJob.Template templateType) {
        String template = templateCache.get(templateType);
        if (template == null) {
            log.error("Template not found in cache: {}", templateType);
            throw new IllegalArgumentException("Email template not found: " + templateType);
        }
        return template;
    }

    /**
     * Retrieves a cached email template and replaces placeholders with provided values.
     *
     * @param templateType the template type to retrieve
     * @param placeholders map of placeholder keys to their replacement values (without {{ }})
     * @return the template content with placeholders replaced
     * @throws IllegalArgumentException if the template is not found in cache
     */
    public String getTemplateWithPlaceholders(EmailJob.Template templateType, Map<String, String> placeholders) {
        String template = getTemplate(templateType);

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            template = template.replace(placeholder, value);
        }

        return template;
    }
}

