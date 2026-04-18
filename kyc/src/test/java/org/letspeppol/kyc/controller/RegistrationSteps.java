package org.letspeppol.kyc.controller;

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
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestComponent
public class RegistrationSteps {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private DirectorRepository directorRepository;
    @Autowired private JwtService jwtService;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders basicHeader(String email, String password) {
        // Build Basic header
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(email, password, StandardCharsets.UTF_8);
        return headers;
    }

    private HttpHeaders jwtHeader(String token) {
        // Build JWT header
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
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

    String login(String email, String password, String peppolId) {
        // POST /api/jwt/auth
        String url = baseUrl() + "/api/jwt/auth";
        HttpEntity<Void> request = new HttpEntity<>(basicHeader(email, password));
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        JwtInfo jwtInfo = jwtService.validateAndGetInfo("Bearer " + response.getBody());
        assertEquals(peppolId, jwtInfo.peppolId());
        assertEquals(AccountType.ADMIN, jwtInfo.accountType());
        return response.getBody();
    }

    String login(String email, String password, AccountType accountType, String peppolId) {
        // POST /api/jwt/auth
        String url = baseUrl() + "/api/jwt/auth";
        AuthRequest authRequest = new AuthRequest(accountType, peppolId);
        HttpEntity<AuthRequest> request = new HttpEntity<>(authRequest, basicHeader(email, password));
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        JwtInfo jwtInfo = jwtService.validateAndGetInfo("Bearer " + response.getBody());
        assertEquals(peppolId, jwtInfo.peppolId());
        assertEquals(accountType, jwtInfo.accountType());
        return response.getBody();
    }

    String swap(String token, AccountType accountType, String peppolId) {
        // POST /sapi/jwt/swap
        String url = baseUrl() + "/sapi/jwt/swap";
        AuthRequest authRequest = new AuthRequest(accountType, peppolId);
        HttpEntity<AuthRequest> request = new HttpEntity<>(authRequest, jwtHeader(token));
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        JwtInfo jwtInfo = jwtService.validateAndGetInfo("Bearer " + response.getBody());
        assertEquals(peppolId, jwtInfo.peppolId());
        assertEquals(accountType, jwtInfo.accountType());
        return response.getBody();
    }

    CompanyResponse getCompany(String peppolId) {
        // GET /api/register/company/{peppolId}
        String url = baseUrl() + "/api/register/company/" + peppolId;
        CompanyResponse companyResponse = restTemplate.getForObject(url, CompanyResponse.class);
        assertNotNull(companyResponse);
        assertEquals(peppolId, companyResponse.peppolId());
        return companyResponse;
    }

    List<DirectorDto> companyIsNewCompany(String peppolId) {
        CompanyResponse companyResponse = getCompany(peppolId);
        assertFalse(companyResponse.hasAdmin());
        return companyResponse.directors();
    }

    void companyIsActiveCompany(String peppolId) {
        CompanyResponse companyResponse = getCompany(peppolId);
        assertTrue(companyResponse.hasAdmin());
    }

    String confirmCompany(AccountType accountType, String peppolId, String email, String city, String postCode, String street) {
        // POST /api/register/confirm-company
        String url = baseUrl() + "/api/register/confirm-company";
        ConfirmCompanyRequest confirmRequest = new ConfirmCompanyRequest(accountType, peppolId, email, city, postCode, street);
        SimpleMessage confirmResponse = restTemplate.postForObject(url, confirmRequest, SimpleMessage.class);
        assertNotNull(confirmResponse);
        assertTrue(confirmResponse.message().contains("Activation email sent"));

        // Simulate activation token
        EmailVerification verification = emailVerificationRepository.findAll().stream()
                .filter(v -> !v.isVerified() && email.equals(v.getEmail()) && peppolId.equals(v.getPeppolId()))
                .findFirst().orElseThrow();
        return verification.getToken();
    }

    String requestCompany(String affiliateToken, AccountType accountType, String peppolId, String email, String city, String postCode, String street) {
        // POST /sapi/linked/request-company
        String url = baseUrl() + "/sapi/linked/request-company";
        ConfirmCompanyRequest confirmRequest = new ConfirmCompanyRequest(accountType, peppolId, email, city, postCode, street); //TODO : no email ? Why address ?
        HttpEntity<ConfirmCompanyRequest> request = new HttpEntity<>(confirmRequest, jwtHeader(affiliateToken));
        ResponseEntity<SimpleMessage> response = restTemplate.exchange(url, HttpMethod.POST, request, SimpleMessage.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        SimpleMessage confirmResponse = response.getBody();
        assertTrue(confirmResponse.message().contains("Request email sent"));

        // Simulate activation token
        EmailVerification verification = emailVerificationRepository.findAll().stream()
                .filter(v -> !v.isVerified() && email.equals(v.getEmail())) //TODO : should we be able to do && (v.getRequester() != null && v.getRequester().getCompany() != null && v.getRequester().getAccount() != null && affiliatePeppolId.equals(v.getRequester().getCompany().getPeppolId()) && affiliateEmail.equals(v.getRequester().getAccount().getEmail())))
                .findFirst().orElseThrow();
        return verification.getToken();
    }

    TokenVerificationResponse verify(String token, String peppolId, String email) {
        // POST /api/register/verify
        String url = baseUrl() + "/api/register/verify?token=" + token;
        TokenVerificationResponse verifyResponse = restTemplate.postForObject(url, null, TokenVerificationResponse.class);
        assertNotNull(verifyResponse);
        assertEquals(email, verifyResponse.email());
        assertNotNull(verifyResponse.company());
        assertEquals(peppolId, verifyResponse.company().peppolId());
        return verifyResponse;
    }

    List<DirectorDto> verifyAsNewAndSelfRequested(String token, String peppolId, String email) {
        TokenVerificationResponse verifyResponse = verify(token, peppolId, email);
        assertFalse(verifyResponse.company().hasAdmin());
        assertNull(verifyResponse.requester());
        return verifyResponse.company().directors();
    }

    List<DirectorDto> verifyAsNewAndRequestedByAffiliated(String token, String peppolId, String email, String affiliateEmail, String affiliateCompany) {
        TokenVerificationResponse verifyResponse = verify(token, peppolId, email);
        assertFalse(verifyResponse.company().hasAdmin());
        assertNotNull(verifyResponse.requester());
        assertEquals(affiliateEmail, verifyResponse.requester().email());
        assertEquals(affiliateCompany, verifyResponse.requester().company());
        return verifyResponse.company().directors();
    }

    void verifyAsActiveAndRequestedByAffiliated(String token, String peppolId, String email, String affiliateEmail, String affiliateCompany) {
        TokenVerificationResponse verifyResponse = verify(token, peppolId, email);
        assertTrue(verifyResponse.company().hasAdmin());
        assertNotNull(verifyResponse.requester());
        assertEquals(affiliateEmail, verifyResponse.requester().email());
        assertEquals(affiliateCompany, verifyResponse.requester().company());
    }

    void signContract(String token, Long directorId, String password) {
        // Mock certificate chain for signing using mockStatic
        try (org.mockito.MockedStatic<CertificateUtil> mocked = Mockito.mockStatic(CertificateUtil.class)) {
            mocked.when(() -> CertificateUtil.getCertificateChain(Mockito.anyString()))
                    .thenReturn(new X509Certificate[] { Mockito.mock(X509Certificate.class) });

            // POST /api/identity/sign/prepare
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

            // GET /api/identity/contract/{directorId}?token=...
            String contractUrl = baseUrl() + "/api/identity/contract/" + directorId + "?token=" + token;
            ResponseEntity<byte[]> contractResponse = restTemplate.getForEntity(contractUrl, byte[].class);
            assertEquals(200, contractResponse.getStatusCode().value());
            assertNotNull(contractResponse.getBody());
            assertTrue(contractResponse.getBody().length > 0);
            assertNotNull(contractResponse.getHeaders().getContentType());
            assertEquals("application/pdf", contractResponse.getHeaders().getContentType().toString());

            // POST /api/identity/sign/finalize
            String signature = "Pip9ksT1yiqpP6AHEshmzl8ND+oPDF6PYjizuiKbHrwv23LqrqDRwJq/b2mbsAGScxYGdzk+sHGUsKcXr9YIiFXA9AM94GptSxwdjxulc2CA4qmd4KX9TdTjQGkCCj7qE0EMYULEtfPTMNPC61CYSic2fap4nicnBKFDGptHccblQICcNDHJ5hAN9fbFIw2OXWynomFgSBohVr0bDKcZQcUX9Chg0RUZ/4i95HdwXN306k343tLKB/doY+TO70akA3mzjBya+aGaE9QPE7zRvLF4IriRBy6QxzEPSsCHYHrP3w3mPLg2+xWX1Aw5M+m8K6XMuFC5O14Det8FZP4HWQ==";
            var finalizeRequest = new FinalizeSigningRequest(
                    token,
                    directorId,
                    certificate,
                    signature,
                    signatureAlgorithm,
                    prepareResponse.hashToSign(),
                    prepareResponse.hashToFinalize(),
                    password
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
    }

}
