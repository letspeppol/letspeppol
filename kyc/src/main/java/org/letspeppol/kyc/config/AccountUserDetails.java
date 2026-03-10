package org.letspeppol.kyc.config;

import lombok.experimental.Delegate;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class AccountUserDetails implements UserDetails {

    private final Account account;

    public AccountUserDetails(Account account) {
        this.account = account;
    }

    public String getPeppolId() {
        return account.getCompany().getPeppolId();
    }

    public boolean isPeppolActive() {
        return account.getCompany().isPeppolActive();
    }

    public UUID getUid() {
        return account.getExternalId();
    }

    public AccountType getAccountType() {
        return account.getType();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(SecurityConfig.ROLE_KYC_USER));
    }

    @Override
    public String getPassword() {
        return account.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return account.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
