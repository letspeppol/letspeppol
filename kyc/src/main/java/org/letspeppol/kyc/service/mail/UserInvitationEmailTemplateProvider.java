package org.letspeppol.kyc.service.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class UserInvitationEmailTemplateProvider {

    public record RenderedTemplate(String subject, String body) {}

    private final LocalizedMailTemplate template;

    public UserInvitationEmailTemplateProvider(
            @Value("${app.mail.subject.user-invitation:You have been invited to Let's Peppol}") String defaultSubject,
            Environment environment,
            ResourceLoader resourceLoader) {
        this.template = new LocalizedMailTemplate(
                "user-invitation-email",
                "app.mail.subject.user-invitation",
                defaultSubject,
                "Hello,\n\n{{inviterName}} has invited you to {{companyName}} on Let's Peppol. Accept the invitation by clicking: {{invitationLink}}\n\nRegards",
                environment,
                resourceLoader);
    }

    public RenderedTemplate render(String companyName, String inviterName, String invitationLink, long validDays, String languageTag) {
        String body = template.body(languageTag)
                .replace("{{companyName}}", safe(companyName))
                .replace("{{inviterName}}", safe(inviterName))
                .replace("{{validDays}}", String.valueOf(validDays))
                .replace("{{invitationLink}}", invitationLink);
        String subject = template.subject(languageTag).replace("{{companyName}}", safe(companyName));
        return new RenderedTemplate(subject, body);
    }

    private String safe(String value) { return value == null ? "" : value; }
}
