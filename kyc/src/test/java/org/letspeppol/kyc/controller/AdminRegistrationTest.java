package org.letspeppol.kyc.controller;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.letspeppol.kyc.dto.*;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.EmailVerification;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.repository.*;
import org.letspeppol.kyc.service.JwtService;
import org.letspeppol.kyc.service.jwt.JwtInfo;
import org.letspeppol.kyc.service.signing.CertificateUtil;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminRegistrationTest {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private DirectorRepository directorRepository;
    @Autowired private OwnershipRepository ownershipRepository;
    @Autowired private JwtService jwtService;
    @MockitoBean private JavaMailSender javaMailSender;
    static MockWebServer mockWebServer;

    String adminCompany = "Test Company";
    String adminPeppolId = "0208:1234567890";
    String adminEmail = "test@company.com";
    String adminPassword = "dummy-password";
    String adminToken = null;

    String partnerCompany = "Test Partner";
    String partnerPeppolId = "0208:0987654321";
    String partnerEmail = "test@partner.com";
    String partnerPassword = "dummy-password";
    String partnerToken = null;

    /// Bob is a company that has been invited by Partner to register and handles this at home
    String bobCompany = "Bob Company";
    String bobPeppolId = "0208:1111111111";
    String bobEmail = "bob@company.com";
    String bobPassword = "bob-password";
    String bobToken = null;

    /// Charlie is a company that has been invited by Partner to register and tries at home, but needs the partner to sign contract
    String charlieCompany = "Charlie Company";
    String charliePeppolId = "0208:2222222222";
    String charlieEmail = "charlie@company.com";
    String charliePassword = "charlie-password";
    String charlieToken = null;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeAll
    static void startMockServer() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("proxy.api.url", () -> "http://localhost:" + mockWebServer.getPort());
    }

    @AfterAll
    static void shutdownMockServer() throws Exception {
        if (mockWebServer != null) mockWebServer.shutdown();
    }

    @BeforeEach
    void setupMailSender() {
        Mockito.when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new java.util.Properties())));
        // Enqueue a default response for /sapi/registry POST
        mockWebServer.enqueue(new MockResponse()
            .setBody("""
                    {"peppolActive":true}
                    """)
            .addHeader("Content-Type", "application/json"));
    }

    void prepareDatabase(String peppolId, String companyName) {
        // Insert test company in DB
        Company company = new Company(peppolId, "BE1234567890", companyName);
        company.setAddress("TestCity", "1234", "TestStreet");
        companyRepository.save(company);
        // Insert a director for the company
        Director director = new Director("Test Director", company);
        director.setRegistered(true);
        directorRepository.save(director);
    }

    String login(String email, String password, AccountType accountType, String peppolId) {
        // Build Basic header
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(email, password, StandardCharsets.UTF_8);

        // 1. POST /api/jwt/auth
        String url = baseUrl() + "/api/jwt/auth";
        AuthRequest authRequest = new AuthRequest(accountType, peppolId);
        HttpEntity<AuthRequest> request = new HttpEntity<>(authRequest, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        JwtInfo jwtInfo = jwtService.validateAndGetInfo("Bearer " + response.getBody());
        assertEquals(peppolId, jwtInfo.peppolId());
        assertEquals(accountType, jwtInfo.accountType());
        return response.getBody();
    }

    @Test
    @Order(1)
    void registrationNewAdmin() {
        prepareDatabase(adminPeppolId, adminCompany);

        // 1. GET /api/register/company/{peppolId}
        String url = baseUrl() + "/api/register/company/" + adminPeppolId;
        CompanyResponse companyResponse = restTemplate.getForObject(url, CompanyResponse.class);
        assertNotNull(companyResponse);
        assertEquals(adminPeppolId, companyResponse.peppolId());
        assertFalse(companyResponse.hasAdmin());

        // 2. POST /api/register/confirm-company
        url = baseUrl() + "/api/register/confirm-company";
        ConfirmCompanyRequest confirmRequest = new ConfirmCompanyRequest(AccountType.ADMIN, adminPeppolId, adminEmail, "TestCity", "1234", "TestStreet");
        SimpleMessage confirmResponse = restTemplate.postForObject(url, confirmRequest, SimpleMessage.class);
        assertNotNull(confirmResponse);
        assertTrue(confirmResponse.message().contains("Activation email sent"));

        // Simulate activation token
        EmailVerification verification = emailVerificationRepository.findAll().stream()
                .filter(v -> !v.isVerified() && adminEmail.equals(v.getEmail()) && adminPeppolId.equals(v.getPeppolId()))
                .findFirst().orElseThrow();
        String token = verification.getToken();

        // 3. POST /api/register/verify
        url = baseUrl() + "/api/register/verify?token=" + token;
        TokenVerificationResponse verifyResponse = restTemplate.postForObject(url, null, TokenVerificationResponse.class);
        assertNotNull(verifyResponse);
        assertEquals(adminEmail, verifyResponse.email());
        assertEquals(adminPeppolId, verifyResponse.company().peppolId());
        assertNull(verifyResponse.requester());

        // Mock certificate chain for signing using mockStatic
        try (org.mockito.MockedStatic<CertificateUtil> mocked = Mockito.mockStatic(CertificateUtil.class)) {
            mocked.when(() -> CertificateUtil.getCertificateChain(Mockito.anyString()))
                    .thenReturn(new X509Certificate[] { Mockito.mock(X509Certificate.class) });

            // 4. POST /api/identity/sign/prepare
            Long directorId = verifyResponse.company().directors().get(0).id();
            // Read a valid base64-encoded certificate from test resources
            String certificate;
            try {
                certificate = Files.readString(Paths.get("src/test/resources/test-certificate-base64.txt")).replaceAll("\\s+", "");
            } catch (Exception e) {
                throw new RuntimeException("Failed to read test certificate", e);
            }
            var signatureAlgorithm = new SignatureAlgorithm("SHA256", "PKCS1", "RSA");
            var prepareRequest = new PrepareSigningRequest(
                    token,
                    directorId,
                    certificate,
                    java.util.List.of(signatureAlgorithm),
                    "en"
            );
            String prepareUrl = baseUrl() + "/api/identity/sign/prepare";
            PrepareSigningResponse prepareResponse = restTemplate.postForObject(prepareUrl, prepareRequest, PrepareSigningResponse.class);
            System.out.println("prepareResponse: " + prepareResponse);
            assertNotNull(prepareResponse);
            assertNotNull(prepareResponse.hashToSign());
            assertNotNull(prepareResponse.hashToFinalize());
            assertEquals("SHA-256", prepareResponse.hashFunction());
            assertTrue(prepareResponse.allowedToSign());

            // 5. GET /api/identity/contract/{directorId}?token=...
            String contractUrl = baseUrl() + "/api/identity/contract/" + directorId + "?token=" + token;
            ResponseEntity<byte[]> contractResponse = restTemplate.getForEntity(contractUrl, byte[].class);
            assertEquals(200, contractResponse.getStatusCode().value());
            assertNotNull(contractResponse.getBody());
            assertTrue(contractResponse.getBody().length > 0);
            assertNotNull(contractResponse.getHeaders().getContentType());
            assertEquals("application/pdf", contractResponse.getHeaders().getContentType().toString());

            // 6. POST /api/identity/sign/finalize
            String signature = "Pip9ksT1yiqpP6AHEshmzl8ND+oPDF6PYjizuiKbHrwv23LqrqDRwJq/b2mbsAGScxYGdzk+sHGUsKcXr9YIiFXA9AM94GptSxwdjxulc2CA4qmd4KX9TdTjQGkCCj7qE0EMYULEtfPTMNPC61CYSic2fap4nicnBKFDGptHccblQICcNDHJ5hAN9fbFIw2OXWynomFgSBohVr0bDKcZQcUX9Chg0RUZ/4i95HdwXN306k343tLKB/doY+TO70akA3mzjBya+aGaE9QPE7zRvLF4IriRBy6QxzEPSsCHYHrP3w3mPLg2+xWX1Aw5M+m8K6XMuFC5O14Det8FZP4HWQ==";
            var finalizeRequest = new FinalizeSigningRequest(
                    token,
                    directorId,
                    certificate,
                    signature,
                    signatureAlgorithm,
                    prepareResponse.hashToSign(),
                    prepareResponse.hashToFinalize(),
                    adminPassword
            );
            String finalizeUrl = baseUrl() + "/api/identity/sign/finalize";
            ResponseEntity<byte[]> finalizeResponse = restTemplate.postForEntity(finalizeUrl, finalizeRequest, byte[].class);
            assertEquals(200, finalizeResponse.getStatusCode().value());
            assertNotNull(finalizeResponse.getBody());
            assertTrue(finalizeResponse.getBody().length > 0);
            assertNotNull(finalizeResponse.getHeaders().getContentType());
            assertEquals("application/pdf", finalizeResponse.getHeaders().getContentType().toString());
            assertNotNull(finalizeResponse.getHeaders().get("Registration-Status"));
        }
        assertTrue(ownershipRepository.existsByTypeAndCompanyPeppolId(AccountType.ADMIN, adminPeppolId));
    }

    @Test
    @Order(2)
    void registrationActiveAdmin() {
        if (!accountRepository.existsByEmail(adminEmail)) {
            registrationNewAdmin();
        }

        // 1. GET /api/register/company/{peppolId}
        String url = baseUrl() + "/api/register/company/" + adminPeppolId;
        CompanyResponse companyResponse = restTemplate.getForObject(url, CompanyResponse.class);
        assertNotNull(companyResponse);
        assertEquals(adminPeppolId, companyResponse.peppolId());
        assertTrue(companyResponse.hasAdmin());
    }

    @Test
    @Order(3)
    void registrationNewPartner() {
        prepareDatabase(partnerPeppolId, partnerCompany);

        // 1. GET /api/register/company/{peppolId}
        String url = baseUrl() + "/api/register/company/" + partnerPeppolId;
        CompanyResponse companyResponse = restTemplate.getForObject(url, CompanyResponse.class);
        assertNotNull(companyResponse);
        assertEquals(partnerPeppolId, companyResponse.peppolId());
        assertFalse(companyResponse.hasAdmin());

        // 2. POST /api/register/confirm-company
        url = baseUrl() + "/api/register/confirm-company";
        ConfirmCompanyRequest confirmRequest = new ConfirmCompanyRequest(AccountType.PARTNER, partnerPeppolId, partnerEmail, "TestCity", "1234", "TestStreet");
        SimpleMessage confirmResponse = restTemplate.postForObject(url, confirmRequest, SimpleMessage.class);
        assertNotNull(confirmResponse);
        assertTrue(confirmResponse.message().contains("Activation email sent"));

        // Simulate activation token
        EmailVerification verification = emailVerificationRepository.findAll().stream()
                .filter(v -> !v.isVerified() && partnerEmail.equals(v.getEmail()) && partnerPeppolId.equals(v.getPeppolId()))
                .findFirst().orElseThrow();
        String token = verification.getToken();

        // 3. POST /api/register/verify
        url = baseUrl() + "/api/register/verify?token=" + token;
        TokenVerificationResponse verifyResponse = restTemplate.postForObject(url, null, TokenVerificationResponse.class);
        assertNotNull(verifyResponse);
        assertEquals(partnerEmail, verifyResponse.email());
        assertEquals(partnerPeppolId, verifyResponse.company().peppolId());
        assertNull(verifyResponse.requester());

        // Mock certificate chain for signing using mockStatic
        try (org.mockito.MockedStatic<CertificateUtil> mocked = Mockito.mockStatic(CertificateUtil.class)) {
            mocked.when(() -> CertificateUtil.getCertificateChain(Mockito.anyString()))
                    .thenReturn(new X509Certificate[] { Mockito.mock(X509Certificate.class) });

            // 4. POST /api/identity/sign/prepare
            Long directorId = verifyResponse.company().directors().get(0).id();
            // Read a valid base64-encoded certificate from test resources
            String certificate;
            try {
                certificate = Files.readString(Paths.get("src/test/resources/test-certificate-base64.txt")).replaceAll("\\s+", "");
            } catch (Exception e) {
                throw new RuntimeException("Failed to read test certificate", e);
            }
            var signatureAlgorithm = new SignatureAlgorithm("SHA256", "PKCS1", "RSA");
            var prepareRequest = new PrepareSigningRequest(
                    token,
                    directorId,
                    certificate,
                    java.util.List.of(signatureAlgorithm),
                    "en"
            );
            String prepareUrl = baseUrl() + "/api/identity/sign/prepare";
            PrepareSigningResponse prepareResponse = restTemplate.postForObject(prepareUrl, prepareRequest, PrepareSigningResponse.class);
            System.out.println("prepareResponse: " + prepareResponse);
            assertNotNull(prepareResponse);
            assertNotNull(prepareResponse.hashToSign());
            assertNotNull(prepareResponse.hashToFinalize());
            assertEquals("SHA-256", prepareResponse.hashFunction());
            assertTrue(prepareResponse.allowedToSign());

            // 5. GET /api/identity/contract/{directorId}?token=...
            String contractUrl = baseUrl() + "/api/identity/contract/" + directorId + "?token=" + token;
            ResponseEntity<byte[]> contractResponse = restTemplate.getForEntity(contractUrl, byte[].class);
            assertEquals(200, contractResponse.getStatusCode().value());
            assertNotNull(contractResponse.getBody());
            assertTrue(contractResponse.getBody().length > 0);
            assertNotNull(contractResponse.getHeaders().getContentType());
            assertEquals("application/pdf", contractResponse.getHeaders().getContentType().toString());

            // 6. POST /api/identity/sign/finalize
            String signature = "Pip9ksT1yiqpP6AHEshmzl8ND+oPDF6PYjizuiKbHrwv23LqrqDRwJq/b2mbsAGScxYGdzk+sHGUsKcXr9YIiFXA9AM94GptSxwdjxulc2CA4qmd4KX9TdTjQGkCCj7qE0EMYULEtfPTMNPC61CYSic2fap4nicnBKFDGptHccblQICcNDHJ5hAN9fbFIw2OXWynomFgSBohVr0bDKcZQcUX9Chg0RUZ/4i95HdwXN306k343tLKB/doY+TO70akA3mzjBya+aGaE9QPE7zRvLF4IriRBy6QxzEPSsCHYHrP3w3mPLg2+xWX1Aw5M+m8K6XMuFC5O14Det8FZP4HWQ==";
            var finalizeRequest = new FinalizeSigningRequest(
                    token,
                    directorId,
                    certificate,
                    signature,
                    signatureAlgorithm,
                    prepareResponse.hashToSign(),
                    prepareResponse.hashToFinalize(),
                    partnerPassword
            );
            String finalizeUrl = baseUrl() + "/api/identity/sign/finalize";
            ResponseEntity<byte[]> finalizeResponse = restTemplate.postForEntity(finalizeUrl, finalizeRequest, byte[].class);
            assertEquals(200, finalizeResponse.getStatusCode().value());
            assertNotNull(finalizeResponse.getBody());
            assertTrue(finalizeResponse.getBody().length > 0);
            assertNotNull(finalizeResponse.getHeaders().getContentType());
            assertEquals("application/pdf", finalizeResponse.getHeaders().getContentType().toString());
            assertNotNull(finalizeResponse.getHeaders().get("Registration-Status"));
        }
        assertTrue(ownershipRepository.existsByTypeAndCompanyPeppolId(AccountType.ADMIN, partnerPeppolId));
        assertTrue(ownershipRepository.existsByTypeAndCompanyPeppolId(AccountType.PARTNER, partnerPeppolId));
    }

    @Test
    @Order(4)
    void registrationActivePartner() {
        if (!accountRepository.existsByEmail(partnerEmail)) {
            registrationNewPartner();
        }

        // 1. GET /api/register/company/{peppolId}
        String url = baseUrl() + "/api/register/company/" + partnerPeppolId;
        CompanyResponse companyResponse = restTemplate.getForObject(url, CompanyResponse.class);
        assertNotNull(companyResponse);
        assertEquals(partnerPeppolId, companyResponse.peppolId());
        assertTrue(companyResponse.hasAdmin());
    }

    @Test
    @Order(5)
    void loginAdmin() {
        if (!accountRepository.existsByEmail(adminEmail)) {
            registrationNewAdmin();
        }

        // Build Basic header
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(adminEmail, adminPassword, StandardCharsets.UTF_8);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        // 1. POST /api/jwt/auth
        String url = baseUrl() + "/api/jwt/auth";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        JwtInfo jwtInfo = jwtService.validateAndGetInfo("Bearer " + response.getBody());
        assertEquals(adminPeppolId, jwtInfo.peppolId());
        assertEquals(AccountType.ADMIN, jwtInfo.accountType());
        adminToken = response.getBody();
    }

    @Test
    @Order(5)
    void loginAdminAsAdmin() {
        if (!accountRepository.existsByEmail(adminEmail)) {
            registrationNewAdmin();
        }
        adminToken = login(adminEmail, adminPassword, AccountType.ADMIN, adminPeppolId);
    }

    @Test
    @Order(5)
    void loginPartnerAsPartner() {
        if (!accountRepository.existsByEmail(partnerEmail)) {
            registrationNewPartner();
        }
        partnerToken = login(partnerEmail, partnerPassword, AccountType.PARTNER, partnerPeppolId);
    }

    @Test
    @Order(5)
    void loginPartnerAsAdmin() {
        if (!accountRepository.existsByEmail(partnerEmail)) {
            registrationNewPartner();
        }
        partnerToken = login(partnerEmail, partnerPassword, AccountType.ADMIN, partnerPeppolId);
    }

    @Test
    @Order(6)
    void swapPartnerToPartner() {
        if (partnerToken == null) {
            loginPartnerAsAdmin();
        }

        // Build JWT header
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(partnerToken);

        // 1. POST /sapi/jwt/swap
        String url = baseUrl() + "/sapi/jwt/swap";
        AuthRequest authRequest = new AuthRequest(AccountType.PARTNER, partnerPeppolId);
        HttpEntity<AuthRequest> request = new HttpEntity<>(authRequest, headers);
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        JwtInfo jwtInfo = jwtService.validateAndGetInfo("Bearer " + response.getBody());
        assertEquals(partnerPeppolId, jwtInfo.peppolId());
        assertEquals(AccountType.PARTNER, jwtInfo.accountType());
        partnerToken = response.getBody();
    }

    @Test
    @Order(7)
    void registrationActiveAdminViaPartnerAndVerifyByEmail() {
        if (!accountRepository.existsByEmail(adminEmail)) {
            registrationNewAdmin();
        }
        if (partnerToken == null) {
            loginPartnerAsPartner();
        }

        // Build JWT header
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(partnerToken);

        // 1. GET /api/register/company/{peppolId}
        String url = baseUrl() + "/api/register/company/" + adminPeppolId;
        CompanyResponse companyResponse = restTemplate.getForObject(url, CompanyResponse.class);
        assertNotNull(companyResponse);
        assertEquals(adminPeppolId, companyResponse.peppolId());
        assertTrue(companyResponse.hasAdmin());

        // 2. POST /sapi/linked/request-company
        url = baseUrl() + "/sapi/linked/request-company";
        ConfirmCompanyRequest confirmRequest = new ConfirmCompanyRequest(AccountType.ADMIN, adminPeppolId, adminEmail, "TestCity", "1234", "TestStreet"); //TODO : no email ? Why address ?
        HttpEntity<ConfirmCompanyRequest> request = new HttpEntity<>(confirmRequest, headers);
        ResponseEntity<SimpleMessage> response = restTemplate.exchange(url, HttpMethod.POST, request, SimpleMessage.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        SimpleMessage confirmResponse = response.getBody();
        assertTrue(confirmResponse.message().contains("Request email sent"));

        // Simulate activation token
        EmailVerification verification = emailVerificationRepository.findAll().stream()
                .filter(v -> !v.isVerified() && adminEmail.equals(v.getEmail())) //TODO : should we be able to do && (v.getRequester() != null && v.getRequester().getCompany() != null && v.getRequester().getAccount() != null && partnerPeppolId.equals(v.getRequester().getCompany().getPeppolId()) && partnerEmail.equals(v.getRequester().getAccount().getEmail())))
                .findFirst().orElseThrow();
        String token = verification.getToken();

        // 3. POST /api/register/verify
        url = baseUrl() + "/api/register/verify?token=" + token;
        TokenVerificationResponse verifyResponse = restTemplate.postForObject(url, null, TokenVerificationResponse.class);
        assertNotNull(verifyResponse);
        assertEquals(adminEmail, verifyResponse.email());
        assertNotNull(verifyResponse.company());
        assertTrue(verifyResponse.company().hasAdmin());
        assertEquals(adminPeppolId, verifyResponse.company().peppolId());
        assertEquals(partnerEmail, verifyResponse.requester().email());
        assertEquals(partnerCompany, verifyResponse.requester().company());

        // 4. login as Admin to approve
        loginAdminAsAdmin();
        //NOTE : call App to store verifyResponse.requester() as partner (and use the adminToken)

        // 5. POST /sapi/linked/approve
        HttpHeaders adminJwtHeaders = new HttpHeaders();
        adminJwtHeaders.setBearerAuth(adminToken);
        url = baseUrl() + "/sapi/linked/approve?token=" + token;
        HttpEntity<Void> requestApprove = new HttpEntity<>(null, adminJwtHeaders);
        ResponseEntity<Void> responseApprove = restTemplate.exchange(url, HttpMethod.POST, requestApprove, Void.class);
        assertEquals(HttpStatus.OK, responseApprove.getStatusCode());
        assertNull(responseApprove.getBody());

        /// ---- ADDITIONAL TEST ----
        // +. POST /api/register/verify --> Should be invalid token
        url = baseUrl() + "/api/register/verify?token=" + token;
        ResponseEntity<String> failedResponse = restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, failedResponse.getStatusCode());
        String body = failedResponse.getBody(); //Maybe not needed to verify ?
        assertNotNull(body);
        assertTrue(body.contains("\"errorCode\":\"token_already_verified\""));
    }

    @Test
    @Order(8)
    void registrationNewAdminViaPartnerAndVerifyByEmailBeforeSigning() {
        prepareDatabase(bobPeppolId, bobCompany);

        if (partnerToken == null) {
            loginPartnerAsPartner();
        }

        // Build JWT header
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(partnerToken);

        // 1. GET /api/register/company/{peppolId}
        String url = baseUrl() + "/api/register/company/" + bobPeppolId;
        CompanyResponse companyResponse = restTemplate.getForObject(url, CompanyResponse.class);
        assertNotNull(companyResponse);
        assertEquals(bobPeppolId, companyResponse.peppolId());
        assertFalse(companyResponse.hasAdmin());

        // 2. POST /sapi/linked/request-company
        url = baseUrl() + "/sapi/linked/request-company";
        ConfirmCompanyRequest confirmRequest = new ConfirmCompanyRequest(AccountType.ADMIN, bobPeppolId, bobEmail, "TestCity", "1234", "TestStreet");
        HttpEntity<ConfirmCompanyRequest> request = new HttpEntity<>(confirmRequest, headers);
        ResponseEntity<SimpleMessage> response = restTemplate.exchange(url, HttpMethod.POST, request, SimpleMessage.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        SimpleMessage confirmResponse = response.getBody();
        assertTrue(confirmResponse.message().contains("Request email sent"));

        // Simulate activation token
        EmailVerification verification = emailVerificationRepository.findAll().stream()
                .filter(v -> !v.isVerified() && bobEmail.equals(v.getEmail())) //TODO : should we be able to do && (v.getRequester() != null && v.getRequester().getCompany() != null && v.getRequester().getAccount() != null && partnerPeppolId.equals(v.getRequester().getCompany().getPeppolId()) && partnerEmail.equals(v.getRequester().getAccount().getEmail())))
                .findFirst().orElseThrow();
        String token = verification.getToken();

        // 3. POST /api/register/verify
        url = baseUrl() + "/api/register/verify?token=" + token;
        TokenVerificationResponse verifyResponse = restTemplate.postForObject(url, null, TokenVerificationResponse.class);
        assertNotNull(verifyResponse);
        assertEquals(bobEmail, verifyResponse.email());
        assertNotNull(verifyResponse.company());
        assertFalse(verifyResponse.company().hasAdmin());
        assertEquals(bobPeppolId, verifyResponse.company().peppolId());
        assertEquals(partnerEmail, verifyResponse.requester().email());
        assertEquals(partnerCompany, verifyResponse.requester().company());

        // Mock certificate chain for signing using mockStatic
        try (org.mockito.MockedStatic<CertificateUtil> mocked = Mockito.mockStatic(CertificateUtil.class)) {
            mocked.when(() -> CertificateUtil.getCertificateChain(Mockito.anyString()))
                    .thenReturn(new X509Certificate[] { Mockito.mock(X509Certificate.class) });

            // 4. POST /api/identity/sign/prepare
            Long directorId = verifyResponse.company().directors().get(0).id();
            // Read a valid base64-encoded certificate from test resources
            String certificate;
            try {
                certificate = Files.readString(Paths.get("src/test/resources/test-certificate-base64.txt")).replaceAll("\\s+", "");
            } catch (Exception e) {
                throw new RuntimeException("Failed to read test certificate", e);
            }
            var signatureAlgorithm = new SignatureAlgorithm("SHA256", "PKCS1", "RSA");
            var prepareRequest = new PrepareSigningRequest(
                    token,
                    directorId,
                    certificate,
                    java.util.List.of(signatureAlgorithm),
                    "en"
            );
            String prepareUrl = baseUrl() + "/api/identity/sign/prepare";
            PrepareSigningResponse prepareResponse = restTemplate.postForObject(prepareUrl, prepareRequest, PrepareSigningResponse.class);
            System.out.println("prepareResponse: " + prepareResponse);
            assertNotNull(prepareResponse);
            assertNotNull(prepareResponse.hashToSign());
            assertNotNull(prepareResponse.hashToFinalize());
            assertEquals("SHA-256", prepareResponse.hashFunction());
            assertTrue(prepareResponse.allowedToSign());

            // 5. GET /api/identity/contract/{directorId}?token=...
            String contractUrl = baseUrl() + "/api/identity/contract/" + directorId + "?token=" + token;
            ResponseEntity<byte[]> contractResponse = restTemplate.getForEntity(contractUrl, byte[].class);
            assertEquals(200, contractResponse.getStatusCode().value());
            assertNotNull(contractResponse.getBody());
            assertTrue(contractResponse.getBody().length > 0);
            assertNotNull(contractResponse.getHeaders().getContentType());
            assertEquals("application/pdf", contractResponse.getHeaders().getContentType().toString());

            // 6. POST /api/identity/sign/finalize
            String signature = "Pip9ksT1yiqpP6AHEshmzl8ND+oPDF6PYjizuiKbHrwv23LqrqDRwJq/b2mbsAGScxYGdzk+sHGUsKcXr9YIiFXA9AM94GptSxwdjxulc2CA4qmd4KX9TdTjQGkCCj7qE0EMYULEtfPTMNPC61CYSic2fap4nicnBKFDGptHccblQICcNDHJ5hAN9fbFIw2OXWynomFgSBohVr0bDKcZQcUX9Chg0RUZ/4i95HdwXN306k343tLKB/doY+TO70akA3mzjBya+aGaE9QPE7zRvLF4IriRBy6QxzEPSsCHYHrP3w3mPLg2+xWX1Aw5M+m8K6XMuFC5O14Det8FZP4HWQ==";
            var finalizeRequest = new FinalizeSigningRequest(
                    token,
                    directorId,
                    certificate,
                    signature,
                    signatureAlgorithm,
                    prepareResponse.hashToSign(),
                    prepareResponse.hashToFinalize(),
                    bobPassword
            );
            String finalizeUrl = baseUrl() + "/api/identity/sign/finalize";
            ResponseEntity<byte[]> finalizeResponse = restTemplate.postForEntity(finalizeUrl, finalizeRequest, byte[].class);
            assertEquals(200, finalizeResponse.getStatusCode().value());
            assertNotNull(finalizeResponse.getBody());
            assertTrue(finalizeResponse.getBody().length > 0);
            assertNotNull(finalizeResponse.getHeaders().getContentType());
            assertEquals("application/pdf", finalizeResponse.getHeaders().getContentType().toString());
            assertNotNull(finalizeResponse.getHeaders().get("Registration-Status"));
        }
        assertTrue(ownershipRepository.existsByTypeAndCompanyPeppolId(AccountType.ADMIN, bobPeppolId));

        // 4. login as Admin to approve
        bobToken = login(bobEmail, bobPassword, AccountType.ADMIN, bobPeppolId);
        //NOTE : call App to store verifyResponse.requester() as partner (and use the adminToken)

        // 5. POST /sapi/linked/approve
        HttpHeaders adminJwtHeaders = new HttpHeaders();
        adminJwtHeaders.setBearerAuth(bobToken);
        url = baseUrl() + "/sapi/linked/approve?token=" + token;
        HttpEntity<Void> requestApprove = new HttpEntity<>(null, adminJwtHeaders);
        ResponseEntity<Void> responseApprove = restTemplate.exchange(url, HttpMethod.POST, requestApprove, Void.class);
        assertEquals(HttpStatus.OK, responseApprove.getStatusCode());
        assertNull(responseApprove.getBody());

        /// ---- ADDITIONAL TEST ----
        // +. POST /api/register/verify --> Should be invalid token
        url = baseUrl() + "/api/register/verify?token=" + token;
        ResponseEntity<String> failedResponse = restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, failedResponse.getStatusCode());
        String body = failedResponse.getBody(); //Maybe not needed to verify ?
        assertNotNull(body);
        assertTrue(body.contains("\"errorCode\":\"token_already_verified\""));
    }

    @Test
    @Order(9)
    void registrationNewAdminViaPartnerAndSignBeforeEmailVerification() {
        prepareDatabase(charliePeppolId, charlieCompany);

        if (partnerToken == null) {
            loginPartnerAsPartner();
        }

        // Build JWT header
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(partnerToken);

        // 1. GET /api/register/company/{peppolId}
        String url = baseUrl() + "/api/register/company/" + charliePeppolId;
        CompanyResponse companyResponse = restTemplate.getForObject(url, CompanyResponse.class);
        assertNotNull(companyResponse);
        assertEquals(charliePeppolId, companyResponse.peppolId());
        assertFalse(companyResponse.hasAdmin());

        // 2. POST /sapi/linked/request-company
        url = baseUrl() + "/sapi/linked/request-company";
        ConfirmCompanyRequest confirmRequest = new ConfirmCompanyRequest(AccountType.ADMIN, charliePeppolId, charlieEmail, "TestCity", "1234", "TestStreet");
        HttpEntity<ConfirmCompanyRequest> request = new HttpEntity<>(confirmRequest, headers);
        ResponseEntity<SimpleMessage> response = restTemplate.exchange(url, HttpMethod.POST, request, SimpleMessage.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        SimpleMessage confirmResponse = response.getBody();
        assertTrue(confirmResponse.message().contains("Request email sent"));

        // Mock certificate chain for signing using mockStatic
        try (org.mockito.MockedStatic<CertificateUtil> mocked = Mockito.mockStatic(CertificateUtil.class)) {
            mocked.when(() -> CertificateUtil.getCertificateChain(Mockito.anyString()))
                    .thenReturn(new X509Certificate[] { Mockito.mock(X509Certificate.class) });

            // 4. POST /api/identity/sign/prepare
            Long directorId = companyResponse.directors().get(0).id();
            // Read a valid base64-encoded certificate from test resources
            String certificate;
            try {
                certificate = Files.readString(Paths.get("src/test/resources/test-certificate-base64.txt")).replaceAll("\\s+", "");
            } catch (Exception e) {
                throw new RuntimeException("Failed to read test certificate", e);
            }
            var signatureAlgorithm = new SignatureAlgorithm("SHA256", "PKCS1", "RSA");
            var prepareRequest = new PrepareSigningRequest(
                    null, //TODO : check this !!!
                    directorId,
                    certificate,
                    java.util.List.of(signatureAlgorithm),
                    "en"
            );
            String prepareUrl = baseUrl() + "/api/identity/sign/prepare";
            PrepareSigningResponse prepareResponse = restTemplate.postForObject(prepareUrl, prepareRequest, PrepareSigningResponse.class);
            System.out.println("prepareResponse: " + prepareResponse);
            assertNotNull(prepareResponse);
            assertNotNull(prepareResponse.hashToSign());
            assertNotNull(prepareResponse.hashToFinalize());
            assertEquals("SHA-256", prepareResponse.hashFunction());
            assertTrue(prepareResponse.allowedToSign());

            // 5. GET /api/identity/contract/{directorId}?token=...
            String contractUrl = baseUrl() + "/api/identity/contract/" + directorId + "?token=" + null; //TODO : check this !!!
            ResponseEntity<byte[]> contractResponse = restTemplate.getForEntity(contractUrl, byte[].class);
            assertEquals(200, contractResponse.getStatusCode().value());
            assertNotNull(contractResponse.getBody());
            assertTrue(contractResponse.getBody().length > 0);
            assertNotNull(contractResponse.getHeaders().getContentType());
            assertEquals("application/pdf", contractResponse.getHeaders().getContentType().toString());

            // 6. POST /api/identity/sign/finalize
            String signature = "Pip9ksT1yiqpP6AHEshmzl8ND+oPDF6PYjizuiKbHrwv23LqrqDRwJq/b2mbsAGScxYGdzk+sHGUsKcXr9YIiFXA9AM94GptSxwdjxulc2CA4qmd4KX9TdTjQGkCCj7qE0EMYULEtfPTMNPC61CYSic2fap4nicnBKFDGptHccblQICcNDHJ5hAN9fbFIw2OXWynomFgSBohVr0bDKcZQcUX9Chg0RUZ/4i95HdwXN306k343tLKB/doY+TO70akA3mzjBya+aGaE9QPE7zRvLF4IriRBy6QxzEPSsCHYHrP3w3mPLg2+xWX1Aw5M+m8K6XMuFC5O14Det8FZP4HWQ==";
            var finalizeRequest = new FinalizeSigningRequest(
                    null, //TODO : check this !!!
                    directorId,
                    certificate,
                    signature,
                    signatureAlgorithm,
                    prepareResponse.hashToSign(),
                    prepareResponse.hashToFinalize(),
                    bobPassword
            );
            String finalizeUrl = baseUrl() + "/api/identity/sign/finalize";
            ResponseEntity<byte[]> finalizeResponse = restTemplate.postForEntity(finalizeUrl, finalizeRequest, byte[].class);
            assertEquals(200, finalizeResponse.getStatusCode().value());
            assertNotNull(finalizeResponse.getBody());
            assertTrue(finalizeResponse.getBody().length > 0);
            assertNotNull(finalizeResponse.getHeaders().getContentType());
            assertEquals("application/pdf", finalizeResponse.getHeaders().getContentType().toString());
            assertNotNull(finalizeResponse.getHeaders().get("Registration-Status"));
        }
        assertTrue(ownershipRepository.existsByTypeAndCompanyPeppolId(AccountType.ADMIN, charliePeppolId));

        // Simulate activation token
        EmailVerification verification = emailVerificationRepository.findAll().stream()
                .filter(v -> !v.isVerified() && charlieEmail.equals(v.getEmail())) //TODO : should we be able to do && (v.getRequester() != null && v.getRequester().getCompany() != null && v.getRequester().getAccount() != null && partnerPeppolId.equals(v.getRequester().getCompany().getPeppolId()) && partnerEmail.equals(v.getRequester().getAccount().getEmail())))
                .findFirst().orElseThrow();
        String token = verification.getToken();

        // 3. POST /api/register/verify
        url = baseUrl() + "/api/register/verify?token=" + token;
        TokenVerificationResponse verifyResponse = restTemplate.postForObject(url, null, TokenVerificationResponse.class);
        assertNotNull(verifyResponse);
        assertEquals(charlieEmail, verifyResponse.email());
        assertNotNull(verifyResponse.company());
        assertTrue(verifyResponse.company().hasAdmin());
        assertEquals(charliePeppolId, verifyResponse.company().peppolId());
        assertEquals(charlieEmail, verifyResponse.requester().email());
        assertEquals(charlieCompany, verifyResponse.requester().company());

        // 4. login as Admin to approve
        charlieToken = login(charlieEmail, charliePassword, AccountType.ADMIN, charliePeppolId);
        //NOTE : call App to store verifyResponse.requester() as partner (and use the adminToken)

        // 5. POST /sapi/linked/approve
        HttpHeaders adminJwtHeaders = new HttpHeaders();
        adminJwtHeaders.setBearerAuth(charlieToken);
        url = baseUrl() + "/sapi/linked/approve?token=" + token;
        HttpEntity<Void> requestApprove = new HttpEntity<>(null, adminJwtHeaders);
        ResponseEntity<Void> responseApprove = restTemplate.exchange(url, HttpMethod.POST, requestApprove, Void.class);
        assertEquals(HttpStatus.OK, responseApprove.getStatusCode());
        assertNull(responseApprove.getBody());

        /// ---- ADDITIONAL TEST ----
        // +. POST /api/register/verify --> Should be invalid token
        url = baseUrl() + "/api/register/verify?token=" + token;
        ResponseEntity<String> failedResponse = restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, String.class);
        assertEquals(HttpStatus.BAD_REQUEST, failedResponse.getStatusCode());
        String body = failedResponse.getBody(); //Maybe not needed to verify ?
        assertNotNull(body);
        assertTrue(body.contains("\"errorCode\":\"token_already_verified\""));
    }

    /**
     *
     * How to generate test certificate:
     *
     * cert.cnf file:
     * [ req ]
     * default_bits       = 2048
     * prompt             = no
     * default_md         = sha256
     * distinguished_name = dn
     *
     * [ dn ]
     * C = BE
     * O = No idea
     * CN = Test Director
     * GN = Test
     * SN = Director
     * serialNumber = 1234567890
     *
     * openssl genrsa -out key.pem 2048
     * openssl req -new -key key.pem -out req.csr -config cert.cnf
     * openssl x509 -req -in req.csr -signkey key.pem -out cert.pem -days 365
     */

}
