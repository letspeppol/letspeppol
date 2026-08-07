package org.letspeppol.kyc.controller;

import org.letspeppol.kyc.dto.*;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.EmailVerification;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.repository.*;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.Ownership;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;
import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestComponent
public class RegistrationSteps {

    private static final byte[] SHA256_DIGEST_INFO_PREFIX = {
            0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48,
            0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20
    };

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private EmailVerificationRepository emailVerificationRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private DirectorRepository directorRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OwnershipRepository ownershipRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtEncoder jwtEncoder;
    @Autowired private JwtDecoder jwtDecoder;

    @Value("${oauth2.audience:letspeppol-api}") private String audience;
    @Value("${spring.security.oauth2.authorizationserver.issuer:}") private String issuer;

    private record TestSigningIdentity(String certificate, KeyPair keyPair) {}

    private static TestSigningIdentity testSigningIdentity() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            X500Principal subject = new X500Principal(
                    "CN=Test Director, GIVENNAME=Test, SURNAME=Director, SERIALNUMBER=1234567890");
            var builder = new JcaX509v3CertificateBuilder(
                    subject, BigInteger.ONE,
                    new Date(System.currentTimeMillis() - 60_000L),
                    new Date(System.currentTimeMillis() + 86_400_000L),
                    subject, keyPair.getPublic());
            var contentSigner = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
            X509Certificate certificate = new JcaX509CertificateConverter().getCertificate(builder.build(contentSigner));
            return new TestSigningIdentity(Base64.getEncoder().encodeToString(certificate.getEncoded()), keyPair);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create test signing identity", e);
        }
    }

    private static String signPreparedDigest(TestSigningIdentity identity, String base64Digest) {
        try {
            byte[] digest = Base64.getDecoder().decode(base64Digest);
            byte[] digestInfo = new byte[SHA256_DIGEST_INFO_PREFIX.length + digest.length];
            System.arraycopy(SHA256_DIGEST_INFO_PREFIX, 0, digestInfo, 0, SHA256_DIGEST_INFO_PREFIX.length);
            System.arraycopy(digest, 0, digestInfo, SHA256_DIGEST_INFO_PREFIX.length, digest.length);
            Signature signature = Signature.getInstance("NONEwithRSA");
            signature.initSign(identity.keyPair().getPrivate());
            signature.update(digestInfo);
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign prepared test digest", e);
        }
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
        Company company = new Company(peppolId, "1234567890", "BE1234567890", companyName);
        company.setAddress("TestCity", "1234", "TestStreet");
        companyRepository.save(company);
        // Insert a director for the company
        Director director = new Director("Test Director", company);
        director.setRegistered(true);
        directorRepository.save(director);
    }

    /**
     * Stands in for the browser's authorization-code login: verifies the credentials the way the
     * form-login {@code DaoAuthenticationProvider} does, then mints the access token the
     * authorization server would have issued for the account's acting (most recently used)
     * ownership. Driving the full PKCE redirect dance here would test Spring Security, not these
     * registration flows.
     */
    String login(String email, String password, String peppolId) {
        return login(email, password, AccountType.ADMIN, peppolId);
    }

    String login(String email, String password, AccountType accountType, String peppolId) {
        Account account = accountRepository.findByEmail(email.toLowerCase()).orElseThrow();
        assertTrue(account.isVerified(), "Account must be verified to sign in: " + email);
        assertTrue(passwordEncoder.matches(password, account.getPasswordHash()), "Wrong password for " + email);

        Ownership ownership = ownershipRepository
                .findFirstByAccountIdAndCompanyPeppolIdAndTypeOrderByLastUsedDesc(account.getId(), peppolId, accountType)
                .orElseThrow(() -> new AssertionError("No " + accountType + " ownership of " + peppolId + " for " + email));
        // Selecting an ownership at sign-in is what the UI does right after login.
        ownership.setLastUsed(Instant.now());
        ownershipRepository.save(ownership);

        return mintAccessToken(account, ownership);
    }

    /**
     * Swaps the acting company/role: hits the real selection endpoint, then mints the token the
     * client would obtain from the follow-up silent re-authorization.
     */
    String swap(String token, AccountType accountType, String peppolId) {
        HttpEntity<AuthRequest> request = new HttpEntity<>(new AuthRequest(accountType, peppolId), jwtHeader(token));
        ResponseEntity<Void> response = restTemplate.exchange("/sapi/account/ownership", HttpMethod.POST, request, Void.class);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        UUID uid = UUID.fromString(jwtDecoder.decode(token).getClaimAsString("uid"));
        Account account = accountRepository.findByExternalId(uid).orElseThrow();
        Ownership acting = ownershipRepository.findFirstByAccountIdOrderByLastUsedDesc(account.getId()).orElseThrow();
        assertEquals(peppolId, acting.getCompany().getPeppolId());
        assertEquals(accountType, acting.getType());

        return mintAccessToken(account, acting);
    }

    /** Mints an access token with the same claim set {@code SecurityConfig#tokenCustomizer} produces. */
    private String mintAccessToken(Account account, Ownership ownership) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .subject(account.getEmail())
                .audience(List.of(audience))
                .claim("uid", account.getExternalId().toString())
                .claim("accountType", ownership.getType().name())
                .claim("peppolId", ownership.getCompany().getPeppolId())
                .claim("peppolActive", ownership.getCompany().isPeppolActive());
        if (issuer != null && !issuer.isBlank()) {
            claims.issuer(issuer);
        }
        return jwtEncoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }

    CompanyResponse getCompany(String peppolId) {
        // GET /api/register/company/{peppolId}
        String url = "/api/register/company/" + peppolId;
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
        String url = "/api/register/confirm-company";
        ConfirmCompanyRequest confirmRequest = new ConfirmCompanyRequest(accountType, peppolId, email, city, postCode, street);
        SimpleMessage confirmResponse = restTemplate.postForObject(url, confirmRequest, SimpleMessage.class);
        assertNotNull(confirmResponse);
        assertTrue(confirmResponse.message().contains("Activation email sent"));

        // Simulate activation token
        EmailVerification verification = emailVerificationRepository.findAll().stream()
                .filter(v -> email.equals(v.getEmail()))
                .filter(v -> peppolId.equals(v.getPeppolId()))
                .max(java.util.Comparator.comparing(EmailVerification::getCreatedOn))
                .orElseThrow();
        return verification.getToken();
    }

    String requestCompany(String affiliateToken, AccountType accountType, String peppolId, String email, String city, String postCode, String street) {
        // POST /sapi/linked/request-company
        String url = "/sapi/linked/request-company";
        ConfirmCompanyRequest confirmRequest = new ConfirmCompanyRequest(accountType, peppolId, email, city, postCode, street); //TODO : no email ? Why address ?
        HttpEntity<ConfirmCompanyRequest> request = new HttpEntity<>(confirmRequest, jwtHeader(affiliateToken));
        ResponseEntity<SimpleMessage> response = restTemplate.exchange(url, HttpMethod.POST, request, SimpleMessage.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        SimpleMessage confirmResponse = response.getBody();
        assertTrue(confirmResponse.message().contains("Request email sent"));

        // Simulate activation token
        EmailVerification verification = emailVerificationRepository.findAll().stream()
                .filter(v -> email.equals(v.getEmail()))
                .filter(v -> peppolId.equals(v.getPeppolId()))
                .max(java.util.Comparator.comparing(EmailVerification::getCreatedOn))
                .orElseThrow();
        return verification.getToken();
    }

    TokenVerificationResponse verify(String token, String peppolId, String email) {
        // POST /api/register/verify
        String url = "/api/register/verify?token=" + token;
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

    void signContract(String peppolId, String email, Long directorId) {
        {
            TestSigningIdentity identity = testSigningIdentity();
            String certificate = identity.certificate();
            var signatureAlgorithm = new SignatureAlgorithm("SHA256", "PKCS1", "RSA");
            var prepareRequest = new PrepareSigningRequest(
                    peppolId,
                    directorId,
                    certificate,
                    java.util.List.of(signatureAlgorithm),
                    "en"
            );
            String prepareUrl = "/api/identity/sign/prepare";
            PrepareSigningResponse prepareResponse = restTemplate.postForObject(prepareUrl, prepareRequest, PrepareSigningResponse.class);
            assertNotNull(prepareResponse);
            assertNotNull(prepareResponse.hashToSign());
            assertNotNull(prepareResponse.hashToFinalize());
            assertEquals("SHA-256", prepareResponse.hashFunction());
            assertTrue(prepareResponse.allowedToSign());

            String contractUrl = "/api/identity/contract/" + peppolId + "/" + directorId;
            ResponseEntity<byte[]> contractResponse = restTemplate.getForEntity(contractUrl, byte[].class);
            assertEquals(200, contractResponse.getStatusCode().value());
            assertNotNull(contractResponse.getBody());
            assertTrue(contractResponse.getBody().length > 0);
            assertNotNull(contractResponse.getHeaders().getContentType());
            assertEquals("application/pdf", contractResponse.getHeaders().getContentType().toString());

            String signature = signPreparedDigest(identity, prepareResponse.hashToSign());
            var finalizeRequest = new FinalizeSigningRequest(
                    peppolId,
                    directorId,
                    email,
                    certificate,
                    signature,
                    signatureAlgorithm,
                    prepareResponse.hashToSign(),
                    prepareResponse.hashToFinalize()
            );
            String finalizeUrl = "/api/identity/sign/finalize";
            ResponseEntity<byte[]> finalizeResponse = restTemplate.postForEntity(finalizeUrl, finalizeRequest, byte[].class);
            assertEquals(200, finalizeResponse.getStatusCode().value());
            assertNotNull(finalizeResponse.getBody());
            assertTrue(finalizeResponse.getBody().length > 0);
            assertNotNull(finalizeResponse.getHeaders().getContentType());
            assertEquals("application/pdf", finalizeResponse.getHeaders().getContentType().toString());
            assertNotNull(finalizeResponse.getHeaders().get("Registration-Status"));
        }
    }

    void signContractAsLoggedIn(String jwtToken, String peppolId, Long directorId) {
        {
            TestSigningIdentity identity = testSigningIdentity();
            String certificate = identity.certificate();
            var signatureAlgorithm = new SignatureAlgorithm("SHA256", "PKCS1", "RSA");
            var prepareRequest = new PrepareSigningRequest(
                    peppolId,
                    directorId,
                    certificate,
                    java.util.List.of(signatureAlgorithm),
                    "en"
            );
            PrepareSigningResponse prepareResponse = restTemplate.postForObject("/api/identity/sign/prepare", prepareRequest, PrepareSigningResponse.class);
            assertNotNull(prepareResponse);

            String signature = signPreparedDigest(identity, prepareResponse.hashToSign());
            var finalizeRequest = new FinalizeSigningRequest(
                    peppolId,
                    directorId,
                    null,
                    certificate,
                    signature,
                    signatureAlgorithm,
                    prepareResponse.hashToSign(),
                    prepareResponse.hashToFinalize()
            );
            HttpEntity<FinalizeSigningRequest> request = new HttpEntity<>(finalizeRequest, jwtHeader(jwtToken));
            ResponseEntity<byte[]> finalizeResponse = restTemplate.exchange("/api/identity/sign/finalize", HttpMethod.POST, request, byte[].class);
            assertEquals(200, finalizeResponse.getStatusCode().value());
            assertNotNull(finalizeResponse.getBody());
        }
    }

    void signContractAsRequester(String jwtToken, String peppolId, String email, Long directorId) {
        {
            TestSigningIdentity identity = testSigningIdentity();
            String certificate = identity.certificate();
            var signatureAlgorithm = new SignatureAlgorithm("SHA256", "PKCS1", "RSA");
            var prepareRequest = new PrepareSigningRequest(
                    peppolId,
                    directorId,
                    certificate,
                    java.util.List.of(signatureAlgorithm),
                    "en"
            );
            PrepareSigningResponse prepareResponse = restTemplate.postForObject("/api/identity/sign/prepare", prepareRequest, PrepareSigningResponse.class);
            assertNotNull(prepareResponse);

            String signature = signPreparedDigest(identity, prepareResponse.hashToSign());
            var finalizeRequest = new FinalizeSigningRequest(
                    peppolId,
                    directorId,
                    email,
                    certificate,
                    signature,
                    signatureAlgorithm,
                    prepareResponse.hashToSign(),
                    prepareResponse.hashToFinalize()
            );
            HttpEntity<FinalizeSigningRequest> request = new HttpEntity<>(finalizeRequest, jwtHeader(jwtToken));
            ResponseEntity<byte[]> finalizeResponse = restTemplate.exchange("/api/identity/sign/finalize", HttpMethod.POST, request, byte[].class);
            assertEquals(200, finalizeResponse.getStatusCode().value());
            assertNotNull(finalizeResponse.getBody());
            assertTrue(finalizeResponse.getBody().length > 0);
        }
    }

    void activateAccount(String token, String password) {
        String url = "/api/register/verify-account";
        SetPasswordRequest request = new SetPasswordRequest(token, password);
        ResponseEntity<Void> response = restTemplate.postForEntity(url, request, Void.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

}
