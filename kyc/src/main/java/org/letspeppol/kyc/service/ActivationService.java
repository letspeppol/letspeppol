package org.letspeppol.kyc.service;

import io.micrometer.core.instrument.Counter;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.CompanyResponse;
import org.letspeppol.kyc.dto.ConfirmCompanyRequest;
import org.letspeppol.kyc.dto.OwnershipInfo;
import org.letspeppol.kyc.dto.TokenVerificationResponse;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.exception.NotFoundException;
import org.letspeppol.kyc.mapper.OwnershipMapper;
import org.letspeppol.kyc.model.EmailVerification;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.repository.AccountIdentityVerificationRepository;
import org.letspeppol.kyc.repository.EmailVerificationRepository;
import org.letspeppol.kyc.service.mail.ActivationEmailTemplateProvider;
import org.letspeppol.kyc.util.LocaleUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.letspeppol.kyc.model.AccountType.ADMIN;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivationService {

    private final EmailVerificationRepository verificationRepository;
    private final JavaMailSender mailSender;
    private final CompanyService companyService;
    private final AccountService accountService;
    private final OwnershipService ownershipService;
    private final AccountIdentityVerificationRepository directorIdentityVerificationRepository;
    private final PasswordResetService passwordResetService;
    private final ActivationEmailTemplateProvider templateProvider;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl = Duration.ofDays(7);
    private final Counter activationRequestedCounter;
    private final Counter tokenVerificationCounter;

    @Value("${app.mail.activation.base-url}")
    private String baseUrl;

    @Value("${app.mail.from:noreply@example.com}")
    private String fromAddress;

    @Transactional
    public void requestActivation(ConfirmCompanyRequest request, String acceptLanguage) {
        ownershipService.verifyPeppolIdNotRegistered(request.peppolId()); //Even for not ADMIN registration, if already registered, we need to use the ADMIN account
        requestActivation(null, request, acceptLanguage);
    }

    @Transactional
    public void requestActivation(Ownership requester, ConfirmCompanyRequest request, String acceptLanguage) {
        String token = generateToken();
        EmailVerification verification = new EmailVerification(
                requester,
                request.type() == null ? ADMIN : request.type(),
                request.email().toLowerCase(),
                request.peppolId(), //Always add peppolId to set the targeted company
                token,
                Instant.now().plus(ttl)
        );
        verificationRepository.save(verification);
        String langTag = LocaleUtil.extractLanguageTag(acceptLanguage);
        sendEmail(request.peppolId(), request.email(), token, langTag);
        activationRequestedCounter.increment();
    }

    @Transactional
    public EmailVerification getValidTokenInformation(String token) {
        EmailVerification emailVerification = verificationRepository.findByToken(token)
                .orElseThrow(() -> new KycException(KycErrorCodes.TOKEN_NOT_FOUND));
        if (emailVerification.getExpiresOn().isBefore(Instant.now())) {
            throw new KycException(KycErrorCodes.TOKEN_EXPIRED);
        }
        //accountService.verifyEmailNotRegistered(emailVerification.getEmail()); //TODO : remove due to double accounts possible
        return emailVerification;
    }

    @Transactional
    public EmailVerification getPendingVerification(String email, String peppolId) {
        return verificationRepository.findFirstByEmailIgnoreCaseAndPeppolIdAndVerifiedFalseAndExpiresOnAfterOrderByCreatedOnDesc(
                        email,
                        peppolId,
                        Instant.now()
                )
                .orElseThrow(() -> new KycException(KycErrorCodes.TOKEN_NOT_FOUND));
    }

    @Transactional
    public TokenVerificationResponse verify(String token) {
        EmailVerification emailVerification = getValidTokenInformation(token);
        CompanyResponse companyResponse = companyService.getResponseByPeppolId(emailVerification.getPeppolId())
                .orElseThrow(() -> new NotFoundException(KycErrorCodes.COMPANY_NOT_FOUND));
        var account = accountService.findByEmail(emailVerification.getEmail());
        boolean accountExists = account.isPresent();
        boolean accountVerified = account.map(org.letspeppol.kyc.model.Account::isVerified).orElse(false);
        boolean directorSigned = account
                .map(existingAccount -> directorIdentityVerificationRepository.existsByAccountIdAndDirectorCompanyPeppolId(existingAccount.getId(), emailVerification.getPeppolId()))
                .orElse(false);
        OwnershipInfo requester = emailVerification.getRequester() == null ? null : OwnershipMapper.toOwnershipInfo(emailVerification.getRequester());
        return new TokenVerificationResponse(
                emailVerification.getEmail(),
                accountExists,
                accountVerified,
                directorSigned,
                emailVerification.getType(),
                companyResponse,
                requester
        );
    }

    @Transactional
    public EmailVerification verifyAccount(String token, String newPassword) {
        EmailVerification emailVerification = getValidTokenInformation(token);
        Ownership ownership = ownershipService.getByAccountEmailAndPeppolIdAndType(emailVerification.getEmail(), emailVerification.getPeppolId(), AccountType.ADMIN);
        if (!directorIdentityVerificationRepository.existsByAccountId(ownership.getAccount().getId())) {
            throw new KycException(KycErrorCodes.ACCOUNT_NOT_VERIFIED);
        }
        if (!ownership.getAccount().isVerified()) {
            passwordResetService.setPassword(ownership.getAccount(), newPassword);
            accountService.verify(ownership.getAccount());
        }
        tokenVerificationCounter.increment();
        return emailVerification;
    }

    @Transactional
    public void linkAffiliateOwnership(EmailVerification emailVerification) {
        Ownership ownership = ownershipService.getByAccountEmailAndPeppolIdAndType(emailVerification.getEmail(), emailVerification.getPeppolId(), AccountType.ADMIN);
        ownershipService.ensureOwnership(ownership.getAccount(), AccountType.AFFILIATE, ownership.getCompany());
    }

    @Transactional
    public long purgeExpired() {
        List<EmailVerification> expired = verificationRepository.findByVerifiedFalseAndExpiresOnBefore(Instant.now());
        long count = expired.size();
        if (count > 0) {
            verificationRepository.deleteAll(expired);
        }
        return count;
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void sendEmail(String peppolId, String to, String token, String languageTag) {
        log.info("Sending activation email to {} for company {} lang={}", to, peppolId, languageTag);
        String activationLink = baseUrl + token;
        try {
            ActivationEmailTemplateProvider.RenderedTemplate tpl = templateProvider.render(peppolId, activationLink, languageTag);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false);
            helper.setTo(to);
            helper.setFrom(fromAddress, "Let’s Peppol");
            helper.setBcc("kyc@letspeppol.org");
            helper.setReplyTo("support@letspeppol.org");
            helper.setSubject(tpl.subject());
            helper.setText(tpl.body(), false);
            mailSender.send(message);
            log.info("Sent activation email to {} for company {} lang={} ", to, peppolId, languageTag);
        } catch (Exception e) {
            log.warn("Failed to send email (logging activation link) token={} error={}", token, e.getMessage());
            log.info("Activation link for {} -> {}", to, activationLink);
        }
    }

    private void sendEmail(String peppolId, String to, String token) { sendEmail(peppolId, to, token, null); }
}
