package org.letspeppol.kyc.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    void appServiceTokenCannotActAsCompanyUser() throws Exception {
        mockMvc.perform(get(SEARCH_PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(
                                List.of("letspeppol-api"), Instant.now().plusSeconds(300), "APP")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userTokenCannotManageCompanyUsers() throws Exception {
        String userToken = token(List.of("letspeppol-api"), Instant.now().plusSeconds(300), "USER");

        mockMvc.perform(get("/sapi/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("not_admin"));
        mockMvc.perform(post("/sapi/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"someone@example.com","name":"Someone","permissionMask":255}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/sapi/users/1/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permissionMask":255}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/sapi/users/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminClaimWithoutAnActiveAdminOwnershipCannotManageCompanyUsers() throws Exception {
        mockMvc.perform(get("/sapi/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(
                                List.of("letspeppol-api"), Instant.now().plusSeconds(300), "ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("not_admin"));
    }

    @Test
    void userTokenCannotRequestAnotherCompany() throws Exception {
        mockMvc.perform(post("/sapi/linked/request-company")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(
                                List.of("letspeppol-api"), Instant.now().plusSeconds(300), "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"ADMIN","peppolId":"0208:0123456789","email":"director@example.com"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("not_admin"));
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
        return token(audience, expiresAt, "ADMIN");
    }

    private String token(List<String> audience, Instant expiresAt, String accountType) {
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
                .claim("accountType", accountType)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
