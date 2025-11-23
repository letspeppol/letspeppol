package org.letspeppol.kyc.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.ConfirmCompanyRequest;
import org.letspeppol.kyc.dto.TokenVerificationResponse;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.exception.NotFoundException;
import org.letspeppol.kyc.model.EmailVerification;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivationService {

    private final EmailVerificationRepository verificationRepository;
    private final JavaMailSender mailSender;
    private final CompanyService companyService;
    private final ActivationEmailTemplateProvider templateProvider;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl = Duration.ofHours(2);

    @Value("${app.mail.activation.base-url}")
    private String baseUrl;

    @Value("${app.mail.from:noreply@example.com}")
    private String fromAddress;

    @Transactional
    public void requestActivation(ConfirmCompanyRequest request, String acceptLanguage) {
        if (isVerified(request.peppolId())) {
            log.warn("User with email {} tried to register for company {} but company was already registered", request.email(), request.peppolId());
            throw new KycException(KycErrorCodes.COMPANY_ALREADY_REGISTERED);
        }
        String token = generateToken();
        EmailVerification verification = new EmailVerification(
                request.email().toLowerCase(),
                request.peppolId(),
                token,
                Instant.now().plus(ttl)
        );
        verificationRepository.save(verification);
        String langTag = LocaleUtil.extractLanguageTag(acceptLanguage);
        sendEmail(request.peppolId(), request.email(), token, langTag);
    }

    // Backwards compatibility
    public void requestActivation(ConfirmCompanyRequest request) { requestActivation(request, null); }

    @Transactional
    public TokenVerificationResponse verify(String token) {
        EmailVerification verification = verificationRepository.findByToken(token)
            .orElseThrow(() -> new KycException(KycErrorCodes.TOKEN_NOT_FOUND));
        if (verification.isVerified()) {
            throw new KycException(KycErrorCodes.TOKEN_ALREADY_VERIFIED);
        }
        if (verification.getExpiresOn().isBefore(Instant.now())) {
            throw new KycException(KycErrorCodes.TOKEN_EXPIRED);
        }
        return new TokenVerificationResponse(
            verification.getEmail(),
            companyService.getByPeppolId(verification.getPeppolId())
                .orElseThrow(() -> new NotFoundException(KycErrorCodes.COMPANY_NOT_FOUND))
        );
    }

    public void setVerified(String token) {
        EmailVerification verification = verificationRepository.findByToken(token)
            .orElseThrow(() -> new KycException(KycErrorCodes.TOKEN_NOT_FOUND));
        verification.setVerified(true);
        verificationRepository.save(verification);
    }

    public boolean isVerified(String peppolId) {
        return verificationRepository.findTopByPeppolIdOrderByCreatedOnDesc(peppolId)
                .map(EmailVerification::isVerified)
                .orElse(false);
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
        log.info("Sending activation email to {} for company {} lang={} ", to, peppolId, languageTag);
        String activationLink = baseUrl + token;
        try {
            ActivationEmailTemplateProvider.RenderedTemplate tpl = templateProvider.render(peppolId, activationLink, languageTag);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false);
            helper.setTo(to);
            helper.setFrom(fromAddress);
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
