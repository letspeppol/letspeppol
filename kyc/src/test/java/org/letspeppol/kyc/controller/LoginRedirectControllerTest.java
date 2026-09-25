package org.letspeppol.kyc.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRedirectControllerTest {

    @Test
    void redirectsLoginAndTotpPagesToAureliaLogin() {
        LoginRedirectController controller = new LoginRedirectController("https://ui.example/");

        assertThat(controller.login(null).getUrl()).isEqualTo("https://ui.example/login");
        assertThat(controller.login("true").getUrl()).isEqualTo("https://ui.example/login?error");
    }
}
