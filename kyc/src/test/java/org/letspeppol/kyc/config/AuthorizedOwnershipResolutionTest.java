package org.letspeppol.kyc.config;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.repository.OwnershipRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizedOwnershipResolutionTest {

    private static final long ACCOUNT_ID = 42L;
    private static final String PEPPOL_ID = "0208:0123456789";

    @Test
    void tokenExchangeUsesTheOwnershipFrozenInTheAuthorizationRequest() {
        OwnershipRepository repository = mock(OwnershipRepository.class);
        Ownership selected = mock(Ownership.class);
        when(repository.findFirstByAccountIdAndCompanyPeppolIdAndTypeOrderByLastUsedDesc(
                ACCOUNT_ID, PEPPOL_ID, AccountType.USER)).thenReturn(Optional.of(selected));

        Ownership result = SecurityConfig.resolveAuthorizedOwnership(
                ACCOUNT_ID, authorizationRequest(AccountType.USER), repository);

        assertThat(result).isSameAs(selected);
        verify(repository).findFirstByAccountIdAndCompanyPeppolIdAndTypeOrderByLastUsedDesc(
                ACCOUNT_ID, PEPPOL_ID, AccountType.USER);
        verify(repository, never()).findFirstByAccountIdOrderByLastUsedDesc(ACCOUNT_ID);
    }

    @Test
    void deletedOwnershipCannotProduceANewToken() {
        OwnershipRepository repository = mock(OwnershipRepository.class);
        when(repository.findFirstByAccountIdAndCompanyPeppolIdAndTypeOrderByLastUsedDesc(
                ACCOUNT_ID, PEPPOL_ID, AccountType.ADMIN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> SecurityConfig.resolveAuthorizedOwnership(
                ACCOUNT_ID, authorizationRequest(AccountType.ADMIN), repository))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("acting ownership");
    }

    private static OAuth2AuthorizationRequest authorizationRequest(AccountType accountType) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://issuer.example/oauth2/authorize")
                .clientId("letspeppol-ui")
                .redirectUri("https://app.example/callback")
                .state("state")
                .additionalParameters(Map.of(
                        ActingOwnershipAuthorizationRequestConverter.PEPPOL_ID_PARAMETER, PEPPOL_ID,
                        ActingOwnershipAuthorizationRequestConverter.ACCOUNT_TYPE_PARAMETER, accountType.name()))
                .build();
    }
}
