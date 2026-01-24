package org.letspeppol.kyc.service;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.exception.NotFoundException;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Counter authenticationCounterFailure;

    public Account getByExternalId(UUID externalId) {
        return accountRepository.findByExternalId(externalId).orElseThrow(() -> new NotFoundException(KycErrorCodes.ACCOUNT_NOT_FOUND));
    }

    public Account findAccountWithCredentials(String email, String password) {
        Account account = accountRepository.findByEmail(email.toLowerCase()).orElseThrow(() -> {
            authenticationCounterFailure.increment();
            return new NotFoundException(KycErrorCodes.ACCOUNT_NOT_FOUND);
        });
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            authenticationCounterFailure.increment();
            throw new KycException(KycErrorCodes.WRONG_PASSWORD);
        }
        return account;
    }

    public Account findAppAccountWithCredentials(String externalId, String password) { //TODO : why ?
        Account account = accountRepository.findByExternalId(UUID.fromString(externalId)).orElseThrow(() -> {
            authenticationCounterFailure.increment();
            return new NotFoundException(KycErrorCodes.ACCOUNT_NOT_FOUND);
        });
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            authenticationCounterFailure.increment();
            throw new KycException(KycErrorCodes.WRONG_PASSWORD);
        }
        return account;
    }

    public void updatePassword(Account account, String rawPassword) {
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        accountRepository.save(account);
    }
}
