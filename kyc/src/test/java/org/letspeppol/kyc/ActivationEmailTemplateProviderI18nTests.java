package org.letspeppol.kyc;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.service.mail.ActivationEmailTemplateProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ActivationEmailTemplateProviderI18nTests {

    @Autowired
    ActivationEmailTemplateProvider provider;

    @Test
    void rendersDefaultEnglish() {
        var rendered = provider.render("BE0123456789", "http://example/act?token=abc");
        assertThat(rendered.body()).contains("verify");
    }

    @Test
    void rendersDutchTemplate() {
        var rendered = provider.render("BE0123456789", "http://example/act?token=abc", "nl");
        assertThat(rendered.body().toLowerCase()).contains("verifi");
    }
}

