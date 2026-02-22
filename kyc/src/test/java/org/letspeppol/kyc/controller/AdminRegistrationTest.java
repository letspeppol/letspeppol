package org.letspeppol.kyc.controller;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.dto.*;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.EmailVerification;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.DirectorRepository;
import org.letspeppol.kyc.repository.EmailVerificationRepository;
import org.letspeppol.kyc.service.signing.CertificateUtil;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminRegistrationTest {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private DirectorRepository directorRepository;
    @MockitoBean private JavaMailSender javaMailSender;
    static MockWebServer mockWebServer;

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

    @Test
    void happyFlow() {
        final String baseUrl = "http://localhost:" + port;
        String peppolId = "0208:1234567890";
        String email = "test@company.com";
        // Insert test company in DB
        Company company = new Company(peppolId, "BE1234567890", "Test Company");
        company.setAddress("TestCity", "1234", "TestStreet");
        companyRepository.save(company);
        // Insert a director for the company
        Director director = new Director("Test Director", company);
        director.setRegistered(true);
        directorRepository.save(director);

        // 1. GET /company/{peppolId}
        String url = baseUrl + "/api/register/company/" + peppolId;
        CompanyResponse companyResponse = restTemplate.getForObject(url, CompanyResponse.class);
        assertNotNull(companyResponse);
        assertEquals(peppolId, companyResponse.peppolId());

        // 2. POST /confirm-company
        url = baseUrl + "/api/register/confirm-company";
        ConfirmCompanyRequest confirmRequest = new ConfirmCompanyRequest(AccountType.ADMIN, peppolId, email, "TestCity", "1234", "TestStreet");
        SimpleMessage confirmResponse = restTemplate.postForObject(url, confirmRequest, SimpleMessage.class);
        assertNotNull(confirmResponse);
        assertTrue(confirmResponse.message().contains("Activation email sent"));

        // Simulate activation token
        EmailVerification verification = emailVerificationRepository.findAll().stream()
                .filter(v -> email.equals(v.getEmail()) && peppolId.equals(v.getPeppolId()))
                .findFirst().orElseThrow();
        String token = verification.getToken();

        // 3. POST /verify
        url = baseUrl + "/api/register/verify?token=" + token;
        TokenVerificationResponse verifyResponse = restTemplate.postForObject(url, null, TokenVerificationResponse.class);
        assertNotNull(verifyResponse);
        assertEquals(email, verifyResponse.email());
        assertEquals(peppolId, verifyResponse.company().peppolId());

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
            String prepareUrl = baseUrl + "/api/identity/sign/prepare";
            PrepareSigningResponse prepareResponse = restTemplate.postForObject(prepareUrl, prepareRequest, PrepareSigningResponse.class);
            System.out.println("prepareResponse: " + prepareResponse);
            assertNotNull(prepareResponse);
            assertNotNull(prepareResponse.hashToSign());
            assertNotNull(prepareResponse.hashToFinalize());
            assertEquals("SHA-256", prepareResponse.hashFunction());
            assertTrue(prepareResponse.allowedToSign());

            // 5. GET /api/identity/contract/{directorId}?token=...
            String contractUrl = baseUrl + "/api/identity/contract/" + directorId + "?token=" + token;
            ResponseEntity<byte[]> contractResponse = restTemplate.getForEntity(contractUrl, byte[].class);
            assertEquals(200, contractResponse.getStatusCode().value());
            assertNotNull(contractResponse.getBody());
            assertTrue(contractResponse.getBody().length > 0);
            assertNotNull(contractResponse.getHeaders().getContentType());
            assertEquals("application/pdf", contractResponse.getHeaders().getContentType().toString());

            // 6. POST /api/identity/sign/finalize
            String signature = "Pip9ksT1yiqpP6AHEshmzl8ND+oPDF6PYjizuiKbHrwv23LqrqDRwJq/b2mbsAGScxYGdzk+sHGUsKcXr9YIiFXA9AM94GptSxwdjxulc2CA4qmd4KX9TdTjQGkCCj7qE0EMYULEtfPTMNPC61CYSic2fap4nicnBKFDGptHccblQICcNDHJ5hAN9fbFIw2OXWynomFgSBohVr0bDKcZQcUX9Chg0RUZ/4i95HdwXN306k343tLKB/doY+TO70akA3mzjBya+aGaE9QPE7zRvLF4IriRBy6QxzEPSsCHYHrP3w3mPLg2+xWX1Aw5M+m8K6XMuFC5O14Det8FZP4HWQ==";
            String password = "dummy-password";
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
            String finalizeUrl = baseUrl + "/api/identity/sign/finalize";
            ResponseEntity<byte[]> finalizeResponse = restTemplate.postForEntity(finalizeUrl, finalizeRequest, byte[].class);
            assertEquals(200, finalizeResponse.getStatusCode().value());
            assertNotNull(finalizeResponse.getBody());
            assertTrue(finalizeResponse.getBody().length > 0);
            assertNotNull(finalizeResponse.getHeaders().getContentType());
            assertEquals("application/pdf", finalizeResponse.getHeaders().getContentType().toString());
            assertNotNull(finalizeResponse.getHeaders().get("Registration-Status"));
        }
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
