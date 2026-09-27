package org.letspeppol.kyc.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;

import static org.assertj.core.api.Assertions.assertThat;

class JsonAwareAuthenticationFailureHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonAwareAuthenticationFailureHandler handler =
            new JsonAwareAuthenticationFailureHandler(objectMapper);

    @Test
    void jsonBadCredentialsReturnsGenericUnauthorizedResponse() throws Exception {
        MockHttpServletRequest request = jsonRequest();
        request.getSession().setAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID, 42L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("wrong password"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertErrorCode(response, KycErrorCodes.AUTHENTCATION_FAILED);
        assertThat(request.getSession().getAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID))
                .isNull();
    }

    @Test
    void jsonLockedAccountReturnsTooManyRequests() throws Exception {
        MockHttpServletRequest request = jsonRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new LockedException("locked"));

        assertThat(response.getStatus()).isEqualTo(429);
        assertErrorCode(response, KycErrorCodes.TOO_MANY_REQUESTS);
    }

    @Test
    void htmlFailurePreservesRedirectBehavior() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("wrong password"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/auth/browser/login?error");
    }

    private MockHttpServletRequest jsonRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        return request;
    }

    private void assertErrorCode(MockHttpServletResponse response, String expected) throws Exception {
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(body.get("errorCode").asText()).isEqualTo(expected);
    }
}
