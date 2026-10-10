package org.letspeppol.kyc.controller;

import org.letspeppol.kyc.dto.AcceptInvitationRequest;
import org.letspeppol.kyc.dto.CompanyUserDto;
import org.letspeppol.kyc.dto.InvitationInfo;
import org.letspeppol.kyc.dto.InviteUserRequest;
import org.letspeppol.kyc.dto.UpdateUserPermissionsRequest;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.model.OwnershipStatus;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.repository.AccountRepository;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.OwnershipInvitationRepository;
import org.letspeppol.kyc.repository.OwnershipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestComponent
public class CompanyUserSteps {

    static final String LOGIN_FAILED = "login_failed";
    private static final String REDIRECT_URI = "http://localhost:9000/callback";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private RegistrationSteps registrationSteps;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OwnershipRepository ownershipRepository;
    @Autowired private OwnershipInvitationRepository invitationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private Environment environment;

    record ApiResult(HttpStatusCode status, JsonNode body) {
        String errorCode() {
            return body == null ? null : body.path("errorCode").asString();
        }
    }

    record Authorization(String accessToken, String error) {}

    void ensureAdmin(String peppolId, String companyName, String name, String email, String password) {
        if (companyRepository.findByPeppolId(peppolId).isEmpty()) {
            registrationSteps.prepareDatabase(peppolId, companyName);
        }
        Company company = companyRepository.findByPeppolId(peppolId).orElseThrow();
        Account account = accountRepository.findByEmail(email).orElseGet(() -> accountRepository.save(Account.builder()
                .name(name)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .verified(true)
                .verifiedOn(Instant.now())
                .externalId(UUID.randomUUID())
                .build()));
        if (ownershipRepository.findFirstByAccountIdAndCompanyIdAndType(account.getId(), company.getId(), AccountType.ADMIN).isEmpty()) {
            ownershipRepository.save(new Ownership(account, AccountType.ADMIN, company));
        }
    }

    ApiResult call(HttpMethod method, String url, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        ResponseEntity<String> response = restTemplate.exchange(url, method, new HttpEntity<>(body, headers), String.class);
        String content = response.getBody();
        return new ApiResult(response.getStatusCode(), content == null || content.isBlank() ? null : objectMapper.readTree(content));
    }

    List<CompanyUserDto> listUsers(String adminToken) {
        ApiResult result = call(HttpMethod.GET, "/sapi/users", adminToken, null);
        assertEquals(HttpStatus.OK, result.status());
        List<CompanyUserDto> users = new ArrayList<>();
        result.body().forEach(node -> users.add(objectMapper.treeToValue(node, CompanyUserDto.class)));
        return users;
    }

    ApiResult tryInvite(String adminToken, String email, String name, int permissionMask) {
        return call(HttpMethod.POST, "/sapi/users", adminToken, new InviteUserRequest(email, name, permissionMask));
    }

    CompanyUserDto invite(String adminToken, String email, String name, int permissionMask) {
        ApiResult result = tryInvite(adminToken, email, name, permissionMask);
        assertEquals(HttpStatus.OK, result.status(), String.valueOf(result.body()));
        CompanyUserDto user = objectMapper.treeToValue(result.body(), CompanyUserDto.class);
        assertEquals(OwnershipStatus.INVITED, user.status());
        assertEquals(AccountType.USER, user.type());
        assertEquals(email, user.email());
        assertNotNull(user.invitationExpiresOn());
        return user;
    }

    ApiResult tryUpdatePermissions(String adminToken, long id, int permissionMask) {
        return call(HttpMethod.PUT, "/sapi/users/" + id + "/permissions", adminToken, new UpdateUserPermissionsRequest(permissionMask));
    }

    CompanyUserDto updatePermissions(String adminToken, long id, int permissionMask) {
        return user(tryUpdatePermissions(adminToken, id, permissionMask));
    }

    ApiResult trySuspend(String adminToken, long id) {
        return call(HttpMethod.POST, "/sapi/users/" + id + "/suspend", adminToken, null);
    }

    ApiResult tryReactivate(String adminToken, long id) {
        return call(HttpMethod.POST, "/sapi/users/" + id + "/reactivate", adminToken, null);
    }

    ApiResult tryResend(String adminToken, long id) {
        return call(HttpMethod.POST, "/sapi/users/" + id + "/resend", adminToken, null);
    }

    ApiResult tryRemove(String adminToken, long id) {
        return call(HttpMethod.DELETE, "/sapi/users/" + id, adminToken, null);
    }

    CompanyUserDto user(ApiResult result) {
        assertEquals(HttpStatus.OK, result.status(), String.valueOf(result.body()));
        return objectMapper.treeToValue(result.body(), CompanyUserDto.class);
    }

    String invitationToken(long ownershipId) {
        return invitationRepository.findByOwnershipId(ownershipId).orElseThrow().getToken();
    }

    ApiResult tryVerifyInvitation(String token) {
        return call(HttpMethod.POST, "/api/invitation/verify?token=" + token, null, null);
    }

    InvitationInfo verifyInvitation(String token) {
        ApiResult result = tryVerifyInvitation(token);
        assertEquals(HttpStatus.OK, result.status(), String.valueOf(result.body()));
        return objectMapper.treeToValue(result.body(), InvitationInfo.class);
    }

    ApiResult tryAcceptInvitation(String token, String newPassword) {
        return call(HttpMethod.POST, "/api/invitation/accept", null, new AcceptInvitationRequest(token, newPassword));
    }

    CompanyUserDto inviteAndAccept(String adminToken, String email, String name, int permissionMask, String password) {
        CompanyUserDto invited = invite(adminToken, email, name, permissionMask);
        assertEquals(HttpStatus.NO_CONTENT, tryAcceptInvitation(invitationToken(invited.id()), password).status());
        return invited;
    }

    List<String> ownedPeppolIds(String token) {
        ApiResult result = call(HttpMethod.GET, "/sapi/account/ownerships", token, null);
        assertEquals(HttpStatus.OK, result.status());
        List<String> peppolIds = new ArrayList<>();
        result.body().forEach(node -> peppolIds.add(node.path("peppolId").asString()));
        return peppolIds;
    }

    HttpStatusCode ownershipsStatus(String token) {
        return call(HttpMethod.GET, "/sapi/account/ownerships", token, null).status();
    }

    List<String> revokedTokenIds() {
        ApiResult result = call(HttpMethod.GET, "/lapi/revocations", null, null);
        assertEquals(HttpStatus.OK, result.status());
        List<String> tokenIds = new ArrayList<>();
        result.body().forEach(node -> {
            assertNotNull(Instant.parse(node.path("expiresAt").asString()));
            tokenIds.add(node.path("jti").asString());
        });
        return tokenIds;
    }

    Authorization authorize(String email, String password, String peppolId, AccountType accountType) {
        try {
            HttpClient browser = HttpClient.newBuilder()
                    .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            String origin = "http://localhost:" + environment.getRequiredProperty("local.server.port");

            JsonNode session = objectMapper.readTree(browser.send(
                    HttpRequest.newBuilder(URI.create(origin + "/auth/browser/session"))
                            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()).body());
            String csrfToken = session.path("csrfToken").asString();

            HttpResponse<String> loginResponse = browser.send(
                    HttpRequest.newBuilder(URI.create(origin + "/auth/browser/login"))
                            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .header(session.path("csrfHeaderName").asString(), csrfToken)
                            .POST(HttpRequest.BodyPublishers.ofString(form(
                                    "username", email,
                                    "password", password,
                                    session.path("csrfParameterName").asString(), csrfToken)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (loginResponse.statusCode() != 200) {
                return new Authorization(null, LOGIN_FAILED);
            }

            String verifier = "company-user-test-pkce-verifier-0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZ";
            String challenge = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            HttpResponse<String> authorizeResponse = browser.send(
                    HttpRequest.newBuilder(URI.create(origin + "/auth/oauth2/authorize?" + form(
                                    "response_type", "code",
                                    "client_id", "letspeppol-ui",
                                    "redirect_uri", REDIRECT_URI,
                                    "code_challenge", challenge,
                                    "code_challenge_method", "S256",
                                    "state", "company-user-test-state",
                                    "scope", "openid",
                                    "peppol_id", peppolId,
                                    "account_type", accountType.name())))
                            .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(302, authorizeResponse.statusCode(), authorizeResponse.body());
            Map<String, String> redirect = query(URI.create(authorizeResponse.headers().firstValue(HttpHeaders.LOCATION).orElseThrow()));
            if (!redirect.containsKey("code")) {
                return new Authorization(null, redirect.get("error"));
            }

            HttpResponse<String> tokenResponse = browser.send(
                    HttpRequest.newBuilder(URI.create(origin + "/auth/oauth2/token"))
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                            .POST(HttpRequest.BodyPublishers.ofString(form(
                                    "grant_type", "authorization_code",
                                    "client_id", "letspeppol-ui",
                                    "redirect_uri", REDIRECT_URI,
                                    "code", redirect.get("code"),
                                    "code_verifier", verifier)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, tokenResponse.statusCode(), tokenResponse.body());
            return new Authorization(objectMapper.readTree(tokenResponse.body()).path("access_token").asString(), null);
        } catch (Exception e) {
            throw new AssertionError("OAuth2 Authorization Code + PKCE flow failed", e);
        }
    }

    private static String form(String... namesAndValues) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            if (!body.isEmpty()) body.append('&');
            body.append(URLEncoder.encode(namesAndValues[i], StandardCharsets.UTF_8));
            body.append('=');
            body.append(URLEncoder.encode(namesAndValues[i + 1], StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> values = new HashMap<>();
        if (uri.getRawQuery() == null) {
            return values;
        }
        for (String pair : uri.getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            values.put(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8));
        }
        return values;
    }
}
