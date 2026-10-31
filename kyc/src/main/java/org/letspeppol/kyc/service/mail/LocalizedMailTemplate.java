package org.letspeppol.kyc.service.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

final class LocalizedMailTemplate {
    private static final Logger log = LoggerFactory.getLogger(LocalizedMailTemplate.class);
    private static final String DEFAULT_KEY = "default";
    private static final String DEFAULT_LANGUAGE = "en";

    private final String name;
    private final String subjectProperty;
    private final String defaultSubject;
    private final String fallbackBody;
    private final Environment environment;
    private final ResourceLoader resourceLoader;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    LocalizedMailTemplate(
            String name,
            String subjectProperty,
            String defaultSubject,
            String fallbackBody,
            Environment environment,
            ResourceLoader resourceLoader) {
        this.name = name;
        this.subjectProperty = subjectProperty;
        this.defaultSubject = defaultSubject;
        this.fallbackBody = fallbackBody;
        this.environment = environment;
        this.resourceLoader = resourceLoader;
    }

    String subject(String languageTag) {
        if (languageTag != null) {
            String subject = environment.getProperty(subjectProperty + "." + languageTag);
            if (subject == null && languageTag.contains("-")) {
                subject = environment.getProperty(subjectProperty + "." + languageTag.split("-", 2)[0]);
            }
            if (subject != null) return subject;
        }
        return defaultSubject;
    }

    String body(String languageTag) {
        for (String key : candidates(languageTag)) {
            String cached = cache.get(key);
            if (cached != null) return cached;
            Resource resource = resourceFor(key);
            if (resource.exists() && resource.isReadable()) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    String text = reader.lines().collect(Collectors.joining("\n"));
                    cache.put(key, text);
                    return text;
                } catch (Exception e) {
                    log.warn("Failed to read {} template for key {}: {}", name, key, e.getMessage());
                }
            }
        }
        cache.put(DEFAULT_KEY, fallbackBody);
        return fallbackBody;
    }

    private static List<String> candidates(String languageTag) {
        List<String> candidates = new ArrayList<>();
        if (languageTag != null && !languageTag.isBlank()) {
            if (languageTag.contains("-")) candidates.add(languageTag);
            candidates.add(languageTag.split("-", 2)[0]);
        } else {
            candidates.add(DEFAULT_LANGUAGE);
        }
        candidates.add(DEFAULT_KEY);
        return candidates;
    }

    private Resource resourceFor(String key) {
        String language = DEFAULT_KEY.equals(key) ? DEFAULT_LANGUAGE : key;
        return resourceLoader.getResource("classpath:mail/" + name + "_" + language + ".txt");
    }
}
