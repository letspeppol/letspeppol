package org.letspeppol.kyc.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OidcLogoutIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anIdTokenHintOfAPurgedAuthorizationSendsTheBrowserToTheLoginScreen() throws Exception {
        mockMvc.perform(get("/auth/browser/logout")
                        .queryParam("id_token_hint", "id-token-of-a-purged-authorization")
                        .queryParam("post_logout_redirect_uri", "http://localhost:9000/login")
                        .queryParam("client_id", "letspeppol-ui")
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:9000/login?error=invalid_token"));
    }
}
