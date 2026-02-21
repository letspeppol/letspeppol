package org.letspeppol.kyc.controller;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.dto.CompanyResponse;
import org.letspeppol.kyc.dto.ConfirmCompanyRequest;
import org.letspeppol.kyc.dto.SimpleMessage;
import org.letspeppol.kyc.dto.TokenVerificationResponse;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.EmailVerification;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.EmailVerificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminRegistrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @MockitoBean
    private JavaMailSender javaMailSender;

    @Test
    void happyFlow() {
        final String baseUrl = "http://localhost:" + port;
        String peppolId = "0208:1234567890";
        String email = "test@company.com";
        // Insert test company in DB
        Company company = new Company(peppolId, "BE1234567890", "Test Company");
        company.setAddress("TestCity", "1234", "TestStreet");
        companyRepository.save(company);

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

    }
}
