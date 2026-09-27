package org.letspeppol.kyc.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserAuthorizationErrorHandlerTest {

    private static final String UI_LOGIN_URL = "https://localpeppol.org:3001/login";

    private final BrowserAuthorizationErrorHandler handler = new BrowserAuthorizationErrorHandler(UI_LOGIN_URL);

    @Test
    void unavailableOwnershipSendsTheBrowserToTheLoginScreenWithAReason() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(browserRequest(), response, ownershipUnavailable());

        assertThat(response.getRedirectedUrl())
                .isEqualTo(UI_LOGIN_URL + "?error=" + BrowserAuthorizationErrorHandler.OWNERSHIP_UNAVAILABLE_REASON);
    }

    @Test
    void operatorFacingDescriptionsNeverReachTheLoginUrl() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(browserRequest(), response, new OAuth2AuthorizationCodeRequestAuthenticationException(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "OAuth 2.0 Parameter: client_id", null), null));

        assertThat(response.getRedirectedUrl()).isEqualTo(UI_LOGIN_URL + "?error=" + OAuth2ErrorCodes.INVALID_REQUEST);
    }

    @Test
    void theSilentReauthorizationFetchGetsTheReasonAsJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, ownershipUnavailable());

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(response.getContentAsString())
                .contains("\"error\":\"" + BrowserAuthorizationErrorHandler.OWNERSHIP_UNAVAILABLE_REASON + "\"");
    }

    @Test
    void aValidatedRedirectUriKeepsTheOAuthErrorRedirect() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthorizationCodeRequestAuthenticationToken authorizationRequest =
                new OAuth2AuthorizationCodeRequestAuthenticationToken(
                        "https://localpeppol.org/kyc/auth/oauth2/authorize",
                        "letspeppol-ui",
                        new TestingAuthenticationToken("user", "n/a"),
                        "https://localpeppol.org:3001/callback",
                        "state 1",
                        Set.of("openid"),
                        Map.of());

        handler.onAuthenticationFailure(browserRequest(), response, new OAuth2AuthorizationCodeRequestAuthenticationException(
                new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED, "Refused by the user", null),
                authorizationRequest));

        assertThat(response.getRedirectedUrl()).isEqualTo(
                "https://localpeppol.org:3001/callback"
                        + "?error=access_denied"
                        + "&error_description=Refused%20by%20the%20user"
                        + "&state=state%201");
    }

    @Test
    void nonOAuthFailuresDegradeToAServerError() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(browserRequest(), response, new BadCredentialsException("nope"));

        assertThat(response.getRedirectedUrl()).isEqualTo(UI_LOGIN_URL + "?error=" + OAuth2ErrorCodes.SERVER_ERROR);
    }

    private static MockHttpServletRequest browserRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        return request;
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationException ownershipUnavailable() {
        return new OAuth2AuthorizationCodeRequestAuthenticationException(
                new OAuth2Error(
                        OAuth2ErrorCodes.INVALID_REQUEST,
                        ActingOwnershipAuthorizationRequestConverter.OWNERSHIP_UNAVAILABLE_DESCRIPTION,
                        null),
                null);
    }
}
