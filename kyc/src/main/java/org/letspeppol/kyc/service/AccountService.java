package org.letspeppol.kyc.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.exception.NotFoundException;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AccountService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public Account getByExternalId(UUID externalId) {
        return accountRepository.findByExternalId(externalId).orElseThrow(() -> new NotFoundException(KycErrorCodes.ACCOUNT_NOT_FOUND));
    }

    public Optional<Account> findByEmail(String email) {
        return accountRepository.findByEmail(email.toLowerCase());
    }

    public void updatePassword(Account account, String rawPassword) {
        validatePasswordStrength(rawPassword);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        accountRepository.save(account);
    }

    public void validatePasswordStrength(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new KycException(KycErrorCodes.INVALID_PASSWORD);
        }
    }

    public void verify(Account account) {
        account.setVerified(true);
        account.setVerifiedOn(java.time.Instant.now());
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

    public Account createPendingAccount(String email, String name) {
        Account account = new Account();
        account.setName(name);
        account.setEmail(email.toLowerCase());
        account.setVerified(false);
        account.setCreatedOn(Instant.now());
        account.setPasswordHash(null);
        accountRepository.save(account);
        return account;
    }

}
