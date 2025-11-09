package io.tubs.kyc;

import io.tubs.kyc.service.mail.PasswordResetEmailTemplateProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PasswordResetEmailTemplateProviderTests {

    @Autowired
    PasswordResetEmailTemplateProvider provider;

    @Test
    void rendersTemplateAndReplacesPlaceholder() {
        var rendered = provider.render("http://example/reset?token=abc123");
        assertThat(rendered.subject()).containsIgnoringCase("reset");
        assertThat(rendered.body()).contains("http://example/reset?token=abc123");
        assertThat(rendered.body()).doesNotContain("{{resetLink}}");
        // Ensure no accidental Java code artifacts remain
        assertThat(rendered.body()).doesNotContain("@Column", "PASSWORD_RESET_EMAIL_TEMPLATE", "public boolean isExpired");
    }

    @Test
    void rendersDutchTemplateWhenLocaleProvided() {
        var rendered = provider.render("http://example/reset?token=abc123", "nl");
        assertThat(rendered.body()).contains("wachtwoord".toLowerCase());
    }

    @Test
    void rendersEnglishTemplateWhenLocaleProvided() {
        var rendered = provider.render("http://example/reset?token=abc123", "en");
        assertThat(rendered.body()).contains("http://example/reset?token=abc123");
        assertThat(rendered.body()).doesNotContain("{{resetLink}}");
    }
}
