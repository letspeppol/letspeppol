package org.letspeppol.app.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.service.DocumentService;
import org.letspeppol.app.service.SynchronizationGateService;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PostAuthProxyRequestTest {

    private static final String PEPPOL_ID = "0208:0123456789";

    private final SynchronizationGateService gate = mock(SynchronizationGateService.class);
    private final PostAuthProxyRequest filter = new PostAuthProxyRequest(mock(DocumentService.class), gate);

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void userWhoCannotReadInvoicesDoesNotTriggerSynchronization() throws Exception {
        request(jwt("USER", 128));

        verify(gate, never()).tryStart(anyString());
    }

    @Test
    void userWhoCanReadInvoicesTriggersSynchronization() throws Exception {
        request(jwt("USER", 1));

        verify(gate).tryStart(PEPPOL_ID);
    }

    @Test
    void adminTokenWithoutMaskStillTriggersSynchronization() throws Exception {
        request(jwt("ADMIN", null));

        verify(gate).tryStart(PEPPOL_ID);
    }

    private void request(Jwt jwt) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        filter.doFilter(new MockHttpServletRequest("GET", "/sapi/company"), new MockHttpServletResponse(), new MockFilterChain());
    }

    private static Jwt jwt(String accountType, Integer permissionMask) {
        Jwt.Builder jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("user")
                .claim(SecurityConfig.ACCOUNT_TYPE, accountType)
                .claim(SecurityConfig.PEPPOL_ID, PEPPOL_ID);
        if (permissionMask != null) {
            jwt.claim(SecurityConfig.PERMISSION_MASK, permissionMask);
        }
        return jwt.build();
    }
}
