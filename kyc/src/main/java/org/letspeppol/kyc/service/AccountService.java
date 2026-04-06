package org.letspeppol.kyc.service;

import io.micrometer.core.instrument.Counter;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.ServiceRequest;
import org.letspeppol.kyc.exception.ForbiddenException;
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
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Counter authenticationCounterFailure;

    public Account getByExternalId(UUID externalId) {
        return accountRepository.findByExternalId(externalId).orElseThrow(() -> new NotFoundException(KycErrorCodes.ACCOUNT_NOT_FOUND));
    }

    public Account findAccountWithCredentials(String emailOrUuid, String password) {
        Account account = null;
        if (!emailOrUuid.contains("@")) {
            try {
                account = accountRepository.findByExternalId(UUID.fromString(emailOrUuid)).orElse(null);
            } catch(IllegalArgumentException e) {
                log.warn("{} does not seem to be a valid UUID", emailOrUuid);
            }
        }
        if (account == null) {
            account = accountRepository.findByEmail(emailOrUuid.toLowerCase()).orElseThrow(() -> {
                authenticationCounterFailure.increment();
                return new NotFoundException(KycErrorCodes.ACCOUNT_NOT_FOUND);
            });
        }
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

    public void verifyEmailNotRegistered(String email) {
        if (accountRepository.existsByEmail(email.toLowerCase())) {
            log.warn("User tried to register with email {} but was already registered", email);
            throw new KycException(KycErrorCodes.ACCOUNT_ALREADY_LINKED);
        }
    }

    public void create(Account account) {
        accountRepository.save(account);
    }

}
