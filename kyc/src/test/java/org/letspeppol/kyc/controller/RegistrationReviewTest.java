package org.letspeppol.kyc.controller;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.*;
import org.letspeppol.kyc.dto.DirectorDto;
import org.letspeppol.kyc.dto.RegistrationReviewDecisionResponse;
import org.letspeppol.kyc.dto.RegistrationReviewDto;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.Permission;
import org.letspeppol.kyc.model.ReviewStatus;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.repository.AccountRepository;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.DirectorRepository;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(RegistrationSteps.class)
class RegistrationReviewTest {

    private static final String REVIEWER_EMAIL = "reviewer@review.test";
    private static final String REVIEWER_PASSWORD = "reviewer-password";
    private static final String REVIEWS = "/sapi/backoffice/registration-reviews";

    @Autowired RegistrationSteps registrationSteps;
    @Autowired TestRestTemplate restTemplate;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired DirectorRepository directorRepository;
    @MockitoBean JavaMailSender javaMailSender;

    static MockWebServer mockWebServer;
    static String reviewerToken;

    @BeforeAll
    static void startMockServer() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse.Builder().body("{\"peppolActive\":true}").addHeader("Content-Type", "application/json").build();
            }
        });
        mockWebServer.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("proxy.api.url", () -> "http://localhost:" + mockWebServer.getPort());
    }

    @AfterAll
    static void shutdownMockServer() throws Exception {
        if (mockWebServer != null) mockWebServer.close();
    }

    @BeforeEach
    void setupMailSender() {
        Mockito.when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    @Order(1)
    void signerLearnsTheReviewOutcomeAtLoginAndARejectionCanStillBeApproved() {
        String peppolId = "0208:7300000001";
        String email = "outcome@review.test";
        Long reviewId = pendingRegistration(AccountType.ADMIN, peppolId, email, "outcome-password");
        assertEquals("ownership_pending_review", registrationSteps.authorizationRefusal(email, "outcome-password"));

        ResponseEntity<String> rejected = decide(reviewId, "reject");
        assertEquals(HttpStatus.OK, rejected.getStatusCode());
        assertEquals("ownership_review_rejected", registrationSteps.authorizationRefusal(email, "outcome-password"));
        assertEquals(HttpStatus.BAD_REQUEST, decide(reviewId, "reject").getStatusCode());

        RegistrationReviewDecisionResponse approved = registrationSteps.approveReview(reviewer(), reviewId);
        assertEquals(ReviewStatus.APPROVED, approved.review().reviewStatus());
        assertEquals(1, ownerships(peppolId, AccountType.ADMIN));
        assertNull(registrationSteps.authorizationRefusal(email, "outcome-password"));
        assertEquals(reviewId, reviews("APPROVED").getFirst().id());
        assertTrue(reviews("REJECTED").stream().noneMatch(review -> review.id().equals(reviewId)));
    }

    @Test
    @Order(2)
    void reviewerCannotDecideTheirOwnRegistration() {
        String peppolId = "0208:7300000002";
        reviewer();
        Long reviewId = pendingRegistration(AccountType.ADMIN, peppolId, REVIEWER_EMAIL, null);

        assertError(decide(reviewId, "approve"), HttpStatus.BAD_REQUEST, "review_own_registration");
        assertError(decide(reviewId, "reject"), HttpStatus.BAD_REQUEST, "review_own_registration");
        assertEquals(0, ownerships(peppolId, AccountType.ADMIN));
    }

    @Test
    @Order(3)
    void approvalIsRefusedWhileAnotherAccountAdministersTheCompany() {
        String peppolId = "0208:7300000003";
        registrationSteps.prepareDatabase(peppolId, "Two Directors", "Someone Else");
        Company company = companyRepository.findByPeppolId(peppolId).orElseThrow();
        directorRepository.save(new Director("Test Director", company));

        String mismatchToken = registrationSteps.confirmCompany(AccountType.ADMIN, peppolId, "mismatch@review.test", "TestCity", "1234", "TestStreet");
        List<DirectorDto> directors = registrationSteps.verifyAsNewAndSelfRequested(mismatchToken, peppolId, "mismatch@review.test");
        assertEquals("MANUAL_REVIEW", registrationSteps.signContract(peppolId, "mismatch@review.test", directorNamed(directors, "Someone Else"), false));

        String directorToken = registrationSteps.confirmCompany(AccountType.ADMIN, peppolId, "director@review.test", "TestCity", "1234", "TestStreet");
        registrationSteps.verifyAsNewAndSelfRequested(directorToken, peppolId, "director@review.test");
        registrationSteps.signContract(peppolId, "director@review.test", directorNamed(directors, "Test Director"));
        assertEquals(1, ownerships(peppolId, AccountType.ADMIN));

        RegistrationReviewDto review = pendingReviewFor(peppolId);
        assertTrue(review.companyHasAdmin());
        assertError(decide(review.id(), "approve"), HttpStatus.BAD_REQUEST, "review_company_has_admin");
        assertEquals(1, ownerships(peppolId, AccountType.ADMIN));
        assertEquals(HttpStatus.OK, decide(review.id(), "reject").getStatusCode());
    }

    @Test
    @Order(4)
    void simultaneousApprovalsLinkTheSignerOnce() throws Exception {
        String peppolId = "0208:7300000004";
        Long reviewId = pendingRegistration(AccountType.ADMIN, peppolId, "race@review.test", "race-password");
        String token = reviewer();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier start = new CyclicBarrier(2);
            Future<ResponseEntity<String>> first = executor.submit(() -> { start.await(); return decide(token, reviewId, "approve"); });
            Future<ResponseEntity<String>> second = executor.submit(() -> { start.await(); return decide(token, reviewId, "approve"); });
            List<ResponseEntity<String>> responses = List.of(first.get(), second.get());

            assertEquals(1, responses.stream().filter(response -> response.getStatusCode() == HttpStatus.OK).count());
            assertError(responses.stream().filter(response -> response.getStatusCode() != HttpStatus.OK).findFirst().orElseThrow(),
                    HttpStatus.BAD_REQUEST, "review_already_decided");
        } finally {
            executor.shutdown();
        }
        assertEquals(1, ownerships(peppolId, AccountType.ADMIN));
    }

    @Test
    @Order(5)
    void affiliateAndUnknownRequestsAreApprovedWithoutPeppolRegistration() {
        String affiliatePeppolId = "0208:7300000005";
        Long affiliateReview = pendingRegistration(AccountType.AFFILIATE, affiliatePeppolId, "affiliate@review.test", "affiliate-password");
        RegistrationReviewDecisionResponse affiliate = registrationSteps.approveReview(reviewer(), affiliateReview);
        assertNull(affiliate.registration());
        assertEquals(1, ownerships(affiliatePeppolId, AccountType.ADMIN));
        assertEquals(1, ownerships(affiliatePeppolId, AccountType.AFFILIATE));

        String legacyPeppolId = "0208:7300000006";
        Long legacyReview = pendingRegistration(AccountType.ADMIN, legacyPeppolId, "legacy@review.test", "legacy-password");
        jdbcTemplate.update("update director_identity_verification set requested_type = null where id = ?", legacyReview);
        assertNull(pendingReviewFor(legacyPeppolId).requestedType());
        RegistrationReviewDecisionResponse legacy = registrationSteps.approveReview(reviewer(), legacyReview);
        assertNull(legacy.registration());
        assertEquals(1, ownerships(legacyPeppolId, AccountType.ADMIN));
        assertEquals(0, ownerships(legacyPeppolId, AccountType.AFFILIATE));
    }

    @Test
    @Order(6)
    void reviewerWithoutCompanyManagesOnlyTheirOwnAccountSecurity() {
        String token = reviewer();
        assertEquals(HttpStatus.OK, get(token, "/sapi/totp/status").getStatusCode());
        assertEquals(HttpStatus.OK, get(token, "/sapi/passkeys").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get(token, "/sapi/account/ownerships").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, registrationSteps.companySearchStatus(token));

        String serviceToken = registrationSteps.clientCredentialsServiceToken();
        assertEquals(HttpStatus.FORBIDDEN, get(serviceToken, "/sapi/totp/status").getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, get(serviceToken, "/sapi/passkeys").getStatusCode());
    }

    @Test
    @Order(7)
    void pendingReviewsAreListedOldestFirstAndInvalidRequestsAreRejected() throws Exception {
        String olderPeppolId = "0208:7300000007";
        String newerPeppolId = "0208:7300000008";
        Long older = pendingRegistration(AccountType.ADMIN, olderPeppolId, "older@review.test", "older-password");
        Long newer = pendingRegistration(AccountType.ADMIN, newerPeppolId, "newer@review.test", "newer-password");
        List<Long> pendingIds = reviews("PENDING").stream().map(RegistrationReviewDto::id).toList();
        assertTrue(pendingIds.indexOf(older) < pendingIds.indexOf(newer));

        assertError(get(reviewer(), REVIEWS + "?status=NOT_REQUIRED"), HttpStatus.BAD_REQUEST, "invalid_review_status");
        assertError(get(reviewer(), REVIEWS + "?status=UNKNOWN"), HttpStatus.BAD_REQUEST, "validation_failed");

        Account signer = accountRepository.findByEmail("newer@review.test").orElseThrow();
        Files.delete(Path.of(System.getProperty("java.io.tmpdir"), "contracts",
                "contract_%s_%d.pdf".formatted(newerPeppolId.replace(':', '_'), signer.getId())));
        assertError(get(reviewer(), REVIEWS + "/" + newer + "/contract"), HttpStatus.NOT_FOUND, "contract_not_found");
    }

    private String reviewer() {
        if (reviewerToken == null) {
            registrationSteps.createStaffAccount(REVIEWER_EMAIL, REVIEWER_PASSWORD, Permission.REVIEW_REGISTRATIONS);
            reviewerToken = registrationSteps.staffOAuth2AuthorizationCodeWithPkce(REVIEWER_EMAIL, REVIEWER_PASSWORD);
        }
        return reviewerToken;
    }

    private Long pendingRegistration(AccountType type, String peppolId, String email, String password) {
        registrationSteps.prepareDatabase(peppolId, "Company " + peppolId, "Someone Else");
        String emailToken = registrationSteps.confirmCompany(type, peppolId, email, "TestCity", "1234", "TestStreet");
        Long directorId = registrationSteps.verifyAsNewAndSelfRequested(emailToken, peppolId, email).getFirst().id();
        assertEquals("MANUAL_REVIEW", registrationSteps.signContract(peppolId, email, directorId, false));
        if (password != null) {
            registrationSteps.activateAccount(emailToken, password);
        }
        return pendingReviewFor(peppolId).id();
    }

    private RegistrationReviewDto pendingReviewFor(String peppolId) {
        return reviews("PENDING").stream()
                .filter(review -> review.peppolId().equals(peppolId))
                .findFirst()
                .orElseThrow();
    }

    private List<RegistrationReviewDto> reviews(String status) {
        ResponseEntity<RegistrationReviewDto[]> response = restTemplate.exchange(
                REVIEWS + "?status=" + status, HttpMethod.GET, new HttpEntity<>(bearer(reviewer())), RegistrationReviewDto[].class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return List.of(response.getBody());
    }

    private ResponseEntity<String> decide(Long reviewId, String decision) {
        return decide(reviewer(), reviewId, decision);
    }

    private ResponseEntity<String> decide(String token, Long reviewId, String decision) {
        return restTemplate.exchange(REVIEWS + "/" + reviewId + "/" + decision, HttpMethod.POST, new HttpEntity<>(bearer(token)), String.class);
    }

    private ResponseEntity<String> get(String token, String path) {
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), String.class);
    }

    private void assertError(ResponseEntity<String> response, HttpStatus status, String errorCode) {
        assertEquals(status, response.getStatusCode(), response.getBody());
        try {
            assertEquals(errorCode, objectMapper.readTree(response.getBody()).path("errorCode").asString());
        } catch (Exception e) {
            throw new AssertionError("Unreadable error body: " + response.getBody(), e);
        }
    }

    private int ownerships(String peppolId, AccountType type) {
        return jdbcTemplate.queryForObject("""
                select count(*) from ownership o join company c on c.id = o.company_id
                where c.peppol_id = ? and o.type = ?::account_type
                """, Integer.class, peppolId, type.name());
    }

    private static Long directorNamed(List<DirectorDto> directors, String name) {
        return directors.stream().filter(director -> director.name().equals(name)).findFirst().orElseThrow().id();
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
