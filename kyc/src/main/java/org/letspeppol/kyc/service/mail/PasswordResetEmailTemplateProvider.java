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
public class PasswordResetEmailTemplateProvider {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailTemplateProvider.class);

    public record RenderedTemplate(String subject, String body) {}

    private final Resource defaultTemplateResource;
    private final String defaultSubject;
    private final Environment environment;
    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new HashMap<>();

    public PasswordResetEmailTemplateProvider(
            @Value("classpath:mail/password-reset-email_en.txt") Resource templateResource, // English variant as canonical default
            @Value("${app.mail.subject.password-reset:Reset your password}") String defaultSubject,
            Environment environment,
            ResourceLoader resourceLoader) {
        this.defaultTemplateResource = templateResource;
        this.defaultSubject = defaultSubject;
        this.environment = environment;
        this.resourceLoader = resourceLoader;
    }

    public RenderedTemplate render(String resetLink) { return render(resetLink, null); }

    public RenderedTemplate render(String resetLink, String languageTag) {
        String template = loadTemplate(languageTag);
        String body = template.replace("{{resetLink}}", resetLink);
        String subject = resolveSubject(languageTag);
        return new RenderedTemplate(subject, body);
    }

    private String resolveSubject(String languageTag) {
        if (languageTag != null) {
            String subj = environment.getProperty("app.mail.subject.password-reset." + languageTag);
            if (subj == null && languageTag.contains("-")) {
                subj = environment.getProperty("app.mail.subject.password-reset." + languageTag.split("-",2)[0]);
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
            candidates.add("en"); // explicit English attempt when no language provided
        }
        candidates.add("default");
        for (String key : candidates) {
            if (cache.containsKey(key)) return cache.get(key);
            Resource r = selectResourceForKey(key);
            if (r != null && r.exists() && r.isReadable()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(r.getInputStream(), StandardCharsets.UTF_8))) {
                    String txt = reader.lines().collect(Collectors.joining("\n"));
                    cache.put(key, txt);
                    return txt;
                } catch (Exception e) {
                    log.warn("Could not read password reset template for key {}: {}", key, e.getMessage());
                }
            }
        }
        String fb = "Dear user,\n\nTo reset your password click: {{resetLink}}\nIf you did not request this you can ignore this email.\n\nRegards";
        cache.put("default", fb);
        return fb;
    }

    private Resource selectResourceForKey(String key) {
        if ("default".equals(key)) return defaultTemplateResource; // now points to _en variant
        return resourceLoader.getResource("classpath:mail/password-reset-email_" + key + ".txt");
    }
}
