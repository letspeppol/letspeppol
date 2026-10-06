package org.letspeppol.kyc.controller;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.letspeppol.kyc.controller.CompanyUserSteps.ApiResult;
import org.letspeppol.kyc.controller.CompanyUserSteps.Authorization;
import org.letspeppol.kyc.dto.CompanyUserDto;
import org.letspeppol.kyc.dto.ConfirmCompanyRequest;
import org.letspeppol.kyc.dto.InvitationInfo;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.model.OwnershipInvitation;
import org.letspeppol.kyc.model.OwnershipStatus;
import org.letspeppol.kyc.repository.AccountRepository;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.OwnershipInvitationRepository;
import org.letspeppol.kyc.repository.OwnershipRepository;
import org.letspeppol.kyc.service.RateLimiterService;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({RegistrationSteps.class, CompanyUserSteps.class})
class CompanyUserTest {

    @Autowired RegistrationSteps registrationSteps;
    @Autowired CompanyUserSteps companyUserSteps;

    @Autowired private AccountRepository accountRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private OwnershipRepository ownershipRepository;
    @Autowired private OwnershipInvitationRepository invitationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtDecoder jwtDecoder;
    @MockitoBean private JavaMailSender javaMailSender;
    @MockitoSpyBean private RateLimiterService rateLimiterService;

    String company = "Users Company";
    String peppolId = "0208:6100000001";
    String adminName = "Ada Admin";
    String adminEmail = "admin@users-company.com";
    String adminPassword = "admin-password";
    String adminToken;

    String otherCompany = "Other Users Company";
    String otherPeppolId = "0208:6100000002";
    String otherAdminName = "Otto Other";
    String otherAdminEmail = "admin@other-users-company.com";
    String otherAdminPassword = "other-password";
    String otherAdminToken;

    String userPassword = "user-password";

    @BeforeEach
    void setUp() {
        Mockito.when(javaMailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        companyUserSteps.ensureAdmin(peppolId, company, adminName, adminEmail, adminPassword);
        companyUserSteps.ensureAdmin(otherPeppolId, otherCompany, otherAdminName, otherAdminEmail, otherAdminPassword);
        adminToken = registrationSteps.login(adminEmail, adminPassword, peppolId);
        otherAdminToken = registrationSteps.login(otherAdminEmail, otherAdminPassword, otherPeppolId);
    }

    @Test
    @Order(1)
    void adminInvitesNewUser() throws Exception {
        String email = "invited@users-company.com";

        CompanyUserDto invited = companyUserSteps.invite(adminToken, email, "Ivy Invited", 4);
        assertEquals("Ivy Invited", invited.name());
        assertEquals(7, invited.permissionMask());

        ArgumentCaptor<MimeMessage> mail = ArgumentCaptor.forClass(MimeMessage.class);
        Mockito.verify(javaMailSender).send(mail.capture());
        assertEquals(email, mail.getValue().getRecipients(Message.RecipientType.TO)[0].toString());
        assertTrue(mail.getValue().getSubject().contains(company));
        String body = mail.getValue().getContent().toString();
        assertTrue(body.contains("http://localhost:9000/invitation?token=" + companyUserSteps.invitationToken(invited.id())));
        assertTrue(body.contains(adminName));

        List<CompanyUserDto> users = companyUserSteps.listUsers(adminToken);
        CompanyUserDto adminRow = users.stream().filter(user -> user.email().equals(adminEmail)).findFirst().orElseThrow();
        assertEquals(AccountType.ADMIN, adminRow.type());
        assertEquals(OwnershipStatus.ACTIVE, adminRow.status());
        assertEquals(255, adminRow.permissionMask());
        assertNull(adminRow.invitationExpiresOn());
        assertTrue(users.stream().anyMatch(user -> user.id().equals(invited.id()) && user.status() == OwnershipStatus.INVITED));
        ApiResult raw = companyUserSteps.call(HttpMethod.GET, "/sapi/users", adminToken, null);
        assertDoesNotThrow(() -> Instant.parse(raw.body().get(0).path("createdOn").asString()));

        Account account = accountRepository.findByEmail(email).orElseThrow();
        assertFalse(account.isVerified());
        assertNull(account.getPasswordHash());
        assertEquals(CompanyUserSteps.LOGIN_FAILED,
                companyUserSteps.authorize(email, userPassword, peppolId, AccountType.USER).error());
    }

    @Test
    @Order(2)
    void invitedUserAcceptsAndSignsInWithGrantedPermissions() {
        String email = "accepting@users-company.com";
        CompanyUserDto invited = companyUserSteps.invite(adminToken, email, "Abel Accepting", 4);
        String token = companyUserSteps.invitationToken(invited.id());

        InvitationInfo info = companyUserSteps.verifyInvitation(token);
        assertEquals(email, info.email());
        assertEquals("Abel Accepting", info.name());
        assertEquals(company, info.companyName());
        assertEquals(peppolId, info.peppolId());
        assertTrue(info.passwordRequired());

        ApiResult withoutPassword = companyUserSteps.tryAcceptInvitation(token, null);
        assertEquals(HttpStatus.BAD_REQUEST, withoutPassword.status());
        assertEquals("invalid_password", withoutPassword.errorCode());
        assertEquals(HttpStatus.NO_CONTENT, companyUserSteps.tryAcceptInvitation(token, userPassword).status());

        Account account = accountRepository.findByEmail(email).orElseThrow();
        assertTrue(account.isVerified());
        Ownership ownership = ownershipRepository.findById(invited.id()).orElseThrow();
        assertEquals(OwnershipStatus.ACTIVE, ownership.getStatus());
        assertTrue(invitationRepository.findByOwnershipId(invited.id()).isEmpty());

        ApiResult reused = companyUserSteps.tryAcceptInvitation(token, userPassword);
        assertEquals(HttpStatus.BAD_REQUEST, reused.status());
        assertEquals("invitation_not_found", reused.errorCode());
        assertEquals("invitation_not_found", companyUserSteps.tryVerifyInvitation(token).errorCode());

        Authorization authorization = companyUserSteps.authorize(email, userPassword, peppolId, AccountType.USER);
        assertNull(authorization.error());
        Jwt jwt = jwtDecoder.decode(authorization.accessToken());
        assertEquals("USER", jwt.getClaimAsString("accountType"));
        assertEquals(peppolId, jwt.getClaimAsString("peppolId"));
        assertEquals(7L, ((Number) jwt.getClaim("permissionMask")).longValue());
        assertEquals(List.of(peppolId), companyUserSteps.ownedPeppolIds(authorization.accessToken()));

        ApiResult list = companyUserSteps.call(HttpMethod.GET, "/sapi/users", authorization.accessToken(), null);
        assertEquals(HttpStatus.FORBIDDEN, list.status());
        assertEquals("not_admin", list.errorCode());
        ApiResult requestCompany = companyUserSteps.call(HttpMethod.POST, "/sapi/linked/request-company", authorization.accessToken(),
                new ConfirmCompanyRequest(AccountType.ADMIN, otherPeppolId, "director@other-users-company.com", null, null, null));
        assertEquals(HttpStatus.FORBIDDEN, requestCompany.status());
        assertEquals("not_admin", requestCompany.errorCode());
    }

    @Test
    @Order(3)
    void permissionsAreNormalisedAndValidated() {
        String email = "permissions@users-company.com";
        CompanyUserDto user = companyUserSteps.inviteAndAccept(adminToken, email, "Pia Permissions", 0, userPassword);
        assertEquals(0, user.permissionMask());

        assertEquals(17, companyUserSteps.updatePermissions(adminToken, user.id(), 16).permissionMask());
        assertEquals(17, ownershipRepository.findById(user.id()).orElseThrow().getPermissionMask());
        assertEquals(169, companyUserSteps.updatePermissions(adminToken, user.id(), 168).permissionMask());

        for (int invalid : new int[]{256, 1024, -1}) {
            ApiResult rejected = companyUserSteps.tryUpdatePermissions(adminToken, user.id(), invalid);
            assertEquals(HttpStatus.BAD_REQUEST, rejected.status());
            assertEquals("invalid_permission_mask", rejected.errorCode());
        }
        ApiResult missing = companyUserSteps.call(HttpMethod.PUT, "/sapi/users/" + user.id() + "/permissions", adminToken, Map.of());
        assertEquals(HttpStatus.BAD_REQUEST, missing.status());
        assertEquals("validation_failed", missing.errorCode());
        assertEquals(169, ownershipRepository.findById(user.id()).orElseThrow().getPermissionMask());
        assertEquals("invalid_permission_mask",
                companyUserSteps.tryInvite(adminToken, "unknown-bits@users-company.com", "Una Unknown", 256).errorCode());
        assertTrue(accountRepository.findByEmail("unknown-bits@users-company.com").isEmpty());

        CompanyUserDto adminRow = companyUserSteps.listUsers(adminToken).stream()
                .filter(row -> row.type() == AccountType.ADMIN).findFirst().orElseThrow();
        ApiResult adminEdit = companyUserSteps.tryUpdatePermissions(adminToken, adminRow.id(), 1);
        assertEquals(HttpStatus.BAD_REQUEST, adminEdit.status());
        assertEquals("user_not_editable", adminEdit.errorCode());
        assertEquals("user_not_editable", companyUserSteps.tryRemove(adminToken, adminRow.id()).errorCode());

        Authorization authorization = companyUserSteps.authorize(email, userPassword, peppolId, AccountType.USER);
        assertEquals(169L, ((Number) jwtDecoder.decode(authorization.accessToken()).getClaim("permissionMask")).longValue());
    }

    @Test
    @Order(4)
    void suspendedUserLosesTheCompanyUntilReactivated() {
        String email = "suspended@users-company.com";
        CompanyUserDto user = companyUserSteps.inviteAndAccept(adminToken, email, "Sam Suspended", 1, userPassword);
        String userToken = companyUserSteps.authorize(email, userPassword, peppolId, AccountType.USER).accessToken();
        assertEquals(List.of(peppolId), companyUserSteps.ownedPeppolIds(userToken));

        ApiResult notSuspended = companyUserSteps.tryReactivate(adminToken, user.id());
        assertEquals(HttpStatus.BAD_REQUEST, notSuspended.status());
        assertEquals("user_not_editable", notSuspended.errorCode());

        assertEquals(OwnershipStatus.SUSPENDED, companyUserSteps.user(companyUserSteps.trySuspend(adminToken, user.id())).status());
        assertEquals("user_not_editable", companyUserSteps.trySuspend(adminToken, user.id()).errorCode());
        assertTrue(companyUserSteps.ownedPeppolIds(userToken).isEmpty());
        assertEquals("ownership_unavailable",
                companyUserSteps.authorize(email, userPassword, peppolId, AccountType.USER).error());

        assertEquals(OwnershipStatus.ACTIVE, companyUserSteps.user(companyUserSteps.tryReactivate(adminToken, user.id())).status());
        assertEquals(List.of(peppolId), companyUserSteps.ownedPeppolIds(userToken));
        assertNull(companyUserSteps.authorize(email, userPassword, peppolId, AccountType.USER).error());
    }

    @Test
    @Order(5)
    void existingAccountAcceptsWithoutChoosingAPassword() {
        String previousHash = accountRepository.findByEmail(otherAdminEmail).orElseThrow().getPasswordHash();

        CompanyUserDto invited = companyUserSteps.invite(adminToken, otherAdminEmail, "Typed By Admin", 3);
        assertEquals("Typed By Admin", invited.name());
        assertEquals("Typed By Admin", listedUser(invited.id()).name());
        assertEquals("Typed By Admin", companyUserSteps.updatePermissions(adminToken, invited.id(), 3).name());
        assertEquals(List.of(otherPeppolId), companyUserSteps.ownedPeppolIds(otherAdminToken));
        assertEquals("ownership_unavailable",
                companyUserSteps.authorize(otherAdminEmail, otherAdminPassword, peppolId, AccountType.USER).error());

        ApiResult duplicate = companyUserSteps.tryInvite(adminToken, otherAdminEmail, otherAdminName, 1);
        assertEquals(HttpStatus.BAD_REQUEST, duplicate.status());
        assertEquals("user_already_member", duplicate.errorCode());
        assertEquals("user_already_member", companyUserSteps.tryInvite(adminToken, adminEmail, adminName, 1).errorCode());

        String token = companyUserSteps.invitationToken(invited.id());
        assertFalse(companyUserSteps.verifyInvitation(token).passwordRequired());
        assertEquals(HttpStatus.NO_CONTENT, companyUserSteps.tryAcceptInvitation(token, "ignored-password").status());
        assertEquals(previousHash, accountRepository.findByEmail(otherAdminEmail).orElseThrow().getPasswordHash());
        assertTrue(passwordEncoder.matches(otherAdminPassword, previousHash));

        assertEquals(otherAdminName, listedUser(invited.id()).name());
        assertEquals(List.of(otherPeppolId, peppolId), companyUserSteps.ownedPeppolIds(otherAdminToken));
        Authorization authorization = companyUserSteps.authorize(otherAdminEmail, otherAdminPassword, peppolId, AccountType.USER);
        Jwt jwt = jwtDecoder.decode(authorization.accessToken());
        assertEquals("USER", jwt.getClaimAsString("accountType"));
        assertEquals(3L, ((Number) jwt.getClaim("permissionMask")).longValue());

        assertEquals(HttpStatus.NO_CONTENT, companyUserSteps.tryRemove(adminToken, invited.id()).status());
    }

    @Test
    @Order(6)
    void resendRotatesTheInvitationToken() {
        CompanyUserDto invited = companyUserSteps.invite(adminToken, "resend@users-company.com", "Rhea Resend", 1);
        String firstToken = companyUserSteps.invitationToken(invited.id());
        Mockito.clearInvocations(javaMailSender);

        assertEquals(HttpStatus.NO_CONTENT, companyUserSteps.tryResend(adminToken, invited.id()).status());
        String secondToken = companyUserSteps.invitationToken(invited.id());
        assertNotEquals(firstToken, secondToken);
        Mockito.verify(javaMailSender).send(Mockito.any(MimeMessage.class));
        assertEquals("invitation_not_found", companyUserSteps.tryVerifyInvitation(firstToken).errorCode());
        assertEquals("resend@users-company.com", companyUserSteps.verifyInvitation(secondToken).email());

        assertEquals(HttpStatus.NO_CONTENT, companyUserSteps.tryAcceptInvitation(secondToken, userPassword).status());
        ApiResult alreadyActive = companyUserSteps.tryResend(adminToken, invited.id());
        assertEquals(HttpStatus.BAD_REQUEST, alreadyActive.status());
        assertEquals("user_not_editable", alreadyActive.errorCode());
    }

    @Test
    @Order(7)
    void expiredInvitationIsRefused() {
        CompanyUserDto invited = companyUserSteps.invite(adminToken, "expired@users-company.com", "Eli Expired", 1);
        OwnershipInvitation invitation = invitationRepository.findByOwnershipId(invited.id()).orElseThrow();
        invitation.setExpiresOn(Instant.now().minusSeconds(60));
        invitationRepository.save(invitation);

        ApiResult verify = companyUserSteps.tryVerifyInvitation(invitation.getToken());
        assertEquals(HttpStatus.BAD_REQUEST, verify.status());
        assertEquals("invitation_expired", verify.errorCode());
        assertEquals("invitation_expired", companyUserSteps.tryAcceptInvitation(invitation.getToken(), userPassword).errorCode());
        assertEquals(OwnershipStatus.INVITED, ownershipRepository.findById(invited.id()).orElseThrow().getStatus());
    }

    @Test
    @Order(8)
    void usersOfAnotherCompanyAreNotFound() {
        CompanyUserDto user = companyUserSteps.inviteAndAccept(adminToken, "isolated@users-company.com", "Isa Isolated", 1, userPassword);

        for (ApiResult result : List.of(
                companyUserSteps.tryUpdatePermissions(otherAdminToken, user.id(), 255),
                companyUserSteps.trySuspend(otherAdminToken, user.id()),
                companyUserSteps.tryReactivate(otherAdminToken, user.id()),
                companyUserSteps.tryResend(otherAdminToken, user.id()),
                companyUserSteps.tryRemove(otherAdminToken, user.id()),
                companyUserSteps.tryRemove(adminToken, Long.MAX_VALUE))) {
            assertEquals(HttpStatus.NOT_FOUND, result.status());
            assertEquals("user_not_found", result.errorCode());
        }
        assertTrue(companyUserSteps.listUsers(otherAdminToken).stream().noneMatch(row -> row.id().equals(user.id())));
        Ownership untouched = ownershipRepository.findById(user.id()).orElseThrow();
        assertEquals(OwnershipStatus.ACTIVE, untouched.getStatus());
        assertEquals(1, untouched.getPermissionMask());
    }

    @Test
    @Order(9)
    void removedUserKeepsTheAccountButLosesTheCompany() {
        String email = "removed@users-company.com";
        CompanyUserDto user = companyUserSteps.inviteAndAccept(adminToken, email, "Remy Removed", 1, userPassword);
        String userToken = companyUserSteps.authorize(email, userPassword, peppolId, AccountType.USER).accessToken();

        assertEquals(HttpStatus.NO_CONTENT, companyUserSteps.tryRemove(adminToken, user.id()).status());
        assertTrue(ownershipRepository.findById(user.id()).isEmpty());
        assertTrue(accountRepository.findByEmail(email).isPresent());
        assertTrue(companyUserSteps.listUsers(adminToken).stream().noneMatch(row -> row.id().equals(user.id())));
        assertTrue(companyUserSteps.ownedPeppolIds(userToken).isEmpty());
        assertEquals("ownership_unavailable",
                companyUserSteps.authorize(email, userPassword, peppolId, AccountType.USER).error());
        assertEquals(HttpStatus.NOT_FOUND, companyUserSteps.tryRemove(adminToken, user.id()).status());

        CompanyUserDto pending = companyUserSteps.invite(adminToken, "withdrawn@users-company.com", "Wim Withdrawn", 1);
        String token = companyUserSteps.invitationToken(pending.id());
        assertEquals(HttpStatus.NO_CONTENT, companyUserSteps.tryRemove(adminToken, pending.id()).status());
        assertEquals("invitation_not_found", companyUserSteps.tryVerifyInvitation(token).errorCode());
    }

    @Test
    @Order(10)
    void adminTokenCarriesEveryPermission() {
        String accessToken = registrationSteps.oauth2AuthorizationCodeWithPkce(adminEmail, adminPassword, peppolId);

        Jwt jwt = jwtDecoder.decode(accessToken);
        assertEquals("ADMIN", jwt.getClaimAsString("accountType"));
        assertEquals(255L, ((Number) jwt.getClaim("permissionMask")).longValue());
    }

    @Test
    @Order(11)
    void invitationThatCannotBeMailedIsSavedAndReportedSoItCanBeResent() {
        String email = "unreachable@users-company.com";
        Mockito.doThrow(new MailSendException("smtp down")).when(javaMailSender).send(Mockito.any(MimeMessage.class));

        ApiResult failed = companyUserSteps.tryInvite(adminToken, email, "Una Unreachable", 1);
        assertEquals(HttpStatus.BAD_REQUEST, failed.status());
        assertEquals("invitation_not_sent", failed.errorCode());
        CompanyUserDto saved = companyUserSteps.listUsers(adminToken).stream()
                .filter(row -> row.email().equals(email)).findFirst().orElseThrow();
        assertEquals(OwnershipStatus.INVITED, saved.status());
        assertEquals("invitation_not_sent", companyUserSteps.tryResend(adminToken, saved.id()).errorCode());

        Mockito.doNothing().when(javaMailSender).send(Mockito.any(MimeMessage.class));
        assertEquals(HttpStatus.NO_CONTENT, companyUserSteps.tryResend(adminToken, saved.id()).status());
        assertEquals(email, companyUserSteps.verifyInvitation(companyUserSteps.invitationToken(saved.id())).email());
    }

    @Test
    @Order(12)
    void simultaneousInvitesForTheSameEmailCreateOneUser() {
        String email = "twice@users-company.com";
        CyclicBarrier bothPassedTheMemberCheck = new CyclicBarrier(2);
        Mockito.doAnswer(invocation -> {
            try {
                bothPassedTheMemberCheck.await(1, TimeUnit.SECONDS);
            } catch (TimeoutException | BrokenBarrierException alone) {
                Thread.interrupted();
            }
            return invocation.callRealMethod();
        }).when(rateLimiterService).checkInvitation(Mockito.anyLong(), Mockito.eq(email));

        List<CompletableFuture<ApiResult>> attempts = Stream
                .generate(() -> CompletableFuture.supplyAsync(() -> companyUserSteps.tryInvite(adminToken, email, "Tess Twice", 1)))
                .limit(2)
                .toList();
        List<ApiResult> results = attempts.stream().map(CompletableFuture::join).toList();

        assertEquals(1, results.stream().filter(result -> HttpStatus.OK.equals(result.status())).count());
        assertEquals(1, results.stream().filter(result -> "user_already_member".equals(result.errorCode())).count());
        assertEquals(1, accountRepository.findAll().stream().filter(account -> email.equals(account.getEmail())).count());
        assertEquals(1, companyUserSteps.listUsers(adminToken).stream().filter(row -> row.email().equals(email)).count());
    }

    @Test
    @Order(13)
    void databaseRefusesASecondUserOwnershipForTheSameAccountAndCompany() {
        CompanyUserDto invited = companyUserSteps.invite(adminToken, "unique@users-company.com", "Uma Unique", 1);
        Ownership existing = ownershipRepository.findById(invited.id()).orElseThrow();

        assertThrows(DataIntegrityViolationException.class, () -> ownershipRepository.saveAndFlush(
                new Ownership(existing.getAccount(), AccountType.USER, existing.getCompany())));
    }

    @Test
    @Order(14)
    void affiliateIsListedAndItsAccessIsManagedLikeAUser() {
        String email = "affiliate@users-company.com";
        Account account = accountRepository.save(Account.builder()
                .name("Alex Affiliate")
                .email(email)
                .passwordHash(passwordEncoder.encode(userPassword))
                .verified(true)
                .verifiedOn(Instant.now())
                .externalId(UUID.randomUUID())
                .build());
        Ownership affiliate = new Ownership(account, AccountType.AFFILIATE, companyRepository.findByPeppolId(peppolId).orElseThrow());
        affiliate.setPermissionMask(255);
        long id = ownershipRepository.save(affiliate).getId();

        CompanyUserDto row = listedUser(id);
        assertEquals(AccountType.AFFILIATE, row.type());
        assertEquals(OwnershipStatus.ACTIVE, row.status());
        assertEquals(255, row.permissionMask());

        assertEquals(17, companyUserSteps.updatePermissions(adminToken, id, 16).permissionMask());
        Jwt jwt = jwtDecoder.decode(companyUserSteps.authorize(email, userPassword, peppolId, AccountType.AFFILIATE).accessToken());
        assertEquals("AFFILIATE", jwt.getClaimAsString("accountType"));
        assertEquals(17L, ((Number) jwt.getClaim("permissionMask")).longValue());

        assertEquals(OwnershipStatus.SUSPENDED, companyUserSteps.user(companyUserSteps.trySuspend(adminToken, id)).status());
        assertEquals("ownership_unavailable",
                companyUserSteps.authorize(email, userPassword, peppolId, AccountType.AFFILIATE).error());
        assertEquals(OwnershipStatus.ACTIVE, companyUserSteps.user(companyUserSteps.tryReactivate(adminToken, id)).status());
        assertNull(companyUserSteps.authorize(email, userPassword, peppolId, AccountType.AFFILIATE).error());

        ApiResult removal = companyUserSteps.tryRemove(adminToken, id);
        assertEquals(HttpStatus.BAD_REQUEST, removal.status());
        assertEquals("user_not_editable", removal.errorCode());
        assertEquals("user_not_editable", companyUserSteps.tryResend(adminToken, id).errorCode());
        assertTrue(ownershipRepository.findById(id).isPresent());
        assertEquals(HttpStatus.NOT_FOUND, companyUserSteps.tryUpdatePermissions(otherAdminToken, id, 1).status());
    }

    private CompanyUserDto listedUser(long id) {
        return companyUserSteps.listUsers(adminToken).stream().filter(row -> row.id() == id).findFirst().orElseThrow();
    }
}
