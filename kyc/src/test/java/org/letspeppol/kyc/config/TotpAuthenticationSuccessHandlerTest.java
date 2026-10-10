package org.letspeppol.kyc.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.model.Account;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TotpAuthenticationSuccessHandlerTest {

    private final ObjectMapper objectMapper = new JsonMapper();
    private final TotpAuthenticationSuccessHandler handler = new TotpAuthenticationSuccessHandler(objectMapper);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void jsonSuccessReturnsAuthenticatedAndClearsStaleState() throws Exception {
        MockHttpServletRequest request = jsonRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession().setAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID, 99L);
        saveRequest(request, response);

        handler.onAuthenticationSuccess(request, response, authentication(false));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(response.getContentAsByteArray()).get("status").asString())
                .isEqualTo(BrowserAuthenticationSupport.STATUS_AUTHENTICATED);
        assertThat(request.getSession().getAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID))
                .isNull();
        assertThat(new HttpSessionRequestCache().getRequest(request, response)).isNull();
    }

    @Test
    void jsonTotpSuccessReturnsAcceptedButLeavesSessionUnauthenticated() throws Exception {
        MockHttpServletRequest request = jsonRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession().setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.createEmptyContext());
        saveRequest(request, response);

        handler.onAuthenticationSuccess(request, response, authentication(true));

        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(objectMapper.readTree(response.getContentAsByteArray()).get("status").asString())
                .isEqualTo(BrowserAuthenticationSupport.STATUS_TOTP_REQUIRED);
        assertThat(request.getSession().getAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID))
                .isEqualTo(42L);
        assertThat(request.getSession().getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(new HttpSessionRequestCache().getRequest(request, response)).isNull();
    }

    @Test
    void htmlTotpSuccessPreservesRedirectBehavior() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication(true));

        assertThat(response.getRedirectedUrl()).isEqualTo("/auth/browser/totp-verify");
    }

    private MockHttpServletRequest jsonRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        return request;
    }

    private void saveRequest(MockHttpServletRequest request, MockHttpServletResponse response) {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName(null);
        requestCache.saveRequest(request, response);
    }

    private UsernamePasswordAuthenticationToken authentication(boolean totpEnabled) {
        Account account = Account.builder()
                .id(42L)
                .externalId(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("hash")
                .verified(true)
                .totpEnabled(totpEnabled)
                .build();
        AccountUserDetails principal = new AccountUserDetails(account);
        return UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
    }
}
