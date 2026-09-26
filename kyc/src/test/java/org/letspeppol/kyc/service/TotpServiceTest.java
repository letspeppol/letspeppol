package org.letspeppol.kyc.service;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.repository.AccountRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TotpServiceTest {

    private static final long NOW_SECONDS = 1_780_000_000L;
    private static final long CURRENT_STEP = NOW_SECONDS / 30;
    private static final long ACCOUNT_ID = 7L;

    private AccountRepository accountRepository;
    private EncryptionService encryptionService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong lastUsedStep = new AtomicLong(Long.MIN_VALUE);
    private final AtomicReference<String> storedRecoveryCodes = new AtomicReference<>();
    private TotpService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        encryptionService = mock(EncryptionService.class);
        // Identity encryption keeps the test focused on TOTP/recovery logic, not crypto.
        when(encryptionService.encrypt(anyString())).thenAnswer(i -> i.getArgument(0));
        when(encryptionService.decrypt(anyString())).thenAnswer(i -> i.getArgument(0));
        when(accountRepository.claimTotpStep(eq(ACCOUNT_ID), anyLong())).thenAnswer(i -> {
            long step = i.getArgument(1);
            return lastUsedStep.getAndUpdate(last -> Math.max(last, step)) < step ? 1 : 0;
        });
        when(accountRepository.replaceTotpRecoveryCodes(eq(ACCOUNT_ID), anyString(), anyString())).thenAnswer(i -> {
            if (!i.getArgument(1).equals(storedRecoveryCodes.get())) {
                return 0;
            }
            storedRecoveryCodes.set(i.getArgument(2));
            return 1;
        });
        service = new TotpService(accountRepository, encryptionService, passwordEncoder, objectMapper, () -> NOW_SECONDS);
    }

    @Test
    void verifyAcceptsCurrentCodeAndRejectsWrongCode() throws Exception {
        String secret = new DefaultSecretGenerator().generate();
        Account account = Account.builder().id(ACCOUNT_ID).totpSecret(secret).build();

        assertThat(service.verify(account, "000000".equals(code(secret, CURRENT_STEP)) ? "111111" : "000000")).isFalse();
        assertThat(service.verify(account, code(secret, CURRENT_STEP))).isTrue();
    }

    @Test
    void codeCannotBeReusedWithinItsValidityWindow() throws Exception {
        String secret = new DefaultSecretGenerator().generate();
        Account account = Account.builder().id(ACCOUNT_ID).totpSecret(secret).build();
        String code = code(secret, CURRENT_STEP);

        assertThat(service.verify(account, code)).isTrue();
        assertThat(service.verify(account, code)).isFalse();
    }

    @Test
    void olderCodeIsRejectedOnceANewerStepWasUsed() throws Exception {
        String secret = new DefaultSecretGenerator().generate();
        Account account = Account.builder().id(ACCOUNT_ID).totpSecret(secret).build();

        assertThat(service.verify(account, code(secret, CURRENT_STEP))).isTrue();
        assertThat(service.verify(account, code(secret, CURRENT_STEP - 1))).isFalse();
    }

    @Test
    void codesOutsideTheAllowedDiscrepancyAreRejected() throws Exception {
        String secret = new DefaultSecretGenerator().generate();
        Account account = Account.builder().id(ACCOUNT_ID).totpSecret(secret).build();

        assertThat(service.verify(account, code(secret, CURRENT_STEP - 2))).isFalse();
        assertThat(service.verify(account, code(secret, CURRENT_STEP - 1))).isTrue();
    }

    @Test
    void verifyReturnsFalseWhenNoSecret() {
        Account account = Account.builder().id(ACCOUNT_ID).build();
        assertThat(service.verify(account, "123456")).isFalse();
    }

    @Test
    void recoveryCodeIsAcceptedOnceThenConsumed() throws Exception {
        String plain = "ABCD1234";
        Account account = accountWithRecoveryCode(plain);

        assertThat(service.verifyRecoveryCode(account, plain)).isTrue();

        account.setTotpRecoveryCodes(storedRecoveryCodes.get());
        assertThat(service.verifyRecoveryCode(account, plain)).isFalse();
    }

    @Test
    void recoveryCodeIsAcceptedOnceAcrossCopiesLoadedBeforeItWasUsed() throws Exception {
        String plain = "ABCD1234";
        Account first = accountWithRecoveryCode(plain);
        Account second = Account.builder().id(ACCOUNT_ID).totpRecoveryCodes(first.getTotpRecoveryCodes()).build();

        assertThat(service.verifyRecoveryCode(first, plain)).isTrue();
        assertThat(service.verifyRecoveryCode(second, plain)).isFalse();
    }

    @Test
    void recoveryCodeRejectsUnknownCode() throws Exception {
        Account account = accountWithRecoveryCode("REALCODE");

        assertThat(service.verifyRecoveryCode(account, "WRONGCODE")).isFalse();
        verify(accountRepository, never()).replaceTotpRecoveryCodes(anyLong(), anyString(), anyString());
    }

    @Test
    void recoveryCodeReturnsFalseWhenNoneStored() {
        assertThat(service.verifyRecoveryCode(Account.builder().id(ACCOUNT_ID).build(), "anything")).isFalse();
    }

    @Test
    void recoveryCodeDisablesTotpSoALostAuthenticatorCanBeReplaced() throws Exception {
        Account account = accountWithRecoveryCode("ABCD1234");
        account.setTotpEnabled(true);
        account.setTotpSecret(new DefaultSecretGenerator().generate());

        service.disable(account, "ABCD1234");

        verify(accountRepository).disableTotp(ACCOUNT_ID);
    }

    @Test
    void disableRejectsAnInvalidCode() throws Exception {
        Account account = accountWithRecoveryCode("ABCD1234");
        account.setTotpEnabled(true);
        account.setTotpSecret(new DefaultSecretGenerator().generate());

        assertThatThrownBy(() -> service.disable(account, "WRONGCODE"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(accountRepository, never()).disableTotp(anyLong());
    }

    @Test
    void generateSetupRejectedWhenAlreadyEnabled() {
        UUID uid = UUID.randomUUID();
        Account enabled = Account.builder().totpEnabled(true).build();
        when(accountRepository.findByExternalId(uid)).thenReturn(Optional.of(enabled));

        assertThatThrownBy(() -> service.generateSetup(uid))
                .isInstanceOf(IllegalStateException.class);
    }

    private Account accountWithRecoveryCode(String plain) throws Exception {
        String json = objectMapper.writeValueAsString(List.of(passwordEncoder.encode(plain)));
        storedRecoveryCodes.set(json);
        return Account.builder().id(ACCOUNT_ID).totpRecoveryCodes(json).build();
    }

    private static String code(String secret, long step) throws Exception {
        return new DefaultCodeGenerator().generate(secret, step);
    }
}
