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
public class ActivationEmailTemplateProvider {
    private static final Logger log = LoggerFactory.getLogger(ActivationEmailTemplateProvider.class);

    public record RenderedTemplate(String subject, String body) {}

    private final Resource defaultTemplateResource;
    private final String defaultSubject;
    private final Environment environment;
    private final ResourceLoader resourceLoader;

    private final Map<String, String> cache = new HashMap<>(); // key: variant or "default"

    public ActivationEmailTemplateProvider(
            @Value("classpath:mail/activation-email_en.txt") Resource templateResource, // use English variant as canonical default
            @Value("${app.mail.subject.activation:Confirm your email address}") String defaultSubject,
            Environment environment,
            ResourceLoader resourceLoader) {
        this.defaultTemplateResource = templateResource;
        this.defaultSubject = defaultSubject;
        this.environment = environment;
        this.resourceLoader = resourceLoader;
    }

    public RenderedTemplate render(String peppolId, String activationLink) {
        return render(peppolId, activationLink, null);
    }

    public RenderedTemplate render(String peppolId, String activationLink, String languageTag) {
        String template = loadTemplate(languageTag);
        String body = template
                .replace("{{peppolId}}", safe(peppolId))
                .replace("{{activationLink}}", activationLink);
        String subject = resolveSubject(languageTag).replace("{{peppolId}}", safe(peppolId));
        return new RenderedTemplate(subject, body);
    }

    private String resolveSubject(String languageTag) {
        if (languageTag != null) {
            String subj = environment.getProperty("app.mail.subject.activation." + languageTag);
            if (subj == null && languageTag.contains("-")) {
                String lang = languageTag.split("-",2)[0];
                subj = environment.getProperty("app.mail.subject.activation." + lang);
            }
            if (subj != null) return subj;
        }
        return defaultSubject;
    }

    private String loadTemplate(String languageTag) {
        List<String> candidates = new ArrayList<>();
        if (languageTag != null && !languageTag.isBlank()) {
            if (languageTag.contains("-")) candidates.add(languageTag);
            candidates.add(languageTag.split("-",2)[0]);
        } else {
            // No explicit language -> attempt English explicit variant first
            candidates.add("en");
        }
        candidates.add("default");
        for (String key : candidates) {
            if (cache.containsKey(key)) return cache.get(key);
            Resource res = selectResourceForKey(key);
            if (res != null && res.exists() && res.isReadable()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                    String txt = reader.lines().collect(Collectors.joining("\n"));
                    cache.put(key, txt);
                    return txt;
                } catch (Exception e) {
                    log.warn("Failed to read activation template for key {}: {}", key, e.getMessage());
                }
            }
        }
        String fb = "Dear user,\n\nPlease verify your email for company {{peppolId}} by clicking: {{activationLink}}\n\nRegards";
        cache.put("default", fb);
        return fb;
    }

    private Resource selectResourceForKey(String key) {
        if ("default".equals(key)) return defaultTemplateResource; // now points to _en variant
        return resourceLoader.getResource("classpath:mail/activation-email_" + key + ".txt");
    }

    private String safe(String v) { return v == null ? "" : v; }
}
