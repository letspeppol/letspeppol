package org.letspeppol.kyc.config;

import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.repository.AccountRepository;
import org.letspeppol.kyc.service.LoginAttemptService;
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
    private final LoginAttemptService loginAttemptService;

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

        boolean locked = loginAttemptService.isBlocked(loginKey(username));
        return account
                .map(a -> new AccountUserDetails(a, locked))
                .orElseThrow(() -> new UsernameNotFoundException("Account not found: " + username));
    }

    /** Throttle key used for form-login attempts; matches LoginAttemptEventListener. */
    public static String loginKey(String username) {
        return "login:" + username.toLowerCase();
    }
}
