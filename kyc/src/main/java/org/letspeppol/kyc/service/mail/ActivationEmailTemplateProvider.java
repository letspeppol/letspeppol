package org.letspeppol.kyc.service.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class ActivationEmailTemplateProvider {

    public record RenderedTemplate(String subject, String body) {}

    private final LocalizedMailTemplate template;

    public ActivationEmailTemplateProvider(
            @Value("${app.mail.subject.activation:Confirm your email address}") String defaultSubject,
            Environment environment,
            ResourceLoader resourceLoader) {
        this.template = new LocalizedMailTemplate(
                "activation-email",
                "app.mail.subject.activation",
                defaultSubject,
                "Dear user,\n\nPlease verify your email for company {{peppolId}} by clicking: {{activationLink}}\n\nRegards",
                environment,
                resourceLoader);
    }

    public RenderedTemplate render(String peppolId, String activationLink) {
        return render(peppolId, activationLink, null);
    }

    public RenderedTemplate render(String peppolId, String activationLink, String languageTag) {
        String body = template.body(languageTag)
                .replace("{{peppolId}}", safe(peppolId))
                .replace("{{activationLink}}", activationLink);
        String subject = template.subject(languageTag).replace("{{peppolId}}", safe(peppolId));
        return new RenderedTemplate(subject, body);
    }

    private String safe(String v) { return v == null ? "" : v; }
}
