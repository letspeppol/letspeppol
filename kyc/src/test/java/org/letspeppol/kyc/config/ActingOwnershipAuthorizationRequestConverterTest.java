package org.letspeppol.kyc.config;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.repository.OwnershipRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActingOwnershipAuthorizationRequestConverterTest {

    private static final long ACCOUNT_ID = 42L;
    private static final String PEPPOL_ID = "0208:0123456789";

    @Test
    void explicitSelectionIsValidatedAndFrozenIntoTheAuthorizationRequest() {
        OwnershipRepository repository = mock(OwnershipRepository.class);
        Ownership selected = ownership(AccountType.USER);
        when(repository.findFirstByAccountIdAndCompanyPeppolIdAndTypeOrderByLastUsedDesc(
                ACCOUNT_ID, PEPPOL_ID, AccountType.USER)).thenReturn(Optional.of(selected));

        OAuth2AuthorizationCodeRequestAuthenticationToken result = convert(repository, Map.of(
                ActingOwnershipAuthorizationRequestConverter.PEPPOL_ID_PARAMETER, PEPPOL_ID,
                ActingOwnershipAuthorizationRequestConverter.ACCOUNT_TYPE_PARAMETER, AccountType.USER.name()));

        assertThat(result.getAdditionalParameters())
                .containsEntry(ActingOwnershipAuthorizationRequestConverter.PEPPOL_ID_PARAMETER, PEPPOL_ID)
                .containsEntry(ActingOwnershipAuthorizationRequestConverter.ACCOUNT_TYPE_PARAMETER, "USER");
        verify(repository).findFirstByAccountIdAndCompanyPeppolIdAndTypeOrderByLastUsedDesc(
                ACCOUNT_ID, PEPPOL_ID, AccountType.USER);
    }

    @Test
    void omittedSelectionUsesTheRememberedDefaultButFreezesTheResolvedOwnership() {
        OwnershipRepository repository = mock(OwnershipRepository.class);
        Ownership selected = ownership(AccountType.ADMIN);
        when(repository.findFirstByAccountIdOrderByLastUsedDesc(ACCOUNT_ID))
                .thenReturn(Optional.of(selected));

        OAuth2AuthorizationCodeRequestAuthenticationToken result = convert(repository, Map.of());

        assertThat(result.getAdditionalParameters())
                .containsEntry(ActingOwnershipAuthorizationRequestConverter.PEPPOL_ID_PARAMETER, PEPPOL_ID)
                .containsEntry(ActingOwnershipAuthorizationRequestConverter.ACCOUNT_TYPE_PARAMETER, "ADMIN");
    }

    @Test
    void peppolIdWithoutRoleResolvesACompanySpecificDefault() {
        OwnershipRepository repository = mock(OwnershipRepository.class);
        Ownership selected = ownership(AccountType.AFFILIATE);
        when(repository.findFirstByAccountIdAndCompanyPeppolIdOrderByLastUsedDesc(ACCOUNT_ID, PEPPOL_ID))
                .thenReturn(Optional.of(selected));

        OAuth2AuthorizationCodeRequestAuthenticationToken result = convert(repository, Map.of(
                ActingOwnershipAuthorizationRequestConverter.PEPPOL_ID_PARAMETER, PEPPOL_ID));

        assertThat(result.getAdditionalParameters())
                .containsEntry(ActingOwnershipAuthorizationRequestConverter.ACCOUNT_TYPE_PARAMETER, "AFFILIATE");
    }

    @Test
    void ownershipBelongingToAnotherAccountIsRejected() {
        OwnershipRepository repository = mock(OwnershipRepository.class);
        when(repository.findFirstByAccountIdAndCompanyPeppolIdAndTypeOrderByLastUsedDesc(
                ACCOUNT_ID, PEPPOL_ID, AccountType.USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> convert(repository, Map.of(
                ActingOwnershipAuthorizationRequestConverter.PEPPOL_ID_PARAMETER, PEPPOL_ID,
                ActingOwnershipAuthorizationRequestConverter.ACCOUNT_TYPE_PARAMETER, "USER")))
                .isInstanceOf(OAuth2AuthorizationCodeRequestAuthenticationException.class);
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationToken convert(
            OwnershipRepository repository,
            Map<String, Object> additionalParameters) {
        AccountUserDetails user = new AccountUserDetails(
                "person@example.com", "password", UUID.randomUUID(), false, ACCOUNT_ID, false, true);
        UsernamePasswordAuthenticationToken principal = UsernamePasswordAuthenticationToken.authenticated(
                user, "password", user.getAuthorities());
        OAuth2AuthorizationCodeRequestAuthenticationToken request =
                new OAuth2AuthorizationCodeRequestAuthenticationToken(
                        "https://issuer.example/oauth2/authorize",
                        "letspeppol-ui",
                        principal,
                        "https://app.example/callback",
                        "state",
                        Set.of("openid"),
                        additionalParameters);
        AuthenticationConverter delegate = ignored -> request;
        return (OAuth2AuthorizationCodeRequestAuthenticationToken)
                new ActingOwnershipAuthorizationRequestConverter(repository, delegate)
                        .convert(mock(HttpServletRequest.class));
    }

    private static Ownership ownership(AccountType type) {
        Company company = mock(Company.class);
        when(company.getPeppolId()).thenReturn(PEPPOL_ID);
        Ownership ownership = mock(Ownership.class);
        when(ownership.getCompany()).thenReturn(company);
        when(ownership.getType()).thenReturn(type);
        return ownership;
    }
}
