package org.letspeppol.kyc.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountUserDetails implements UserDetails {

    private final String username;
    private final String password;
    private final String peppolId;
    private final boolean peppolActive;
    private final UUID uid;
    private final AccountType accountType;
    private final boolean totpEnabled;
    private final Long accountId;

    public AccountUserDetails(Account account) {
        this.username = account.getEmail();
        this.password = account.getPasswordHash();
        this.peppolId = account.getCompany().getPeppolId();
        this.peppolActive = account.getCompany().isPeppolActive();
        this.uid = account.getExternalId();
        this.accountType = account.getType();
        this.totpEnabled = account.isTotpEnabled();
        this.accountId = account.getId();
    }

    @JsonCreator
    public AccountUserDetails(
            @JsonProperty("username") String username,
            @JsonProperty("password") String password,
            @JsonProperty("peppolId") String peppolId,
            @JsonProperty("peppolActive") boolean peppolActive,
            @JsonProperty("uid") UUID uid,
            @JsonProperty("accountType") AccountType accountType,
            @JsonProperty("totpEnabled") boolean totpEnabled,
            @JsonProperty("accountId") Long accountId) {
        this.username = username;
        this.password = password;
        this.peppolId = peppolId;
        this.peppolActive = peppolActive;
        this.uid = uid;
        this.accountType = accountType;
        this.totpEnabled = totpEnabled;
        this.accountId = accountId;
    }

    public String getPeppolId() {
        return peppolId;
    }

    public boolean isPeppolActive() {
        return peppolActive;
    }

    public UUID getUid() {
        return uid;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public boolean isTotpEnabled() {
        return totpEnabled;
    }

    public Long getAccountId() {
        return accountId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(SecurityConfig.ROLE_KYC_USER));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
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
