package org.letspeppol.kyc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
public class OAuth2AuthorizationCleanupJob {

    private static final Duration RETENTION_MARGIN = Duration.ofHours(1);
    private static final Duration MAXIMUM_RETENTION = Duration.ofDays(7);
    private static final String LAST_ISSUED_AT =
            "GREATEST(authorization_code_issued_at, access_token_issued_at, oidc_id_token_issued_at, refresh_token_issued_at)";

    private final JdbcOperations jdbcOperations;
    private final Duration idleRetention;

    public OAuth2AuthorizationCleanupJob(JdbcOperations jdbcOperations,
                                         @Value("${server.servlet.session.timeout:8h}") Duration sessionTimeout) {
        this.jdbcOperations = jdbcOperations;
        this.idleRetention = sessionTimeout.plus(RETENTION_MARGIN);
    }

    @Scheduled(cron = "0 15 * * * *")
    public void purgeExpired() {
        Instant now = Instant.now();
        int removed = jdbcOperations.update("""
                DELETE FROM oauth2_authorization
                WHERE %1$s < ?
                   OR (registered_client_id, principal_name) IN (
                        SELECT registered_client_id, principal_name
                        FROM oauth2_authorization
                        GROUP BY registered_client_id, principal_name
                        HAVING MAX(%1$s) < ?
                   )
                """.formatted(LAST_ISSUED_AT),
                Timestamp.from(now.minus(MAXIMUM_RETENTION)),
                Timestamp.from(now.minus(idleRetention)));
        if (removed > 0) {
            log.info("Purged {} OAuth2 authorizations of principals idle for {} or older than {}", removed, idleRetention, MAXIMUM_RETENTION);
        }
    }
}
