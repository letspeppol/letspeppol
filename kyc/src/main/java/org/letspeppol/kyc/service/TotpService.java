package org.letspeppol.kyc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.TotpEnableResponse;
import org.letspeppol.kyc.dto.TotpSetupResponse;
import org.letspeppol.kyc.dto.TotpStatusResponse;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

@Slf4j
@Service
public class TotpService {

    private static final String ISSUER = "Let's Peppol";
    private static final int RECOVERY_CODE_COUNT = 8;
    private static final int RECOVERY_CODE_LENGTH = 8;

    private final AccountRepository accountRepository;
    private final EncryptionService encryptionService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeVerifier codeVerifier;
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final SecureRandom secureRandom = new SecureRandom();

    public TotpService(AccountRepository accountRepository, EncryptionService encryptionService,
                       PasswordEncoder passwordEncoder, ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.encryptionService = encryptionService;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.codeVerifier = new DefaultCodeVerifier(
                new DefaultCodeGenerator(HashingAlgorithm.SHA1),
                new SystemTimeProvider()
        );
        ((DefaultCodeVerifier) this.codeVerifier).setAllowedTimePeriodDiscrepancy(1);
    }

    @Transactional
    public TotpSetupResponse generateSetup(UUID uid) {
        Account account = findByExternalId(uid);
        String secret = secretGenerator.generate();

        // Store encrypted secret but keep TOTP disabled until verified
        account.setTotpSecret(encryptionService.encrypt(secret));
        account.setTotpEnabled(false);
        account.setTotpRecoveryCodes(null);
        accountRepository.save(account);

        QrData qrData = new QrData.Builder()
                .label(account.getEmail())
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        try {
            String dataUri = getDataUriForImage(qrGenerator.generate(qrData), qrGenerator.getImageMimeType());
            return new TotpSetupResponse(secret, dataUri);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate QR code", e);
        }
    }

    @Transactional
    public TotpEnableResponse verifyAndEnable(UUID uid, String code) {
        Account account = findByExternalId(uid);
        String secret = decryptSecret(account);

        if (secret == null) {
            throw new IllegalStateException("TOTP setup not initiated");
        }

        if (!codeVerifier.isValidCode(secret, code)) {
            throw new IllegalArgumentException("Invalid TOTP code");
        }

        List<String> recoveryCodes = generateRecoveryCodes();
        List<String> hashedCodes = recoveryCodes.stream()
                .map(passwordEncoder::encode)
                .toList();

        account.setTotpEnabled(true);
        account.setTotpRecoveryCodes(encryptionService.encrypt(serializeList(hashedCodes)));
        accountRepository.save(account);

        log.info("TOTP enabled for account {}", uid);
        return new TotpEnableResponse(recoveryCodes);
    }

    public boolean verify(Long accountId, String code) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        String secret = decryptSecret(account);
        if (secret == null) return false;
        return codeVerifier.isValidCode(secret, code);
    }

    @Transactional
    public boolean verifyRecoveryCode(Long accountId, String code) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        String encryptedCodes = account.getTotpRecoveryCodes();
        if (encryptedCodes == null) return false;

        List<String> hashedCodes = new ArrayList<>(deserializeList(encryptionService.decrypt(encryptedCodes)));

        for (int i = 0; i < hashedCodes.size(); i++) {
            if (passwordEncoder.matches(code, hashedCodes.get(i))) {
                hashedCodes.remove(i);
                account.setTotpRecoveryCodes(encryptionService.encrypt(serializeList(hashedCodes)));
                accountRepository.save(account);
                log.info("Recovery code used for account id {}, {} codes remaining", accountId, hashedCodes.size());
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void disable(UUID uid, String code) {
        Account account = findByExternalId(uid);

        if (!account.isTotpEnabled()) {
            throw new IllegalStateException("TOTP is not enabled");
        }

        String secret = decryptSecret(account);
        if (!codeVerifier.isValidCode(secret, code)) {
            throw new IllegalArgumentException("Invalid TOTP code");
        }

        account.setTotpSecret(null);
        account.setTotpEnabled(false);
        account.setTotpRecoveryCodes(null);
        accountRepository.save(account);

        log.info("TOTP disabled for account {}", uid);
    }

    public TotpStatusResponse getStatus(UUID uid) {
        Account account = findByExternalId(uid);
        return new TotpStatusResponse(account.isTotpEnabled());
    }

    private Account findByExternalId(UUID uid) {
        return accountRepository.findByExternalId(uid)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    private String decryptSecret(Account account) {
        String encrypted = account.getTotpSecret();
        if (encrypted == null) return null;
        return encryptionService.decrypt(encrypted);
    }

    private List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>();
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder sb = new StringBuilder(RECOVERY_CODE_LENGTH);
            for (int j = 0; j < RECOVERY_CODE_LENGTH; j++) {
                sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    private String serializeList(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize", e);
        }
    }

    private List<String> deserializeList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize", e);
        }
    }
}
