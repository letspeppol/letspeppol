package org.letspeppol.kyc;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.repository.AccountRepository;
import org.letspeppol.kyc.service.EncryptionService;
import org.letspeppol.kyc.service.TotpService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TotpVerificationTests {

    private static final String RECOVERY_CODE = "ABCD1234";

    @Autowired
    TotpService totpService;
    @Autowired
    AccountRepository accountRepository;
    @Autowired
    EncryptionService encryptionService;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void acceptedCodeCannotBeReplayed() throws Exception {
        String secret = new DefaultSecretGenerator().generate();
        Account account = accountWithTotp(secret);
        String code = currentCode(secret);

        assertThat(totpService.verify(account, code)).isTrue();
        assertThat(totpService.verify(account, code)).isFalse();
    }

    @Test
    void recoveryCodeIsConsumedOnceAcrossCopiesLoadedBeforeItWasUsed() throws Exception {
        Account account = accountWithTotp(new DefaultSecretGenerator().generate());
        Account first = accountRepository.findById(account.getId()).orElseThrow();
        Account second = accountRepository.findById(account.getId()).orElseThrow();

        assertThat(totpService.verifyRecoveryCode(first, RECOVERY_CODE)).isTrue();
        assertThat(totpService.verifyRecoveryCode(second, RECOVERY_CODE)).isFalse();
    }

    @Test
    void disableClearsTheStoredTotpState() throws Exception {
        String secret = new DefaultSecretGenerator().generate();
        Account account = accountWithTotp(secret);

        totpService.disable(account, currentCode(secret));

        Account disabled = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(disabled.isTotpEnabled()).isFalse();
        assertThat(disabled.getTotpSecret()).isNull();
        assertThat(disabled.getTotpRecoveryCodes()).isNull();
    }

    private Account accountWithTotp(String secret) throws Exception {
        String recoveryCodes = objectMapper.writeValueAsString(List.of(passwordEncoder.encode(RECOVERY_CODE)));
        return accountRepository.save(Account.builder()
                .name("Totp Person")
                .email("totp-" + UUID.randomUUID() + "@example.com")
                .totpSecret(encryptionService.encrypt(secret))
                .totpEnabled(true)
                .totpRecoveryCodes(encryptionService.encrypt(recoveryCodes))
                .build());
    }

    private static String currentCode(String secret) throws Exception {
        return new DefaultCodeGenerator().generate(secret, Instant.now().getEpochSecond() / 30);
    }
}
