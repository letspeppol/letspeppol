package org.letspeppol.kyc.controller;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.letspeppol.kyc.dto.*;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.repository.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RegistrationSteps.class)
class RegistrationTest {

    @Autowired RegistrationSteps registrationSteps;

    @Autowired private AccountRepository accountRepository;
    @Autowired private OwnershipRepository ownershipRepository;
    @MockitoBean private JavaMailSender javaMailSender;
    static MockWebServer mockWebServer;

    String adminCompany = "Test Company";
    String adminPeppolId = "0208:1234567890";
    String adminEmail = "test@company.com";
    String adminPassword = "dummy-password";
    String adminToken = null;

    String affiliateCompany = "Test Affiliate";
    String affiliatePeppolId = "0208:0987654321";
    String affiliateEmail = "test@affiliate.com";
    String affiliatePassword = "dummy-password";
    String affiliateToken = null;

    /// Bob is a company that has been invited by Affiliate to register and handles this at home
    String bobCompany = "Bob Company";
    String bobPeppolId = "0208:1111111111";
    String bobEmail = "bob@company.com";
    String bobPassword = "bob-password";
    String bobToken = null;

    /// Charlie is a company that has been invited by Affiliate to register and tries at home, but needs the affiliate to sign contract
    String charlieCompany = "Charlie Company";
    String charliePeppolId = "0208:2222222222";
    String charlieEmail = "charlie@company.com";
    String charliePassword = "charlie-password";
    String charlieToken = null;

    String deltaCompany = "Delta Company";
    String deltaPeppolId = "0208:3333333333";

    String echoCompany = "Echo Company";
    String echoPeppolId = "0208:4444444444";
    String echoEmail = "echo@company.com";
    String echoPassword = "echo-password";

    String foxtrotCompany = "Foxtrot Company";
    String foxtrotPeppolId = "0208:5555555555";

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
    @Order(1)
    void registrationNewAdmin() {
        registrationSteps.prepareDatabase(adminPeppolId, adminCompany);

        // 1. GET /api/register/company/{peppolId}
        registrationSteps.companyIsNewCompany(adminPeppolId);

        // 2. POST /api/register/confirm-company
        String emailToken = registrationSteps.confirmCompany(AccountType.ADMIN, adminPeppolId, adminEmail, "TestCity", "1234", "TestStreet");

        // 3. POST /api/register/verify
        Long directorId = registrationSteps.verifyAsNewAndSelfRequested(emailToken, adminPeppolId, adminEmail).getFirst().id();

        // 4. POST /api/identity/sign/prepare
        // 5. GET /api/identity/contract/{directorId}?token=...
        // 6. POST /api/identity/sign/finalize
        registrationSteps.signContract(adminPeppolId, adminEmail, directorId);
        registrationSteps.activateAccount(emailToken, adminPassword);
        assertTrue(ownershipRepository.existsByTypeAndCompanyPeppolId(AccountType.ADMIN, adminPeppolId));
    }

    @Test
    @Order(2)
    void registrationActiveAdmin() {
        if (!accountRepository.existsByEmail(adminEmail)) {
            registrationNewAdmin();
        }

        // 1. GET /api/register/company/{peppolId}
        registrationSteps.companyIsActiveCompany(adminPeppolId);
    }

    @Test
    @Order(3)
    void registrationNewAffiliate() {
        registrationSteps.prepareDatabase(affiliatePeppolId, affiliateCompany);

        // 1. GET /api/register/company/{peppolId}
        registrationSteps.companyIsNewCompany(affiliatePeppolId);

        // 2. POST /api/register/confirm-company
        String emailToken = registrationSteps.confirmCompany(AccountType.AFFILIATE, affiliatePeppolId, affiliateEmail, "TestCity", "1234", "TestStreet");

        // 3. POST /api/register/verify
        Long directorId = registrationSteps.verifyAsNewAndSelfRequested(emailToken, affiliatePeppolId, affiliateEmail).getFirst().id();

        // 4. POST /api/identity/sign/prepare
        // 5. GET /api/identity/contract/{directorId}?token=...
        // 6. POST /api/identity/sign/finalize
        registrationSteps.signContract(affiliatePeppolId, affiliateEmail, directorId);
        registrationSteps.activateAccount(emailToken, affiliatePassword);
        assertTrue(ownershipRepository.existsByTypeAndCompanyPeppolId(AccountType.ADMIN, affiliatePeppolId));
        assertTrue(ownershipRepository.existsByTypeAndCompanyPeppolId(AccountType.AFFILIATE, affiliatePeppolId));
    }

    @Test
    @Order(4)
    void registrationActiveAffiliate() {
        if (!accountRepository.existsByEmail(affiliateEmail)) {
            registrationNewAffiliate();
        }

        // 1. GET /api/register/company/{peppolId}
        registrationSteps.companyIsActiveCompany(affiliatePeppolId);
    }

    @Test
    @Order(5)
    void loginAdmin() {
        if (!accountRepository.existsByEmail(adminEmail)) {
            registrationNewAdmin();
        }
        adminToken = registrationSteps.login(adminEmail, adminPassword, adminPeppolId);
    }

    @Test
    @Order(5)
    void loginAdminAsAdmin() {
        if (!accountRepository.existsByEmail(adminEmail)) {
            registrationNewAdmin();
        }
        adminToken = registrationSteps.login(adminEmail, adminPassword, AccountType.ADMIN, adminPeppolId);
    }

    @Test
    @Order(5)
    void loginAffiliateAsAffiliate() {
        if (!accountRepository.existsByEmail(affiliateEmail)) {
            registrationNewAffiliate();
        }
        affiliateToken = registrationSteps.login(affiliateEmail, affiliatePassword, AccountType.AFFILIATE, affiliatePeppolId);
    }

    @Test
    @Order(5)
    void loginAffiliateAsAdmin() {
        if (!accountRepository.existsByEmail(affiliateEmail)) {
            registrationNewAffiliate();
        }
        affiliateToken = registrationSteps.login(affiliateEmail, affiliatePassword, AccountType.ADMIN, affiliatePeppolId);
    }

    @Test
    @Order(6)
    void swapAffiliateToAffiliate() {
        if (affiliateToken == null) {
            loginAffiliateAsAdmin();
        }
        affiliateToken = registrationSteps.swap(affiliateToken, AccountType.AFFILIATE, affiliatePeppolId);
    }

    @Test
    @Order(7)
    void registrationActiveAdminViaAffiliateAndVerifyByEmail() {
        if (!accountRepository.existsByEmail(adminEmail)) {
            registrationNewAdmin();
        }
        if (affiliateToken == null) {
            loginAffiliateAsAffiliate();
        }

        // 1. GET /api/register/company/{peppolId}
        registrationSteps.companyIsActiveCompany(adminPeppolId);

        // 2. POST /sapi/linked/request-company
        String emailToken = registrationSteps.requestCompany(affiliateToken, AccountType.ADMIN, adminPeppolId, adminEmail, "TestCity", "1234", "TestStreet"); //TODO : Why address ?

        // 3. POST /api/register/verify
        registrationSteps.verifyAsActiveAndRequestedByAffiliated(emailToken, adminPeppolId, adminEmail, affiliateEmail, affiliateCompany);

        // 4. login as Admin to approve
        loginAdminAsAdmin();
        //NOTE : call App to store verifyResponse.requester() as affiliate (and use the adminToken)

//        // 5. POST /sapi/linked/approve
//        HttpHeaders adminJwtHeaders = new HttpHeaders();
//        adminJwtHeaders.setBearerAuth(adminToken);
//        url = baseUrl() + "/sapi/linked/approve?token=" + emailToken;
//        HttpEntity<Void> requestApprove = new HttpEntity<>(null, adminJwtHeaders);
//        ResponseEntity<Void> responseApprove = restTemplate.exchange(url, HttpMethod.POST, requestApprove, Void.class);
//        assertEquals(HttpStatus.OK, responseApprove.getStatusCode());
//        assertNull(responseApprove.getBody());
//
//        /// ---- ADDITIONAL TEST ----
//        // +. POST /api/register/verify --> Should be invalid token
//        url = baseUrl() + "/api/register/verify?token=" + emailToken;
//        ResponseEntity<String> failedResponse = restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, String.class);
//        assertEquals(HttpStatus.BAD_REQUEST, failedResponse.getStatusCode());
//        String body = failedResponse.getBody(); //Maybe not needed to verify ?
//        assertNotNull(body);
//        assertTrue(body.contains("\"errorCode\":\"token_already_verified\""));
    }

    @Test
    @Order(8)
    void registrationNewAdminViaAffiliateAndVerifyByEmailBeforeSigning() {
        registrationSteps.prepareDatabase(bobPeppolId, bobCompany);

        if (affiliateToken == null) {
            loginAffiliateAsAffiliate();
        }

        // 1. GET /api/register/company/{peppolId}
        registrationSteps.companyIsNewCompany(bobPeppolId);

        // 2. POST /sapi/linked/request-company
        String emailToken = registrationSteps.requestCompany(affiliateToken, AccountType.ADMIN, bobPeppolId, bobEmail, "TestCity", "1234", "TestStreet");

        // 3. POST /api/register/verify
        Long directorId = registrationSteps.verifyAsNewAndRequestedByAffiliated(emailToken, bobPeppolId, bobEmail, affiliateEmail, affiliateCompany).getFirst().id();

        // 4. POST /api/identity/sign/prepare
        // 5. GET /api/identity/contract/{directorId}?token=...
        // 6. POST /api/identity/sign/finalize
        registrationSteps.signContract(bobPeppolId, bobEmail, directorId);
        registrationSteps.activateAccount(emailToken, bobPassword);
        assertTrue(ownershipRepository.existsByTypeAndCompanyPeppolId(AccountType.ADMIN, bobPeppolId));

        // 7. login as Admin to approve
        bobToken = registrationSteps.login(bobEmail, bobPassword, AccountType.ADMIN, bobPeppolId);
        //NOTE : call App to store verifyResponse.requester() as affiliate (and use the adminToken)

//        // 8. POST /sapi/linked/approve
//        HttpHeaders adminJwtHeaders = new HttpHeaders();
//        adminJwtHeaders.setBearerAuth(bobToken);
//        url = baseUrl() + "/sapi/linked/approve?token=" + emailToken;
//        HttpEntity<Void> requestApprove = new HttpEntity<>(null, adminJwtHeaders);
//        ResponseEntity<Void> responseApprove = restTemplate.exchange(url, HttpMethod.POST, requestApprove, Void.class);
//        assertEquals(HttpStatus.OK, responseApprove.getStatusCode());
//        assertNull(responseApprove.getBody());
//
//        /// ---- ADDITIONAL TEST ----
//        // +. POST /api/register/verify --> Should be invalid token
//        url = baseUrl() + "/api/register/verify?token=" + emailToken;
//        ResponseEntity<String> failedResponse = restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, String.class);
//        assertEquals(HttpStatus.BAD_REQUEST, failedResponse.getStatusCode());
//        String body = failedResponse.getBody(); //Maybe not needed to verify ?
//        assertNotNull(body);
//        assertTrue(body.contains("\"errorCode\":\"token_already_verified\""));
    }

    @Test
    @Order(9)
    void registrationNewAdminViaAffiliateAndSignBeforeEmailVerification() {
        registrationSteps.prepareDatabase(charliePeppolId, charlieCompany);

        if (affiliateToken == null) {
            loginAffiliateAsAffiliate();
        }

        // 1. GET /api/register/company/{peppolId}
        Long directorId = registrationSteps.companyIsNewCompany(charliePeppolId).getFirst().id();

        // 2. POST /sapi/linked/request-company
        String emailToken = registrationSteps.requestCompany(affiliateToken, AccountType.ADMIN, charliePeppolId, charlieEmail, "TestCity", "1234", "TestStreet");

        // 3. POST /api/identity/sign/prepare
        // 4. GET /api/identity/contract/{directorId}?token=...
        // 5. POST /api/identity/sign/finalize
        registrationSteps.signContract(charliePeppolId, charlieEmail, directorId);
        assertTrue(ownershipRepository.existsByTypeAndCompanyPeppolId(AccountType.ADMIN, charliePeppolId));

        // 6. POST /api/register/activate
        registrationSteps.activateAccount(emailToken, charliePassword);

        // 7. login as Admin to approve
        charlieToken = registrationSteps.login(charlieEmail, charliePassword, AccountType.ADMIN, charliePeppolId);
        //NOTE : call App to store verifyResponse.requester() as affiliate (and use the adminToken)

//        // 8. POST /sapi/linked/approve
//        HttpHeaders adminJwtHeaders = new HttpHeaders();
//        adminJwtHeaders.setBearerAuth(charlieToken);
//        url = baseUrl() + "/sapi/linked/approve?token=" + emailToken;
//        HttpEntity<Void> requestApprove = new HttpEntity<>(null, adminJwtHeaders);
//        ResponseEntity<Void> responseApprove = restTemplate.exchange(url, HttpMethod.POST, requestApprove, Void.class);
//        assertEquals(HttpStatus.OK, responseApprove.getStatusCode());
//        assertNull(responseApprove.getBody());
//
//        /// ---- ADDITIONAL TEST ----
//        // +. POST /api/register/verify --> Should be invalid token
//        url = baseUrl() + "/api/register/verify?token=" + emailToken;
//        ResponseEntity<String> failedResponse = restTemplate.exchange(url, HttpMethod.POST, HttpEntity.EMPTY, String.class);
//        assertEquals(HttpStatus.BAD_REQUEST, failedResponse.getStatusCode());
//        String body = failedResponse.getBody(); //Maybe not needed to verify ?
//        assertNotNull(body);
//        assertTrue(body.contains("\"errorCode\":\"token_already_verified\""));
    }

    @Test
    @Order(10)
    void registrationNewAdminByActiveAdmin() {
        if (adminToken == null) {
            loginAdminAsAdmin();
        }
        registrationSteps.prepareDatabase(deltaPeppolId, deltaCompany);
        Long directorId = registrationSteps.companyIsNewCompany(deltaPeppolId).getFirst().id();
        registrationSteps.signContractAsLoggedIn(adminToken, deltaPeppolId, directorId);
        assertTrue(ownershipRepository.existsByTypeAndCompanyPeppolId(AccountType.ADMIN, deltaPeppolId));
        assertTrue(accountRepository.existsByEmail(adminEmail));
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
