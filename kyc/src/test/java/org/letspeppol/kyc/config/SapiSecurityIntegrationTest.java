package org.letspeppol.kyc.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SapiSecurityIntegrationTest {

    private static final String SEARCH_PATH = "/sapi/company/search?companyName=__sec3_no_match__";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void browserSessionCannotCallSapiGetOrMutation() throws Exception {
        MockHttpSession session = browserSession();

        mockMvc.perform(get(SEARCH_PATH).session(session))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/sapi/account/ownership").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validBearerTokenCallsCompanySearchEvenWhenBrowserCookieIsPresent() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .session(browserSession())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(
                                List.of("letspeppol-api"), Instant.now().plusSeconds(300))))
                .andExpect(status().isOk());
    }

    @Test
    void malformedExpiredAndWrongAudienceTokensAreRejected() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(SEARCH_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(
                                List.of("letspeppol-api"), Instant.now().minusSeconds(300))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(SEARCH_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(
                                List.of("some-other-api"), Instant.now().plusSeconds(300))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void browserLoginMutationStillRequiresCsrf() throws Exception {
        mockMvc.perform(post("/auth/browser/login")
                        .param("username", "person@example.com")
                        .param("password", "password"))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession browserSession() {
        AccountUserDetails principal = new AccountUserDetails(
                "person@example.com", "password", UUID.randomUUID(), false, 1L, false, true);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private String token(List<String> audience, Instant expiresAt) {
        Instant issuedAt = expiresAt.isAfter(Instant.now())
                ? Instant.now().minusSeconds(5)
                : expiresAt.minusSeconds(300);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost:8084")
                .subject("person@example.com")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .audience(audience)
                .claim("uid", UUID.randomUUID().toString())
                .claim("peppolId", "0208:0123456789")
                .claim("accountType", "ADMIN")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
