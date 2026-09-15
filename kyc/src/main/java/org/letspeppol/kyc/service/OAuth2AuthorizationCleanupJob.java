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

    private final JdbcOperations jdbcOperations;
    private final Duration retention;

    public OAuth2AuthorizationCleanupJob(JdbcOperations jdbcOperations,
                                         @Value("${server.servlet.session.timeout:8h}") Duration sessionTimeout) {
        this.jdbcOperations = jdbcOperations;
        this.retention = sessionTimeout.plus(RETENTION_MARGIN);
    }

    @Scheduled(cron = "0 15 * * * *")
    public void purgeExpired() {
        int removed = jdbcOperations.update("""
                DELETE FROM oauth2_authorization
                WHERE GREATEST(authorization_code_issued_at, access_token_issued_at, oidc_id_token_issued_at, refresh_token_issued_at) < ?
                """, Timestamp.from(Instant.now().minus(retention)));
        if (removed > 0) {
            log.info("Purged {} OAuth2 authorizations older than {}", removed, retention);
        }
    }
}
