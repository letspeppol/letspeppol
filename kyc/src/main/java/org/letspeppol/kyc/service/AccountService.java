package org.letspeppol.kyc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.exception.NotFoundException;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public Account findAccountWithCredentials(String email, String password) {
        Account account = accountRepository.findByEmail(email).orElseThrow(() -> new NotFoundException(KycErrorCodes.ACCOUNT_NOT_FOUND));
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            throw new KycException(KycErrorCodes.WRONG_PASSWORD);
        }
        return account;
    }

    public void updatePassword(Account account, String rawPassword) {
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        accountRepository.save(account);
    }
}
