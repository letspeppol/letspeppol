package org.letspeppol.kyc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.TotpEnableResponse;
import org.letspeppol.kyc.dto.TotpSetupResponse;
import org.letspeppol.kyc.dto.TotpStatusResponse;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

import static dev.samstevens.totp.util.Utils.getDataUriForImage;

@Slf4j
@Service
public class TotpService {

    private static final String ISSUER = "Let's Peppol";
    private static final int RECOVERY_CODE_COUNT = 8;
    private static final int RECOVERY_CODE_LENGTH = 8;
    private static final int TIME_PERIOD_SECONDS = 30;
    private static final int ALLOWED_TIME_PERIOD_DISCREPANCY = 1;

    private final AccountRepository accountRepository;
    private final EncryptionService encryptionService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1);
    private final TimeProvider timeProvider;
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public TotpService(AccountRepository accountRepository, EncryptionService encryptionService,
                       PasswordEncoder passwordEncoder, ObjectMapper objectMapper) {
        this(accountRepository, encryptionService, passwordEncoder, objectMapper, new SystemTimeProvider());
    }

    TotpService(AccountRepository accountRepository, EncryptionService encryptionService,
                PasswordEncoder passwordEncoder, ObjectMapper objectMapper, TimeProvider timeProvider) {
        this.accountRepository = accountRepository;
        this.encryptionService = encryptionService;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.timeProvider = timeProvider;
    }

    @Transactional
    public TotpSetupResponse generateSetup(UUID uid) {
        Account account = findByExternalId(uid);

        if (account.isTotpEnabled()) {
            throw new IllegalStateException("TOTP is already enabled. Disable it first before setting up again.");
        }

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

        if (!claimTimeStep(account, secret, code)) {
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

    public Account findById(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    public boolean verify(Account account, String code) {
        String secret = decryptSecret(account);
        if (secret == null) return false;
        return claimTimeStep(account, secret, code);
    }

    public boolean verifyRecoveryCode(Account account, String code) {
        String encryptedCodes = account.getTotpRecoveryCodes();
        if (encryptedCodes == null || code == null) return false;

        List<String> hashedCodes = new ArrayList<>(deserializeList(encryptionService.decrypt(encryptedCodes)));

        for (int i = 0; i < hashedCodes.size(); i++) {
            if (passwordEncoder.matches(code, hashedCodes.get(i))) {
                hashedCodes.remove(i);
                String remainingCodes = encryptionService.encrypt(serializeList(hashedCodes));
                if (accountRepository.replaceTotpRecoveryCodes(account.getId(), encryptedCodes, remainingCodes) != 1) {
                    return false;
                }
                log.info("Recovery code used for account id {}, {} codes remaining", account.getId(), hashedCodes.size());
                return true;
            }
        }
        return false;
    }

    public void disable(Account account, String code) {
        if (!account.isTotpEnabled()) {
            throw new IllegalStateException("TOTP is not enabled");
        }

        if (!verify(account, code) && !verifyRecoveryCode(account, code)) {
            throw new IllegalArgumentException("Invalid TOTP code");
        }

        accountRepository.disableTotp(account.getId());

        log.info("TOTP disabled for account {}", account.getExternalId());
    }

    public TotpStatusResponse getStatus(UUID uid) {
        Account account = findByExternalId(uid);
        return new TotpStatusResponse(account.isTotpEnabled());
    }

    public Account findByExternalId(UUID uid) {
        return accountRepository.findByExternalId(uid)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    private boolean claimTimeStep(Account account, String secret, String code) {
        OptionalLong timeStep = matchingTimeStep(secret, code);
        return timeStep.isPresent() && accountRepository.claimTotpStep(account.getId(), timeStep.getAsLong()) == 1;
    }

    private OptionalLong matchingTimeStep(String secret, String code) {
        if (secret == null || code == null) return OptionalLong.empty();
        byte[] submitted = code.getBytes(StandardCharsets.UTF_8);
        long currentStep = Math.floorDiv(timeProvider.getTime(), TIME_PERIOD_SECONDS);
        for (long step = currentStep + ALLOWED_TIME_PERIOD_DISCREPANCY; step >= currentStep - ALLOWED_TIME_PERIOD_DISCREPANCY; step--) {
            if (MessageDigest.isEqual(generateCode(secret, step).getBytes(StandardCharsets.UTF_8), submitted)) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    private String generateCode(String secret, long step) {
        try {
            return codeGenerator.generate(secret, step);
        } catch (CodeGenerationException e) {
            throw new IllegalStateException("Failed to generate TOTP code", e);
        }
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
