package org.letspeppol.kyc.config;

import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.repository.AccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<Account> account = Optional.empty();

        if (!username.contains("@")) {
            try {
                account = accountRepository.findByExternalId(UUID.fromString(username));
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (account.isEmpty()) {
            account = accountRepository.findByEmail(username.toLowerCase());
        }

        return account
                .map(AccountUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found: " + username));
    }
}
