package org.letspeppol.kyc.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.AcceptInvitationRequest;
import org.letspeppol.kyc.dto.CompanyUserDto;
import org.letspeppol.kyc.dto.InvitationInfo;
import org.letspeppol.kyc.dto.InviteUserRequest;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.exception.NotFoundException;
import org.letspeppol.kyc.mapper.CompanyUserMapper;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.CompanyPermission;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.model.OwnershipInvitation;
import org.letspeppol.kyc.model.OwnershipStatus;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.OwnershipInvitationRepository;
import org.letspeppol.kyc.repository.OwnershipRepository;
import org.letspeppol.kyc.service.mail.UserInvitationEmailTemplateProvider;
import org.letspeppol.kyc.util.LocaleUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompanyUserService {

    private static final Set<AccountType> LISTED_TYPES = EnumSet.of(AccountType.ADMIN, AccountType.USER, AccountType.USER_DRAFT, AccountType.USER_READ);
    private static final Set<AccountType> MEMBER_TYPES = EnumSet.complementOf(EnumSet.of(AccountType.APP));

    private final OwnershipRepository ownershipRepository;
    private final OwnershipInvitationRepository invitationRepository;
    private final CompanyRepository companyRepository;
    private final AccountService accountService;
    private final PasswordResetService passwordResetService;
    private final RateLimiterService rateLimiterService;
    private final JavaMailSender mailSender;
    private final UserInvitationEmailTemplateProvider templateProvider;
    private final TransactionTemplate transactionTemplate;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.mail.user-invitation.base-url}")
    private String baseUrl;

    @Value("${app.mail.user-invitation.ttl-days:7}")
    private long ttlDays;

    @Value("${app.mail.from:noreply@example.com}")
    private String fromAddress;

    @Transactional(readOnly = true)
    public List<CompanyUserDto> list(Ownership admin) {
        Long companyId = admin.getCompany().getId();
        Map<Long, OwnershipInvitation> invitations = invitationRepository.findByOwnershipCompanyId(companyId).stream()
                .collect(Collectors.toMap(invitation -> invitation.getOwnership().getId(), Function.identity()));
        return ownershipRepository.findByCompanyIdAndTypeInOrderByCreatedOnAsc(companyId, LISTED_TYPES).stream()
                .map(ownership -> CompanyUserMapper.toCompanyUserDto(ownership, invitations.get(ownership.getId())))
                .toList();
    }

    public CompanyUserDto invite(Ownership admin, InviteUserRequest request, String acceptLanguage) {
        int permissionMask = validPermissionMask(request.permissionMask());
        SavedInvitation saved = transactionTemplate.execute(status -> saveInvitation(admin, request, permissionMask));
        log.info("Invited {} as user of company {}", saved.mail().to(), saved.mail().peppolId());
        send(saved.mail(), LocaleUtil.extractLanguageTag(acceptLanguage));
        return saved.user();
    }

    @Transactional
    public CompanyUserDto updatePermissions(Ownership admin, Long id, int requestedMask) {
        Ownership ownership = editableUser(admin, id);
        ownership.setPermissionMask(validPermissionMask(requestedMask));
        return toDto(ownershipRepository.save(ownership));
    }

    @Transactional
    public CompanyUserDto suspend(Ownership admin, Long id) {
        return changeStatus(admin, id, OwnershipStatus.ACTIVE, OwnershipStatus.SUSPENDED);
    }

    @Transactional
    public CompanyUserDto reactivate(Ownership admin, Long id) {
        return changeStatus(admin, id, OwnershipStatus.SUSPENDED, OwnershipStatus.ACTIVE);
    }

    public void resendInvitation(Ownership admin, Long id, String acceptLanguage) {
        InvitationMail mail = transactionTemplate.execute(status -> rotateInvitation(admin, id));
        send(mail, LocaleUtil.extractLanguageTag(acceptLanguage));
    }

    @Transactional
    public void remove(Ownership admin, Long id) {
        Ownership ownership = editableUser(admin, id);
        invitationRepository.findByOwnershipId(ownership.getId()).ifPresent(invitationRepository::delete);
        ownershipRepository.delete(ownership);
        log.info("Removed {} as user of company {}", ownership.getAccount().getEmail(), admin.getCompany().getPeppolId());
    }

    @Transactional(readOnly = true)
    public InvitationInfo verifyInvitation(String token) {
        return CompanyUserMapper.toInvitationInfo(validInvitation(token));
    }

    @Transactional
    public void acceptInvitation(AcceptInvitationRequest request) {
        OwnershipInvitation invitation = validInvitation(request.token());
        Ownership ownership = invitation.getOwnership();
        Account account = ownership.getAccount();
        if (!account.isVerified()) {
            passwordResetService.setPassword(account, request.newPassword());
            accountService.verify(account);
        }
        ownership.setStatus(OwnershipStatus.ACTIVE);
        ownershipRepository.save(ownership);
        invitationRepository.delete(invitation);
        log.info("{} accepted the invitation for company {}", account.getEmail(), ownership.getCompany().getPeppolId());
    }

    private SavedInvitation saveInvitation(Ownership admin, InviteUserRequest request, int permissionMask) {
        Company company = companyRepository.findByIdForUpdate(admin.getCompany().getId())
                .orElseThrow(() -> new NotFoundException(KycErrorCodes.COMPANY_NOT_FOUND));
        Optional<Account> existingAccount = accountService.findByEmail(request.email());
        if (existingAccount.isPresent()
                && ownershipRepository.existsByAccountIdAndCompanyIdAndTypeIn(existingAccount.get().getId(), company.getId(), MEMBER_TYPES)) {
            throw new KycException(KycErrorCodes.USER_ALREADY_MEMBER);
        }
        rateLimiterService.checkInvitation(company.getId(), request.email());
        String invitedName = request.name().trim();
        Account account = existingAccount.orElseGet(() -> accountService.createPendingAccount(request.email(), invitedName));
        Ownership ownership = new Ownership(account, AccountType.USER, company);
        ownership.setStatus(OwnershipStatus.INVITED);
        ownership.setPermissionMask(permissionMask);
        ownershipRepository.save(ownership);
        OwnershipInvitation invitation = invitationRepository.save(
                new OwnershipInvitation(ownership, admin.getAccount(), invitedName, generateToken(), expiry()));
        return new SavedInvitation(CompanyUserMapper.toCompanyUserDto(ownership, invitation), toMail(invitation, admin));
    }

    private InvitationMail rotateInvitation(Ownership admin, Long id) {
        Ownership ownership = editableUser(admin, id);
        if (ownership.getStatus() != OwnershipStatus.INVITED) {
            throw new KycException(KycErrorCodes.USER_NOT_EDITABLE);
        }
        String email = ownership.getAccount().getEmail();
        rateLimiterService.checkInvitation(admin.getCompany().getId(), email);
        OwnershipInvitation invitation = invitationRepository.findByOwnershipId(ownership.getId())
                .orElseGet(() -> new OwnershipInvitation(ownership, admin.getAccount(), email, null, null));
        invitation.setToken(generateToken());
        invitation.setExpiresOn(expiry());
        invitationRepository.save(invitation);
        return toMail(invitation, admin);
    }

    private static InvitationMail toMail(OwnershipInvitation invitation, Ownership admin) {
        Ownership ownership = invitation.getOwnership();
        return new InvitationMail(
                ownership.getAccount().getEmail(),
                ownership.getCompany().getName(),
                ownership.getCompany().getPeppolId(),
                admin.getAccount().getName(),
                invitation.getToken());
    }

    private CompanyUserDto changeStatus(Ownership admin, Long id, OwnershipStatus from, OwnershipStatus to) {
        Ownership ownership = editableUser(admin, id);
        if (ownership.getStatus() != from) {
            throw new KycException(KycErrorCodes.USER_NOT_EDITABLE);
        }
        ownership.setStatus(to);
        return toDto(ownershipRepository.save(ownership));
    }

    private Ownership editableUser(Ownership admin, Long id) {
        Ownership ownership = ownershipRepository.findByIdAndCompanyId(id, admin.getCompany().getId())
                .filter(candidate -> LISTED_TYPES.contains(candidate.getType()))
                .orElseThrow(() -> new NotFoundException(KycErrorCodes.USER_NOT_FOUND));
        if (ownership.getType() != AccountType.USER) {
            throw new KycException(KycErrorCodes.USER_NOT_EDITABLE);
        }
        return ownership;
    }

    private OwnershipInvitation validInvitation(String token) {
        OwnershipInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new KycException(KycErrorCodes.INVITATION_NOT_FOUND));
        if (invitation.isExpired()) {
            throw new KycException(KycErrorCodes.INVITATION_EXPIRED);
        }
        return invitation;
    }

    private CompanyUserDto toDto(Ownership ownership) {
        OwnershipInvitation invitation = ownership.getStatus() == OwnershipStatus.INVITED
                ? invitationRepository.findByOwnershipId(ownership.getId()).orElse(null)
                : null;
        return CompanyUserMapper.toCompanyUserDto(ownership, invitation);
    }

    private static int validPermissionMask(int permissionMask) {
        if (!CompanyPermission.isValid(permissionMask)) {
            throw new KycException(KycErrorCodes.INVALID_PERMISSION_MASK);
        }
        return CompanyPermission.normalize(permissionMask);
    }

    private Instant expiry() {
        return Instant.now().plus(Duration.ofDays(ttlDays));
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void send(InvitationMail mail, String languageTag) {
        try {
            UserInvitationEmailTemplateProvider.RenderedTemplate template = templateProvider.render(
                    mail.companyName(), mail.inviterName(), baseUrl + mail.token(), ttlDays, languageTag);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false);
            helper.setTo(mail.to());
            helper.setFrom(fromAddress, "Let’s Peppol");
            helper.setReplyTo("support@letspeppol.org");
            helper.setSubject(template.subject());
            helper.setText(template.body(), false);
            mailSender.send(message);
            log.info("Sent user invitation email to {} for company {} lang={}", mail.to(), mail.peppolId(), languageTag);
        } catch (Exception e) {
            log.warn("Failed to send user invitation email to {} for company {} error={}", mail.to(), mail.peppolId(), e.getMessage());
            throw new KycException(KycErrorCodes.INVITATION_NOT_SENT);
        }
    }

    private record InvitationMail(String to, String companyName, String peppolId, String inviterName, String token) {}

    private record SavedInvitation(CompanyUserDto user, InvitationMail mail) {}
}
