package org.letspeppol.kyc.service.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetEmailTemplateProvider {

    public record RenderedTemplate(String subject, String body) {}

    private final LocalizedMailTemplate template;

    public PasswordResetEmailTemplateProvider(
            @Value("${app.mail.subject.password-reset:Reset your password}") String defaultSubject,
            Environment environment,
            ResourceLoader resourceLoader) {
        this.template = new LocalizedMailTemplate(
                "password-reset-email",
                "app.mail.subject.password-reset",
                defaultSubject,
                "Dear user,\n\nTo reset your password click: {{resetLink}}\nIf you did not request this you can ignore this email.\n\nRegards",
                environment,
                resourceLoader);
    }

    public RenderedTemplate render(String resetLink) { return render(resetLink, null); }

    public RenderedTemplate render(String resetLink, String languageTag) {
        String body = template.body(languageTag).replace("{{resetLink}}", resetLink);
        String subject = template.subject(languageTag);
        return new RenderedTemplate(subject, body);
    }
}
