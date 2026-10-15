package org.letspeppol.kyc.service.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class UserInvitationEmailTemplateProvider {
    private static final Logger log = LoggerFactory.getLogger(UserInvitationEmailTemplateProvider.class);

    public record RenderedTemplate(String subject, String body) {}

    private final Resource defaultTemplateResource;
    private final String defaultSubject;
    private final Environment environment;
    private final ResourceLoader resourceLoader;

    private final Map<String, String> cache = new HashMap<>();

    public UserInvitationEmailTemplateProvider(
            @Value("classpath:mail/user-invitation-email_en.txt") Resource templateResource,
            @Value("${app.mail.subject.user-invitation:You have been invited to Let's Peppol}") String defaultSubject,
            Environment environment,
            ResourceLoader resourceLoader) {
        this.defaultTemplateResource = templateResource;
        this.defaultSubject = defaultSubject;
        this.environment = environment;
        this.resourceLoader = resourceLoader;
    }

    public RenderedTemplate render(String companyName, String inviterName, String invitationLink, long validDays, String languageTag) {
        String body = loadTemplate(languageTag)
                .replace("{{companyName}}", safe(companyName))
                .replace("{{inviterName}}", safe(inviterName))
                .replace("{{validDays}}", String.valueOf(validDays))
                .replace("{{invitationLink}}", invitationLink);
        String subject = resolveSubject(languageTag).replace("{{companyName}}", safe(companyName));
        return new RenderedTemplate(subject, body);
    }

    private String resolveSubject(String languageTag) {
        if (languageTag != null) {
            String subject = environment.getProperty("app.mail.subject.user-invitation." + languageTag);
            if (subject == null && languageTag.contains("-")) {
                subject = environment.getProperty("app.mail.subject.user-invitation." + languageTag.split("-", 2)[0]);
            }
            if (subject != null) return subject;
        }
        return defaultSubject;
    }

    private String loadTemplate(String languageTag) {
        List<String> candidates = new ArrayList<>();
        if (languageTag != null && !languageTag.isBlank()) {
            if (languageTag.contains("-")) candidates.add(languageTag);
            candidates.add(languageTag.split("-", 2)[0]);
        } else {
            candidates.add("en");
        }
        candidates.add("default");
        for (String key : candidates) {
            if (cache.containsKey(key)) return cache.get(key);
            Resource resource = selectResourceForKey(key);
            if (resource != null && resource.exists() && resource.isReadable()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String text = reader.lines().collect(Collectors.joining("\n"));
                    cache.put(key, text);
                    return text;
                } catch (Exception e) {
                    log.warn("Failed to read user invitation template for key {}: {}", key, e.getMessage());
                }
            }
        }
        String fallback = "Hello,\n\n{{inviterName}} has invited you to {{companyName}} on Let's Peppol. Accept the invitation by clicking: {{invitationLink}}\n\nRegards";
        cache.put("default", fallback);
        return fallback;
    }

    private Resource selectResourceForKey(String key) {
        if ("default".equals(key)) return defaultTemplateResource;
        return resourceLoader.getResource("classpath:mail/user-invitation-email_" + key + ".txt");
    }

    private String safe(String value) { return value == null ? "" : value; }
}
