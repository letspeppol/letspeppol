package org.letspeppol.kyc;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.service.mail.UserInvitationEmailTemplateProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class UserInvitationEmailTemplateProviderTests {

    private static final String LINK = "http://example/invitation?token=abc123";

    @Autowired
    UserInvitationEmailTemplateProvider provider;

    @Test
    void rendersDefaultEnglishAndReplacesEveryPlaceholder() {
        var rendered = provider.render("Acme bv", "Alice Admin", LINK, 7, null);

        assertThat(rendered.subject()).contains("Acme bv").doesNotContain("{{");
        assertThat(rendered.body())
                .contains("Alice Admin has invited you", "Acme bv", LINK, "valid for 7 days")
                .doesNotContain("{{");
    }

    @Test
    void rendersDutchTemplate() {
        var rendered = provider.render("Acme bv", "Alice Admin", LINK, 7, "nl-BE");

        assertThat(rendered.body())
                .contains("Alice Admin heeft u uitgenodigd", "Acme bv", LINK, "7 dagen geldig")
                .doesNotContain("{{");
    }

    @Test
    void unknownLanguageFallsBackToEnglish() {
        var rendered = provider.render("Acme bv", "Alice Admin", LINK, 7, "fr");

        assertThat(rendered.body()).contains("has invited you", LINK);
    }
}
