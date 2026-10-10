package org.letspeppol.kyc.service;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.repository.AccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AccountServiceTest {

    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final AccountService accountService = new AccountService(accountRepository, mock(PasswordEncoder.class));

    @Test
    void accountThatWasNeverVerifiedTakesTheNewName() {
        Account invited = Account.builder().name("Typed By Admin").email("jan@example.com").verified(false).build();

        assertThat(accountService.renameUnlessVerified(invited, "Jan Peeters")).isSameAs(invited);

        assertThat(invited.getName()).isEqualTo("Jan Peeters");
        verify(accountRepository).save(invited);
    }

    @Test
    void verifiedAccountKeepsItsName() {
        Account verified = Account.builder().name("Jan Peeters").email("jan@example.com").verified(true).build();

        assertThat(accountService.renameUnlessVerified(verified, "Somebody Else")).isSameAs(verified);

        assertThat(verified.getName()).isEqualTo("Jan Peeters");
        verify(accountRepository, never()).save(verified);
    }
}
