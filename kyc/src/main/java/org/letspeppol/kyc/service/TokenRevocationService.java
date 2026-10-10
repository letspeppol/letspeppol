package org.letspeppol.kyc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.OwnershipAccessChanged;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TokenRevocationService {

    private static final String AUTHORIZATIONS_WITH_A_LIVE_ACCESS_TOKEN =
            "SELECT id FROM oauth2_authorization WHERE principal_name = ? AND access_token_expires_at > ?";

    private final JdbcOperations jdbcOperations;
    private final OAuth2AuthorizationService authorizationService;
    private final TokenRevocationFeed feed;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void revokeTokensOf(OwnershipAccessChanged ownership) {
        List<String> authorizationIds = jdbcOperations.queryForList(
                AUTHORIZATIONS_WITH_A_LIVE_ACCESS_TOKEN, String.class, ownership.email(), Timestamp.from(Instant.now()));
        int revoked = 0;
        for (String authorizationId : authorizationIds) {
            if (revokeAccessToken(authorizationId, ownership)) {
                revoked++;
            }
        }
        log.info("Revoked {} access token(s) of {} for company {}", revoked, ownership.email(), ownership.peppolId());
    }

    private boolean revokeAccessToken(String authorizationId, OwnershipAccessChanged ownership) {
        try {
            OAuth2Authorization authorization = authorizationService.findById(authorizationId);
            OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization == null ? null : authorization.getAccessToken();
            Map<String, Object> claims = accessToken == null ? null : accessToken.getClaims();
            if (claims == null
                    || !ownership.peppolId().equals(claims.get("peppolId"))
                    || !ownership.type().name().equals(claims.get("accountType"))
                    || !(claims.get(JwtClaimNames.JTI) instanceof String tokenId)) {
                return false;
            }
            feed.revoke(tokenId, accessToken.getToken().getExpiresAt());
            return true;
        } catch (RuntimeException e) {
            log.warn("Could not read authorization {} while revoking tokens of {}: {}", authorizationId, ownership.email(), e.getMessage());
            return false;
        }
    }
}
