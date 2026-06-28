package org.letspeppol.kyc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.repository.AccountRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TotpServiceTest {

    private AccountRepository accountRepository;
    private EncryptionService encryptionService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private TotpService service;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        encryptionService = mock(EncryptionService.class);
        // Identity encryption keeps the test focused on TOTP/recovery logic, not crypto.
        when(encryptionService.encrypt(anyString())).thenAnswer(i -> i.getArgument(0));
        when(encryptionService.decrypt(anyString())).thenAnswer(i -> i.getArgument(0));
        service = new TotpService(accountRepository, encryptionService, passwordEncoder, objectMapper);
    }

    @Test
    void verifyAcceptsCurrentCodeAndRejectsWrongCode() throws Exception {
        String secret = new DefaultSecretGenerator().generate();
        Account account = Account.builder().totpSecret(secret).build();

        assertThat(service.verify(account, currentCode(secret))).isTrue();
        assertThat(service.verify(account, "000000")).isFalse();
    }

    @Test
    void verifyReturnsFalseWhenNoSecret() {
        Account account = Account.builder().build();
        assertThat(service.verify(account, "123456")).isFalse();
    }

    @Test
    void recoveryCodeIsAcceptedOnceThenConsumed() throws Exception {
        String plain = "ABCD1234";
        String json = objectMapper.writeValueAsString(List.of(passwordEncoder.encode(plain)));
        Account account = Account.builder().totpRecoveryCodes(json).build();

        assertThat(service.verifyRecoveryCode(account, plain)).isTrue();
        verify(accountRepository, times(1)).save(account);

        // Already consumed -> second use fails.
        assertThat(service.verifyRecoveryCode(account, plain)).isFalse();
    }

    @Test
    void recoveryCodeRejectsUnknownCode() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(passwordEncoder.encode("REALCODE")));
        Account account = Account.builder().totpRecoveryCodes(json).build();

        assertThat(service.verifyRecoveryCode(account, "WRONGCODE")).isFalse();
        verify(accountRepository, never()).save(account);
    }

    @Test
    void recoveryCodeReturnsFalseWhenNoneStored() {
        Account account = Account.builder().build();
        assertThat(service.verifyRecoveryCode(account, "anything")).isFalse();
    }

    @Test
    void generateSetupRejectedWhenAlreadyEnabled() {
        UUID uid = UUID.randomUUID();
        Account enabled = Account.builder().totpEnabled(true).build();
        when(accountRepository.findByExternalId(uid)).thenReturn(Optional.of(enabled));

        assertThatThrownBy(() -> service.generateSetup(uid))
                .isInstanceOf(IllegalStateException.class);
    }

    private String currentCode(String secret) throws Exception {
        TimeProvider timeProvider = new SystemTimeProvider();
        CodeGenerator codeGenerator = new DefaultCodeGenerator();
        long counter = Math.floorDiv(timeProvider.getTime(), 30);
        return codeGenerator.generate(secret, counter);
    }
}
