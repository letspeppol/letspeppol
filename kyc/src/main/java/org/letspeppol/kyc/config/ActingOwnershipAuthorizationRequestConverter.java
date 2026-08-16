package org.letspeppol.kyc.config;

import jakarta.servlet.http.HttpServletRequest;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.repository.OwnershipRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeRequestAuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationConverter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves and freezes the acting ownership while an authorization request is accepted.
 */
public class ActingOwnershipAuthorizationRequestConverter implements AuthenticationConverter {

    public static final String PEPPOL_ID_PARAMETER = "peppol_id";
    public static final String ACCOUNT_TYPE_PARAMETER = "account_type";

    private final OwnershipRepository ownershipRepository;
    private final AuthenticationConverter delegate;

    public ActingOwnershipAuthorizationRequestConverter(OwnershipRepository ownershipRepository) {
        this(ownershipRepository, new OAuth2AuthorizationCodeRequestAuthenticationConverter());
    }

    ActingOwnershipAuthorizationRequestConverter(
            OwnershipRepository ownershipRepository,
            AuthenticationConverter delegate) {
        this.ownershipRepository = ownershipRepository;
        this.delegate = delegate;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        Authentication converted = delegate.convert(request);
        if (!(converted instanceof OAuth2AuthorizationCodeRequestAuthenticationToken authorizationRequest)
                || !(authorizationRequest.getPrincipal() instanceof Authentication principal)
                || !(principal.getPrincipal() instanceof AccountUserDetails userDetails)) {
            return converted;
        }

        Map<String, Object> parameters = new HashMap<>(authorizationRequest.getAdditionalParameters());
        String requestedPeppolId = optionalSingleString(parameters, PEPPOL_ID_PARAMETER);
        String requestedAccountType = optionalSingleString(parameters, ACCOUNT_TYPE_PARAMETER);
        if (requestedPeppolId == null && requestedAccountType != null) {
            throw invalidSelection();
        }

        Optional<Ownership> selectedOwnership;
        if (requestedPeppolId == null) {
            // No URL context: use the remembered default, but freeze it into this authorization.
            selectedOwnership = ownershipRepository.findFirstByAccountIdOrderByLastUsedDesc(userDetails.getAccountId());
        } else {
            if (requestedPeppolId.isBlank() || requestedPeppolId.length() > 32) {
                throw invalidSelection();
            }
            if (requestedAccountType == null) {
                selectedOwnership = ownershipRepository
                        .findFirstByAccountIdAndCompanyPeppolIdOrderByLastUsedDesc(
                                userDetails.getAccountId(), requestedPeppolId);
            } else {
                AccountType accountType;
                try {
                    accountType = AccountType.valueOf(requestedAccountType);
                } catch (IllegalArgumentException exception) {
                    throw invalidSelection();
                }
                selectedOwnership = ownershipRepository
                        .findFirstByAccountIdAndCompanyPeppolIdAndTypeOrderByLastUsedDesc(
                                userDetails.getAccountId(), requestedPeppolId, accountType);
            }
        }

        Ownership ownership = selectedOwnership.orElseThrow(ActingOwnershipAuthorizationRequestConverter::invalidSelection);
        parameters.put(PEPPOL_ID_PARAMETER, ownership.getCompany().getPeppolId());
        parameters.put(ACCOUNT_TYPE_PARAMETER, ownership.getType().name());

        return new OAuth2AuthorizationCodeRequestAuthenticationToken(
                authorizationRequest.getAuthorizationUri(),
                authorizationRequest.getClientId(),
                (Authentication) authorizationRequest.getPrincipal(),
                authorizationRequest.getRedirectUri(),
                authorizationRequest.getState(),
                authorizationRequest.getScopes(),
                parameters);
    }

    private static String optionalSingleString(Map<String, Object> parameters, String name) {
        Object value = parameters.get(name);
        if (value == null) {
            return null;
        }
        if (!(value instanceof String stringValue)) {
            throw invalidSelection();
        }
        return stringValue;
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationException invalidSelection() {
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_REQUEST,
                "The requested acting ownership is not available",
                null);
        // The standard provider has not validated redirect_uri yet, so do not attach the request.
        return new OAuth2AuthorizationCodeRequestAuthenticationException(error, null);
    }
}
