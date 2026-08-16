package org.letspeppol.kyc.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.letspeppol.kyc.model.Account;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Authenticated KYC principal.
 *
 * <p>Deliberately carries no company/ownership context: an account can own several companies
 * (and hold several roles). The acting ownership is validated from the authorization request and
 * stored with its code (see {@code SecurityConfig#tokenCustomizer}). Freezing an ownership into the
 * browser-session principal would make independent tab contexts impossible.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountUserDetails implements UserDetails {

    private final String username;
    private final String password;
    private final UUID uid;
    private final boolean totpEnabled;
    private final Long accountId;
    private final boolean locked;
    private final boolean verified;

    public AccountUserDetails(Account account) {
        this(account, false);
    }

    public AccountUserDetails(Account account, boolean locked) {
        this.username = account.getEmail();
        this.password = account.getPasswordHash();
        this.uid = account.getExternalId();
        this.totpEnabled = account.isTotpEnabled();
        this.accountId = account.getId();
        this.locked = locked;
        this.verified = account.isVerified();
    }

    @JsonCreator
    public AccountUserDetails(
            @JsonProperty("username") String username,
            @JsonProperty("password") String password,
            @JsonProperty("uid") UUID uid,
            @JsonProperty("totpEnabled") boolean totpEnabled,
            @JsonProperty("accountId") Long accountId,
            @JsonProperty("locked") boolean locked,
            @JsonProperty("verified") boolean verified) {
        this.username = username;
        this.password = password;
        this.uid = uid;
        this.totpEnabled = totpEnabled;
        this.accountId = accountId;
        this.locked = locked;
        this.verified = verified;
    }

    public UUID getUid() {
        return uid;
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
        return !locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** An account that has not completed email verification cannot sign in. */
    @Override
    public boolean isEnabled() {
        return verified;
    }

    /**
     * Spring Authorization Server stores the principal in the JDBC authorization and
     * deserializes it again when the authorization code is exchanged.  The OIDC session
     * registry, however, is keyed by the principal from the live HTTP session.  Treat both
     * representations of the same account as the same principal so the ID token receives
     * the session's {@code sid} claim and can subsequently be used for RP-initiated logout.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof AccountUserDetails that && Objects.equals(uid, that.uid);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uid);
    }
}
