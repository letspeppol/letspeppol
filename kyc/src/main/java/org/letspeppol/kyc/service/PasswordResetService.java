package org.letspeppol.kyc.service;

import jakarta.mail.internet.MimeMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.ChangePasswordRequest;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.model.PasswordResetToken;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.repository.PasswordResetTokenRepository;
import org.letspeppol.kyc.repository.AccountRepository;
import org.letspeppol.kyc.service.mail.PasswordResetEmailTemplateProvider;
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
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final JavaMailSender mailSender;
    private final PasswordResetEmailTemplateProvider templateProvider;
    private final SecureRandom random = new SecureRandom();
    private final Duration ttl = Duration.ofHours(1);

    @Value("${app.mail.password-reset.base-url}")
    private String baseUrl;

    @Value("${app.mail.from:noreply@example.com}")
    private String fromAddress;

    @Transactional
    public void requestReset(String email, String acceptLanguage) {
        String langTag = LocaleUtil.extractLanguageTag(acceptLanguage);
        accountRepository.findByEmail(email.toLowerCase()).ifPresent(account -> {
            String tokenValue = generateToken();
            PasswordResetToken token = PasswordResetToken.builder()
                    .account(account)
                    .token(tokenValue)
                    .expiresOn(Instant.now().plus(ttl))
                    .build();
            tokenRepository.save(token);
            sendEmail(account.getEmail(), tokenValue, langTag);
        });
    }

    public void requestReset(String email) { requestReset(email, null); }

    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        PasswordResetToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new KycException(KycErrorCodes.PASSWORD_RESET_TOKEN_NOT_FOUND));
        if (token.isUsed()) {
            throw new KycException(KycErrorCodes.PASSWORD_RESET_TOKEN_ALREADY_USED);
        }
        if (token.isExpired()) {
            throw new KycException(KycErrorCodes.PASSWORD_RESET_TOKEN_EXPIRED);
        }
        validatePassword(newPassword);
        Account account = token.getAccount();
        accountService.updatePassword(account, newPassword);
        token.markUsed();
        tokenRepository.save(token);
    }

    public long purgeExpired() {
        List<PasswordResetToken> expired = tokenRepository.findByUsedOnIsNullAndExpiresOnBefore(Instant.now());
        long count = expired.size();
        if (count > 0) tokenRepository.deleteAll(expired);
        return count;
    }

    private void validatePassword(String pwd) {
        if (pwd == null || pwd.length() < 8) {
            throw new KycException(KycErrorCodes.INVALID_PASSWORD);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void sendEmail(String to, String tokenValue, String languageTag) {
        String link = baseUrl + tokenValue;
        try {
            PasswordResetEmailTemplateProvider.RenderedTemplate tpl = templateProvider.render(link, languageTag);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false);
            helper.setTo(to);
            helper.setFrom(fromAddress);
            helper.setSubject(tpl.subject());
            helper.setText(tpl.body(), false);
            mailSender.send(message);
            log.info("Sent password reset email to {} lang={}", to, languageTag);
        } catch (Exception e) {
            log.warn("Failed to send password reset email (logging reset link) token={} error={}", tokenValue, e.getMessage());
            log.info("Password reset link for {} -> {}", to, link);
        }
    }

    @Transactional
    public void changePassword(String uid, @Valid ChangePasswordRequest request) {
        Account account = accountRepository.findByExternalId(UUID.fromString(uid)).orElseThrow(() -> new KycException(KycErrorCodes.ACCOUNT_NOT_FOUND));
        accountService.updatePassword(account, request.password());
    }
}
